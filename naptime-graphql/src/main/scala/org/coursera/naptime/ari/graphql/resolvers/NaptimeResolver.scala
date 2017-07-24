package org.coursera.naptime.ari.graphql.resolvers

import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.schema.DataMapWithParent
import org.coursera.naptime.ari.graphql.schema.NaptimeResourceUtils
import org.coursera.naptime.ari.graphql.schema.ParentModel
import play.api.libs.json.JsArray
import play.api.libs.json.JsNull
import play.api.libs.json.JsValue
import sangria.execution.deferred.Deferred
import sangria.execution.deferred.DeferredResolver

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future


case class NaptimeRequest(
    idx: RequestId,
    resourceName: ResourceName,
    arguments: Set[(String, JsValue)],
    resourceSchema: RecordDataSchema,
    paginationOverride: Option[ResponsePagination] = None)

case class NaptimeResponse(
    elements: List[DataMapWithParent],
    pagination: Option[ResponsePagination],
    url: String)

case class NaptimeError(
    url: String,
    error: String)

sealed trait DeferredNaptime {
  def toNaptimeRequest(idx: Int): NaptimeRequest
}

case class DeferredNaptimeRequest(
    resourceName: ResourceName,
    arguments: Set[(String, JsValue)],
    resourceSchema: RecordDataSchema,
    paginationOverride: Option[ResponsePagination] = None)
  extends Deferred[Either[NaptimeError, NaptimeResponse]] with DeferredNaptime {

  def toNaptimeRequest(idx: Int): NaptimeRequest = {
    NaptimeRequest(RequestId(idx), resourceName, arguments, resourceSchema, paginationOverride)
  }
}

case class DeferredNaptimeElement(
    resourceName: ResourceName,
    id: JsValue,
    arguments: Set[(String, JsValue)],
    resourceSchema: RecordDataSchema)
  extends Deferred[Either[NaptimeError, NaptimeResponse]] with DeferredNaptime {

  def toNaptimeRequest(idx: Int): NaptimeRequest = {
    NaptimeRequest(
      RequestId(idx),
      resourceName,
      arguments + ("ids" -> JsArray(List(id))),
      resourceSchema)
  }
}

case class RequestId(idx: Int)

class NaptimeResolver extends DeferredResolver[SangriaGraphQlContext] with StrictLogging {
  def resolve(
      deferred: Vector[Deferred[Any]],
      ctx: SangriaGraphQlContext,
      queryState: Any)
    (implicit ec: ExecutionContext): Vector[Future[Any]] = {

    val naptimeRequests = deferred.zipWithIndex.collect {
      case (d: DeferredNaptime, idx: Int) => d.toNaptimeRequest(idx)
    }

    val dataByResource = naptimeRequests.groupBy(_.resourceName)
      .map { case (resourceName, requests) =>
        val (forwardRequests, reverseRequests) =
          requests.partition(_.arguments.exists(_._1 == "ids"))

        // Handle MultiGet and Non-Multigets differently, since multigets can be batched
        val forwardRelations = fetchForwardRelations(forwardRequests, resourceName, ctx)
        val reverseRelations = fetchReverseRelations(reverseRequests, resourceName, ctx)
        Future.sequence(List(forwardRelations, reverseRelations)).map(_.flatten.toMap)

      }
    val allData = Future.sequence(dataByResource).map(_.flatten.toMap)

    deferred.zipWithIndex.map { case (_, idx) =>
      allData.map(
        _.getOrElse(
          RequestId(idx), {
            throw new RuntimeException(
              "Error in NaptimeResolver. Could not find outgoing request.")
          }))
    }
  }

  /**
    * Fetches forward relations (via multiget) for a specific resource given a list of requests.
    * This implementation optimizes fetches by merging multigets into as few requests as possible.
    *
    * Multiget requests can be merged if all other query parameters are the same.
    *
    * In the event of an error, a NaptimeError is returned instead of a NaptimeResponse
    *
    * @param requests list of NaptimeRequests containing the endpoint and arguments (including ids)
    * @param resourceName resource that the requests is made against
    * @param context request context (includes things like header)
    * @return Map of request ids (indexes from the deferred request batching) to either a
    *         NaptimeError or NaptimeResponse
    */
  def fetchForwardRelations(
      requests: Vector[NaptimeRequest],
      resourceName: ResourceName,
      context: SangriaGraphQlContext)
      (implicit ec: ExecutionContext):
    Future[Map[RequestId, Either[NaptimeError, NaptimeResponse]]] = {

    Future.sequence {
      mergeMultigetRequests(requests, resourceName).map { case (topLevelRequest, sourceRequests) =>
        val request = Request(context.requestHeader, List(topLevelRequest))
        context.fetcher.data(request).map { response =>
          // TODO(bryan): Fix this `.head`
          val parsedElements = parseElements(response, requests.head.resourceSchema)
          val parsedElementsMap = parsedElements.map { element =>
            val id = Option(element.element.get("id"))
              .map(NaptimeResourceUtils.parseToJson)
              .getOrElse(JsNull)
            id -> element
          }.toMap
          sourceRequests.map { sourceRequest =>
            // TODO(bryan): Clean this up
            val ids = sourceRequest.arguments
              .find(_._1 == "ids")
              .map(_._2)
              .map {
                case JsArray(idValues) => idValues
                case value: JsValue => List(value)
              }
              .getOrElse(List.empty)

            val elements = ids.flatMap(parsedElementsMap.get).toList
            val url = response.topLevelResponses.headOption.flatMap(_._2.url).getOrElse("???")
            sourceRequest.idx ->
              Right[NaptimeError, NaptimeResponse](
                NaptimeResponse(elements, sourceRequest.paginationOverride, url))
          }.toMap
        }
      }
    }.map(_.flatten.toMap)
  }

