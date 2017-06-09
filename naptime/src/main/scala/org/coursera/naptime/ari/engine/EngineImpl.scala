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
import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.EngineApi
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.ResponseMetrics
import org.coursera.naptime.ari.SchemaProvider
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.schema.RelationType.FINDER
import org.coursera.naptime.schema.RelationType.GET
import org.coursera.naptime.schema.RelationType.MULTI_GET
import org.coursera.naptime.schema.RelationType.SINGLE_ELEMENT_FINDER
import org.coursera.naptime.schema.ReverseRelationAnnotation
import play.api.libs.json.JsNull
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
    (implicit executionContext: ExecutionContext)
  extends EngineApi
  with StrictLogging {

  import EngineHelpers._

  override def execute(request: Request): Future[Response] = {
    val responseFutures = request.topLevelRequests.map { topLevelRequest =>
      executeTopLevelRequest(request.requestHeader, topLevelRequest).flatMap { response =>
        executeRelatedRequest(request.requestHeader, topLevelRequest, response)
      }
    }
    val futureResponses = Future.sequence(responseFutures)
    futureResponses.map { responses =>
      responses.foldLeft(Response.empty)(_ ++ _)
    }
  }

  /**
    * Executes a request on the fetcher to load data off a resource
    * @param requestHeader incoming requestheader containing cookies, headers, etc.
    * @param topLevelRequest request specifying resource name, arguments, etc.
    * @return a Response containing response data, ids, and metrics about the request and response
    */
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

  /**
    * Traverses through the request and response to find all forward and reverse relations,
    * and then execute those relations by creating and executing additional topLevelRequests
    * @param requestHeader incoming requestheader containing cookies, headers, etc.
    * @param topLevelRequest request used to generate the topLevelResponse
    * @param topLevelResponse response containing data associated with the topLevelRequest
    * @return
    */
  private[this] def executeRelatedRequest(
      requestHeader: RequestHeader,
      topLevelRequest: TopLevelRequest,
      topLevelResponse: Response): Future[Response] = {
    val topLevelData = topLevelResponse.data.get(topLevelRequest.resource)
      .toIterable.flatMap(_.values)

    schemaProvider.mergedType(topLevelRequest.resource).map { mergedType =>
      val (forwardRelations, reverseRelations) = collectRelations(
        topLevelRequest.selection, topLevelData, mergedType)

      val forwardRelationResponses = forwardRelations
        .groupBy(relation => (relation.resourceName, relation.selection))
        .mapValues(_.foldLeft(List[String]())(_ ++ _.ids))
        .map { case ((resourceName, selection), ids) =>
          fetchForwardRelation(requestHeader, selection, resourceName, ids)
        }

      val reverseRelationResponses = reverseRelations
        .groupBy(relation => (relation.selection, relation.path, relation.annotation))
        .mapValues(_.map(_.element))
        .map { case ((selection, path, annotation), elements) =>
          fetchReverseRelation(requestHeader, selection, elements, mergedType, path, annotation)
        }

      val topLevelResponses = forwardRelationResponses ++ reverseRelationResponses

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
          val ids = idMap.getOrElse(id, new DataList())
          insertAtPath(data, mergedType, fieldRelationResponse.path, ids)
        }
        val updatedData = topLevelResponse.data +
          (topLevelRequest.resource -> mutableTopLevelData)
        val responseWithUpdatedData = topLevelResponse.copy(data = updatedData)

        val finalResponse = fieldResponses.foldLeft(responseWithUpdatedData)(_ ++ _.response)
        val relatedResponsesFut = fieldResponses.flatMap { fieldResponse =>
          fieldResponse.response.data.headOption.map { case (resourceName, data) =>
            val newTopLevelRequest = TopLevelRequest(resourceName, fieldResponse.requestField)
            executeRelatedRequest(
              requestHeader,
              newTopLevelRequest,
              finalResponse.copy(metrics = ResponseMetrics()))
          }
        }
        Future.sequence(relatedResponsesFut).map { relatedResponses =>
          relatedResponses.foldLeft(finalResponse)(_ ++ _)
        }
      }
    }.getOrElse {
      logger.error(s"No merged type found for resource ${topLevelRequest.resource}. " +
        s"Skipping automatic inclusions.")
      Future.successful(topLevelResponse)
    }
  }

  /**
    * Execute a topLevelRequest for a forward relation
    * @param requestHeader incoming requestheader containing cookies, headers, etc.
    * @param requestField selection specifying arguments and nested selections on the relation
    * @param resourceName resource name to query (using a multiGet on the resource)
    * @param ids list of ids to fetch elements for (turns into a query parameter on the request)
    * @return a FieldRelationResponse containing the selection and response
    */
  private[this] def fetchForwardRelation(
      requestHeader: RequestHeader,
      requestField: RequestField,
      resourceName: ResourceName,
      ids: List[String]): Future[FieldRelationResponse] = {
    val multiGetIds = ids.toSet.mkString(",")

    if (multiGetIds.nonEmpty) {
      val relatedTopLevelRequest = TopLevelRequest(
        resource = resourceName,
        selection = RequestField(
          name = "multiGet",
          alias = None,
          args = Set("ids" -> JsString(multiGetIds)) ++ requestField.args,
          selections = requestField.selections))
      executeTopLevelRequest(requestHeader, relatedTopLevelRequest).map { response =>
        // Exclude the top level ids in the response.
        val res = Response(
          topLevelResponses = Map.empty,
          data = response.data,
          metrics = response.metrics)
        FieldRelationResponse(requestField, response = res)
      }
    } else {
      Future.successful(FieldRelationResponse(requestField))
    }
  }

  /**
    * This regex is used to match both "$instructorIds" and "${instructorDetails/instructorIds}"
    */
  private[this] val InterpolationRegex =
    new Regex("""\$(?:([a-zA-Z0-9_]+)|\{([^\}]+)\})""", "withoutBraces", "withBraces")

  /**
    * Executes a series of topLevelRequests for a reverse relation
    * @param requestHeader incoming requestheader containing cookies, headers, etc.
    * @param requestField selection specifying arguments and nested selections on the relation
    * @param data list of dataMaps containing the resources that the reverse relation is on.
    *             (these are necessary in order to support interpolation of arguments)
    * @param path path of the new, dynamic field where the reverse relation will be inserted
    * @param reverse ReverseRelationAnnotation containing arguments, resource name, and type
    * @return a FieldRelationResponse containing a merged response
    *         and merged list of ids to be added to the parent element at the specified path
    */
  private[this] def fetchReverseRelation(
      requestHeader: RequestHeader,
      requestField: RequestField,
      data: Iterable[DataMap],
      schema: RecordDataSchema,
      path: Seq[String],
      reverse: ReverseRelationAnnotation): Future[FieldRelationResponse] = {
    val resourceName = ResourceName.parse(reverse.resourceName).getOrElse {
      throw new IllegalStateException(s"Could not parse identifier " +
        s"'${reverse.resourceName}''")
    }
    val argumentsByElement = data.map { topLevelElement =>
      val arguments: Set[(String, JsValue)] = reverse.arguments
        .mapValues { value =>
          InterpolationRegex.replaceAllIn(
            value, { regexMatch =>
              val withoutBraces = Option(regexMatch.group("withoutBraces"))
              val withBraces = Option(regexMatch.group("withBraces"))
              val variableName = withoutBraces.orElse(withBraces).getOrElse("")
              EngineHelpers.getValueAtPath(topLevelElement, schema, variableName.split("/")).map {
                case dataList: DataList => dataList.asScala.mkString(",")
                case other: Any => other.toString
              }.getOrElse("")
            })
        }
        .filterNot(_._2.isEmpty)
        .mapValues(value => JsString(value))
        .toSet
      topLevelElement -> arguments
    }.toMap
    val futureIdMapAndResponse = reverse.relationType match {
      case MULTI_GET | GET =>
        // Group requests by all arguments other than `ids`, and them merge the ids together
        // This lets us make as few requests as possible while still fetching all ids
        // and maintaining element-level arguments
        val groupedRequests = argumentsByElement.groupBy(_._2.filterNot(_._1 == "ids"))
        groupedRequests.map { case (nonIdArguments, elementsAndArguments) =>
          val ids = elementsAndArguments
            .flatMap(_._2.find(_._1 == "ids").map(_._2))
            .map(ids => EngineHelpers.stringifyArg(ids))
            .mkString(",")
          val arguments = nonIdArguments
            .filterNot(key => key._1 == "limit" || key._1 == "start") + ("ids" -> JsString(ids))

          // Remove null and empty arguments from the request
          val fieldArguments = requestField.args.filter { case (key, value) =>
            value match {
              case JsString(stringValue) => stringValue.nonEmpty
              case JsNull => false
              case _ => true
            }
          }
          val relatedTopLevelRequest = TopLevelRequest(
            resource = resourceName,
            selection = RequestField(
              name = "reverseRelation",
              alias = None,
              args = arguments ++ fieldArguments,
              selections = requestField.selections))
          executeTopLevelRequest(requestHeader, relatedTopLevelRequest).map { response =>
            val responseIds = response.data.headOption.map(_._2.keys.toList).getOrElse(List.empty)
            val idMap = elementsAndArguments.map { case (element, elementArguments) =>
              val ids = elementArguments.find(_._1 == "ids")
                .map(_._2.as[String].split(",").map(_.asInstanceOf[AnyRef]).toList)
                .getOrElse(List[AnyRef]())

              // MultiGets return a list of ids, and Gets return a single id (or null)
              val intersection = responseIds.filter(id => ids.contains(id.toString))
              val indexedIds = ids.zipWithIndex.toMap
              val sortedIntersection = intersection.sortBy(id => indexedIds.get(id.toString).getOrElse(-1))
              val filteredIds = reverse.relationType match {
                case MULTI_GET => new DataList(sortedIntersection.asJava)
                case GET => intersection.headOption.orNull
                case _ => throw new RuntimeException(s"Unhandled relation type")
              }
              element.get("id") -> filteredIds
            }

            (idMap, Response(
              topLevelResponses = Map.empty,
              data = response.data,
              metrics = response.metrics))
          }
        }
      case FINDER | SINGLE_ELEMENT_FINDER =>
        data.map { topLevelElement =>
          val arguments: Set[(String, JsValue)] =
            argumentsByElement.get(topLevelElement).map(_.toSet).getOrElse(Set.empty)
          val relatedTopLevelRequest = TopLevelRequest(
            resource = resourceName,
            selection = RequestField(
              name = "reverseRelation",
              alias = None,
              args = arguments ++ requestField.args,
              selections = requestField.selections))
          executeTopLevelRequest(requestHeader, relatedTopLevelRequest).map { response =>
            val responseIds = response.data.headOption.map { case (_, elements) => elements.keys.toList }.getOrElse(List.empty)
            val filteredResponseIds = reverse.relationType match {
              case FINDER => new DataList(responseIds.asJava)
              case SINGLE_ELEMENT_FINDER => responseIds.headOption.orNull
              case _ => throw new RuntimeException(s"Unhandled relation type")
            }
            val idMap = Map(topLevelElement.get("id") -> filteredResponseIds)
            (idMap, Response(
              topLevelResponses = Map.empty,
              data = response.data,
              metrics = response.metrics))
          }
        }
      case _ => throw new RuntimeException(s"Unhandled relation type: ${reverse.relationType}")
    }
    Future.fold(futureIdMapAndResponse)(FieldRelationResponse(requestField, path)) {
      case (fieldRelationResponse, (idMap, res)) =>
        fieldRelationResponse.copy(
          response = fieldRelationResponse.response ++ res,
          idsToAnnotate = Some(fieldRelationResponse.idsToAnnotate.getOrElse(Map.empty) ++ idMap))
    }
  }

  /**
    * Helper case class containing information about a response from a forward or reverse relation
    * @param requestField selection on the field, containing arguments and nested selections
    * @param path path of the field relation, used for annotating ids for reverse relations
    * @param response a top level response with returned elements and response metrics
    * @param idsToAnnotate a map of data ids to fetched ids, used for populating reverse relations
    */
  case class FieldRelationResponse(
      requestField: RequestField,
      path: Seq[String] = Seq.empty,
      response: Response = Response.empty,
      idsToAnnotate: Option[Map[AnyRef, AnyRef]] = None)
}

