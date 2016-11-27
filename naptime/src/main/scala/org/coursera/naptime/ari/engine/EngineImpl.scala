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
      executeTopLevelRequest(request.requestHeader, topLevelRequest).flatMap { response =>
        executeRelatedRequest(request.requestHeader, response, topLevelRequest)
      }

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

    topLevelResponse.map { topLevelResponse =>
      topLevelResponse.copy(
        metrics = ResponseMetrics(
          numRequests = 1,
          duration = FiniteDuration(System.nanoTime() - startTime, "nanos")))
    }
  }
  private[this] def executeRelatedRequest(
      requestHeader: RequestHeader,
      topLevelResponse: Response,
      topLevelRequest: TopLevelRequest): Future[Response] = {
    val topLevelData = extractDataMaps(topLevelResponse, topLevelRequest.resource)
    mergedTypeForResource(topLevelRequest.resource).map { mergedType =>
      val topLevelResponses = getNestedFields(mergedType, topLevelRequest, topLevelResponse)
        .map { case (nestedField, field) =>
          val forwardRelation = relatedResourceForField(field)
          val reverseRelation = reverseRelationForField(field)
          (forwardRelation, reverseRelation) match {
            case (Some(resourceName), None) =>
              fetchForwardRelation(requestHeader, nestedField, topLevelData, field, resourceName)
            case (None, Some(reverse)) =>
              fetchReverseRelation(requestHeader, nestedField, topLevelData, field, reverse)
            case (Some(forward), Some(reverse)) =>
              throw new RuntimeException(
                s"Both forward and reverse relations cannot be defined on ${field.getName}")
            case (None, None) =>
              Future.successful(FieldRelationResponse(field, nestedField))
          }
        }
      Future.sequence(topLevelResponses).flatMap { fieldResponses =>
        val mutableTopLevelData = topLevelData
          .map(_.clone())
          .map(data => data.get("id") -> data)
          .toMap
        for {
          fieldRelationResponse <- fieldResponses
          (id, data) <- mutableTopLevelData
          idMap <- fieldRelationResponse.idsToAnnotate
        } yield {
          val ids = idMap.getOrElse(id, List.empty)
          data.put(fieldRelationResponse.field.getName, new DataList(ids.asJava))
        }
        val updatedData = topLevelResponse.data +
          (topLevelRequest.resource -> mutableTopLevelData)
        val responseWithUpdatedData = topLevelResponse.copy(data = updatedData)
        val finalResponse = fieldResponses.foldLeft(responseWithUpdatedData)(_ ++ _.response)
        val relatedResponsesFut = fieldResponses.map { fieldResponse =>
          val newTopLevelRequest = TopLevelRequest(
            fieldResponse.response.data.head._1,
            fieldResponse.requestField)
          executeRelatedRequest(requestHeader, finalResponse.copy(metrics = ResponseMetrics()), newTopLevelRequest)
        }
        Future.sequence(relatedResponsesFut).map { relatedResponses =>
          relatedResponses.foldLeft(finalResponse)(_ ++ _)
        }
      }
    }.getOrElse {
      logger.error(s"No merged type found for resource ${topLevelRequest.resource}. " +
        s"Skipping automatic inclusions.")
      Future.successful(Response.empty)
    }
  }

  private[this] def extractDataMaps(
      response: Response,
      resourceName: ResourceName): Iterable[DataMap] = {
    response.data.get(resourceName).toIterable.flatMap { dataMap =>
      dataMap.values
    }
  }

  private[this] def relatedResourceForField(
      field: RecordDataSchema.Field): Option[ResourceName] = {
    Option(field.getProperties.get(Relations.PROPERTY_NAME)).map {
      case idString: String =>
        ResourceName.parse(idString).getOrElse {
          throw new IllegalStateException(s"Could not parse identifier '$idString' " +
            s"for field '$field'")
        }
      case identifier =>
        throw new IllegalStateException(s"Unexpected type for identifier '$identifier' " +
          s"for field '$field'")
    }
  }

  private[this] def reverseRelationForField(
      field: RecordDataSchema.Field): Option[ReverseRelationAnnotation] = {
    Option(field.getProperties.get(Relations.REVERSE_PROPERTY_NAME)).map {
      case dataMap: DataMap => ReverseRelationAnnotation(dataMap, DataConversion.SetReadOnly)
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
          logger.debug(s"Field $nestedField was not found / was not a data list " +
            s"in $elem for query field $field")
          List.empty
        }
      } else {
        throw new IllegalStateException(
          s"Cannot join on an unknown field type '${field.getType.getDereferencedType}' " +
            s"for field '${field.getName}'")
      }
    }.toSet.map[String, Set[String]](_.toString).mkString(",") // TODO: escape appropriately.
  }

  private[this] def getNestedFields(
      mergedType: RecordDataSchema,
      topLevelRequest: TopLevelRequest,
      response: Response) = {

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

    for {
      nestedField <- nestedLookups
      field <- Option(mergedType.getField(nestedField.name)).toList
    } yield {
      (nestedField, field)
    }
  }

  private[this] def fetchForwardRelation(
      requestHeader: RequestHeader,
      requestField: RequestField,
      data: Iterable[DataMap],
      field: RecordDataSchema.Field,
      resourceName: ResourceName) = {
    val multiGetIds = computeMultiGetIds(data, requestField, field)

    if (multiGetIds.nonEmpty) {

      val relatedTopLevelRequest = TopLevelRequest(
        resource = resourceName,
        selection = RequestField(
          name = "multiGet",
          alias = None,

          // TODO: pass through original request fields for pagination
          args = Set("ids" -> JsString(multiGetIds)),

          selections = requestField.selections))
      executeTopLevelRequest(requestHeader, relatedTopLevelRequest).map { response =>
        // Exclude the top level ids in the response.
        Response(
          topLevelResponses = Map.empty,
          data = response.data,
          metrics = response.metrics)
      }.map { response =>
        FieldRelationResponse(field, requestField, response)
      }
    } else {
      Future.successful(FieldRelationResponse(field, requestField))
    }
  }

  private[this] def fetchReverseRelation(
      requestHeader: RequestHeader,
      requestField: RequestField,
      data: Iterable[DataMap],
      field: RecordDataSchema.Field,
      reverse: ReverseRelationAnnotation) = {
    val resourceName = ResourceName.parse(reverse.resourceName).getOrElse {
      throw new IllegalStateException(s"Could not parse identifier " +
        s"'${reverse.resourceName}' for field '$field'")
    }
    val responses = data.map { topLevelElement =>
      val InterpolationRegex = new Regex("""\$([a-zA-Z0-9_]+)""", "variableName")
      val interpolatedArguments: Set[(String, JsValue)] = reverse.arguments
        .mapValues { value =>
          InterpolationRegex.replaceAllIn(value, { regexMatch =>
            val variableName = regexMatch.group("variableName")
            Option(topLevelElement.get(variableName))
              .map(_.toString)
              .getOrElse(variableName)
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
          selections = requestField.selections))
      executeTopLevelRequest(requestHeader, relatedTopLevelRequest).map { response =>
        topLevelElement.get("id") -> Response(
          topLevelResponses = Map.empty,
          data = response.data,
          metrics = response.metrics)
      }
    }
    Future.fold(responses)(FieldRelationResponse(field, requestField)) { case (frr, (id, response)) =>
      val idMap = response.data.headOption.map { case (_, data) =>
        id -> data.keys.toList
      }
      frr.copy(
        response = frr.response ++ response,
        idsToAnnotate = Some(frr.idsToAnnotate.getOrElse(Map.empty) ++ idMap))
    }
  }
}

case class FieldRelationResponse(
    field: RecordDataSchema.Field,
    requestField: RequestField,
    response: Response = Response.empty,
    idsToAnnotate: Option[Map[AnyRef, List[AnyRef]]] = None)