  /**
    * Converts a list of forward requests into the most optimal list of requests to execute.
    * Multiget requests can be merged into a single request,
    * but _only_ if all of the query parameters are the same for the requests.
    *
    * @param requests A list of NaptimeRequests specifying the resource and arguments
    * @return a map of TopLevelRequests -> list of NaptimeRequests that it fulfills
    */
  def mergeMultigetRequests(
      requests: Vector[NaptimeRequest],
      resourceName: ResourceName):
    Map[TopLevelRequest, Vector[NaptimeRequest]] = {

    requests
      .groupBy(_.arguments.filterNot(_._1 == "ids"))
      .map { case (nonIdArguments, innerRequests) =>
        val ids = innerRequests.flatMap(_.arguments.find(_._1 == "ids").map(_._2)).distinct
        // TODO(bryan): Limit multiget requests by number of ids as well, to avoid http limits
        val topLevelRequest = TopLevelRequest(
          resourceName,
          RequestField(
            name = "",
            alias = None,
            args = nonIdArguments + ("ids" -> JsArray(ids)),
            selections = List.empty))
        topLevelRequest -> innerRequests
      }

  }

  /**
    * Fetches reverse relations for a specific resource given a list of requests
    *
    * In the event of an error, a NaptimeError is returned instead of a NaptimeResponse
    *
    * @param requests list of NaptimeRequests containing the endpoint and arguments (including ids)
    * @param resourceName resource that the requests is made against
    * @param context request context (includes things like header)
    * @return Map of request ids (indexes from the deferred request batching) to either a
    *         NaptimeError or NaptimeResponse
    */
  def fetchReverseRelations(
      requests: Vector[NaptimeRequest],
      resourceName: ResourceName,
      context: SangriaGraphQlContext)
      (implicit ec: ExecutionContext):
    Future[Map[RequestId, Either[NaptimeError, NaptimeResponse]]] = {

    Future.sequence {
      requests.map { request =>
        val topLevelRequest = TopLevelRequest(
          resourceName,
          RequestField(
            name = "",
            alias = None,
            args = request.arguments,
            selections = List.empty))
        val fetcherRequest = Request(context.requestHeader, List(topLevelRequest))
        context.fetcher.data(fetcherRequest).map { response =>
          (for {
            (_, topLevelResponse) <- response.topLevelResponses.headOption
          } yield {
            val elements = parseElements(response, requests.head.resourceSchema)
            Right(NaptimeResponse(
              elements,
              Some(topLevelResponse.pagination),
              topLevelResponse.url.getOrElse("???")))
          }).getOrElse {
            val url = response.topLevelResponses.headOption.flatMap(_._2.url).getOrElse("???")
            Left(NaptimeError(url, "Unknown error while fetching data."))
          }
        }.map(res => Map(request.idx -> res))
      }
    }.map(_.flatten.toMap)
  }

  /**
    * Helper to parse the elements in a response into a map of JsValue -> DataMapWithParent
    * @param response Response from the network call, containing data returned
    * @param resourceSchema schema that defines the shape of the response, for later use
    * @return Map of JsValue ids to DataMapWithParents
    */
  def parseElements(
      response: Response,
      resourceSchema: RecordDataSchema): List[DataMapWithParent] = {
    for {
      (_, topLevelResponse) <- response.topLevelResponses.headOption.toList
      (resourceName, data) <- response.data.toList // Should only be one resource, ideally
      id <- topLevelResponse.ids.asScala
      element <- data.get(id).toList
    } yield {
      DataMapWithParent(
        element,
        ParentModel(resourceName, element, resourceSchema))
    }
  }

  /**
    * Extracts a resource name from a list of NaptimeRequests.
    *
    * This method assumes that all requests will be for the same resource,
    * otherwise it will return None.
    *
    * @param requests a list of NaptimeRequests
    * @return a ResourceName if all requests are for the same resource, otherwise None
    */
  def getResourceName(requests: Vector[NaptimeRequest]): Option[ResourceName] = {
    val byResourceName = requests.groupBy(_.resourceName)
    if (byResourceName.size > 1) {
      logger.error("getResourceName detected a list of requests for more than one resource")
      None
    } else {
      byResourceName.headOption.map(_._1)
    }
  }


}
