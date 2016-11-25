/*
 * Copyright 2016 Coursera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.coursera.naptime.ari.engine

import javax.inject.Inject

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.ResourceName
import org.coursera.naptime.Types.Relations
import org.coursera.naptime.ari.EngineApi
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.ResponseMetrics
import org.coursera.naptime.ari.SchemaProvider
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.schema.ReverseRelationAnnotation
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex

class EngineImpl @Inject() (
    schemaProvider: SchemaProvider,
    fetcher: FetcherApi)
    (implicit executionContext: ExecutionContext) extends EngineApi with StrictLogging {

  private[this] def mergedTypeForResource(resourceName: ResourceName): Option[RecordDataSchema] = {
    schemaProvider.mergedType(resourceName)
  }

  override def execute(request: Request): Future[Response] = {
    val responseFutures = request.topLevelRequests.map { topLevelRequest =>
      executeTopLevelRequest(request.requestHeader, topLevelRequest)
    }
    val futureResponses = Future.sequence(responseFutures)
    futureResponses.map { responses =>
      responses.foldLeft(Response.empty)(_ ++ _)
    }
  }

  private[this] def executeTopLevelRequest(
      requestHeader: RequestHeader,
      topLevelRequest: TopLevelRequest): Future[Response] = {
    val startTime = System.nanoTime()
    val topLevelResponse = fetcher.data(Request(requestHeader, List(topLevelRequest)))

    topLevelResponse.flatMap { topLevelResponse =>
      val topLevelResponseWithMetrics = topLevelResponse.copy(
        metrics = ResponseMetrics(
          numRequests = 1,
          duration = FiniteDuration(System.nanoTime() - startTime, "nanos")))
      // If we have a schema, we can perform automatic resource inclusion.
      mergedTypeForResource(topLevelRequest.resource).map { mergedType =>
        val topLevelData = extractDataMaps(topLevelResponseWithMetrics, topLevelRequest)

        // TODO(bryan): Pull out logic about connections in here
        val elementLookups = (for {
          elements <- topLevelRequest.selection.selections.find(_.name == "elements")
        } yield {
          elements.selections.filter(_.selections.nonEmpty)
        }).getOrElse {
          List.empty
        }

        val singularLookups = topLevelRequest.selection.selections.filter { selection =>
          selection.selections.nonEmpty && selection.name != "elements"
        }

        val nestedLookups = elementLookups ++ singularLookups

        val additionalResponses = for {
          nestedField <- nestedLookups
          field <- Option(mergedType.getField(nestedField.name)).toList
        } yield {
          val forwardRelation = relatedResourceForField(field, mergedType)
          val reverseRelation = reverseRelationForField(field, mergedType)
          (forwardRelation, reverseRelation) match {
            case (Some(resourceName), None) =>
              val multiGetIds = computeMultiGetIds(topLevelData, nestedField, field)

              val relatedTopLevelRequest = TopLevelRequest(
                resource = resourceName,
                selection = RequestField(
                  name = "multiGet",
                  alias = None,
                  args = Set(
                    "ids" ->
                      JsString(multiGetIds)), // TODO: pass through original request fields for pagination
                  selections = nestedField.selections))
              executeTopLevelRequest(requestHeader, relatedTopLevelRequest).map { response =>
                // Exclude the top level ids in the response.
                Response(
                  topLevelResponses = Map.empty,
                  data = response.data,
                  metrics = response.metrics)
              }.map { response =>
                FieldRelationResponse(field, Map.empty, List(response))
              }
            case (None, Some(reverse)) =>
              val resourceName = ResourceName.parse(reverse.resourceName).getOrElse {
                throw new IllegalStateException(s"Could not parse identifier " +
                  s"'${reverse.resourceName}' for field '$field' in merged type $mergedType")
              }
              val responses = topLevelData.map { topLevelElement =>
                val InterpolationRegex = new Regex("""\$([a-zA-Z0-9_]+)""", "variableName")
                val interpolatedArguments: Set[(String, JsValue)] = reverse.arguments
                  .mapValues { value =>
                    InterpolationRegex.replaceAllIn(value, { regexMatch =>
                      val variableName = regexMatch.group("variableName")
                      Option(topLevelElement.get(variableName)).map(_.toString).getOrElse(variableName)
                    })
                  }
                  .mapValues(value => JsString(value))
                  .toSet
                val relatedTopLevelRequest = TopLevelRequest(
                  resource = resourceName,
                  selection = RequestField(
                    name = "reverseRelation",
                    alias = None,
                    args = interpolatedArguments,
                    selections = nestedField.selections))
                executeTopLevelRequest(requestHeader, relatedTopLevelRequest).map { response =>
                  topLevelElement.get("id") -> Response(
                    topLevelResponses = Map.empty,
                    data = response.data,
                    metrics = response.metrics)
                }
              }
              Future.sequence(responses).map { responseIdPairs =>
                val idMap = responseIdPairs.toMap.flatMap { case (id, response) =>
                  response.data.headOption.map { case (resourceName, data) =>
                    id -> data.keys.toList
                  }
                }
                FieldRelationResponse(field, idMap, responseIdPairs.map(_._2).toList)
              }

            case (Some(forward), Some(reverse)) =>
              throw new RuntimeException(
                s"Both forward and reverse relations cannot be defined on ${field.getName}")
            case (None, None) => Future.successful(FieldRelationResponse(field, Map.empty, List.empty))
          }
        }
        Future.sequence(additionalResponses).map { fieldResponses =>
          val mutableTopLevelData = topLevelData
            .map(_.clone())
            .map(data => data.get("id") -> data)
            .toMap
          fieldResponses.map { fieldRelationResponse =>
            mutableTopLevelData.foreach { case (id, data) =>
              val ids = fieldRelationResponse.idMap.getOrElse(id, List.empty)
              data.put(fieldRelationResponse.field.getName, new DataList(ids.asJava))
            }
            fieldRelationResponse.responses
          }
          val updatedData = topLevelResponseWithMetrics.data + (topLevelRequest.resource -> mutableTopLevelData)
          val responseWithUpdatedData = topLevelResponseWithMetrics.copy(data = updatedData)
          val additionalResponses = fieldResponses.flatMap(_.responses)
          additionalResponses.foldLeft(responseWithUpdatedData)(_ ++ _)
        }
      }.getOrElse {
        logger.error(s"No merged type found for resource ${topLevelRequest.resource}. Skipping automatic inclusions.")
        Future.successful(topLevelResponseWithMetrics)
      }
    }
  }

  private[this] def extractDataMaps(response: Response, request: TopLevelRequest): Iterable[DataMap] = {
    response.data.get(request.resource).toIterable.flatMap { dataMap =>
      dataMap.values
    }
  }

  private[this] def relatedResourceForField(field: RecordDataSchema.Field, mergedType: RecordDataSchema): Option[ResourceName] = {
    Option(field.getProperties.get(Relations.PROPERTY_NAME)).map {
      case idString: String =>
        ResourceName.parse(idString).getOrElse {
          throw new IllegalStateException(s"Could not parse identifier '$idString' for field '$field' in " +
            s"merged type $mergedType")
        }
      case identifier =>
        throw new IllegalStateException(s"Unexpected type for identifier '$identifier' for field '$field' " +
          s"in merged type $mergedType")
    }
  }

  private[this] def reverseRelationForField(field: RecordDataSchema.Field, mergedType: RecordDataSchema): Option[ReverseRelationAnnotation] = {
    Option(field.getProperties.get(Relations.REVERSE_PROPERTY_NAME)).map {
      case dataMap: DataMap =>
        ReverseRelationAnnotation(dataMap, DataConversion.SetReadOnly)
    }
  }

  private[this] def computeMultiGetIds(
      topLevelData: Iterable[DataMap],
      nestedField: RequestField,
      field: RecordDataSchema.Field): String = {
    topLevelData.flatMap { elem =>
      if (field.getType.getDereferencedDataSchema.isPrimitive) {
        Option(elem.get(nestedField.name))
      } else if (field.getType.getDereferencedDataSchema.isInstanceOf[ArrayDataSchema]) {
        Option(elem.getDataList(nestedField.name)).map(_.asScala).getOrElse {
          logger.debug(
            s"Field $nestedField was not found / was not a data list in $elem for query field $field")
          List.empty
        }
      } else {
        throw new IllegalStateException(
          s"Cannot join on an unknown field type '${field.getType.getDereferencedType}' " +
            s"for field '${field.getName}'")
      }
    }.toSet.map[String, Set[String]](_.toString).mkString(",") // TODO: escape appropriately.
  }
}

case class FieldRelationResponse(
    field: RecordDataSchema.Field,
    idMap: Map[AnyRef, List[AnyRef]],
    responses: List[Response])

