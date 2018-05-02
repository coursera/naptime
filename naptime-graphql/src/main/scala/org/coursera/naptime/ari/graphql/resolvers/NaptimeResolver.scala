package org.coursera.naptime.ari.graphql.resolvers

import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.schema.DataMapWithParent
import org.coursera.naptime.ari.graphql.schema.NaptimeResourceUtils
import org.coursera.naptime.ari.graphql.schema.ParentModel
import play.api.libs.json.JsArray
import play.api.libs.json.JsNull
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader
import sangria.execution.deferred.Deferred
import sangria.execution.deferred.DeferredResolver

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
    url: String,
    status: Int = 200,
    errorMessage: Option[String] = None)

case class NaptimeError(url: String, status: Int, errorMessage: String)

sealed trait DeferredNaptime {
  def toNaptimeRequest(idx: Int): NaptimeRequest
}

case class DeferredNaptimeRequest(
    resourceName: ResourceName,
    arguments: Set[(String, JsValue)],
    resourceSchema: RecordDataSchema,
    paginationOverride: Option[ResponsePagination] = None)
    extends Deferred[Either[NaptimeError, NaptimeResponse]]
    with DeferredNaptime {

  def toNaptimeRequest(idx: Int): NaptimeRequest = {
    NaptimeRequest(RequestId(idx), resourceName, arguments, resourceSchema, paginationOverride)
  }
}

case class DeferredNaptimeElement(
    resourceName: ResourceName,
    idOpt: Option[JsValue],
    arguments: Set[(String, JsValue)],
    resourceSchema: RecordDataSchema)
    extends Deferred[Either[NaptimeError, NaptimeResponse]]
    with DeferredNaptime {

  def toNaptimeRequest(idx: Int): NaptimeRequest = {
    NaptimeRequest(
      RequestId(idx),
      resourceName,
      arguments ++ idOpt
        .map(id => List("ids" -> JsArray(List(id))))
        .getOrElse(List.empty),
      resourceSchema)
  }
}

case class RequestId(idx: Int)

class NaptimeResolver extends DeferredResolver[SangriaGraphQlContext] with StrictLogging {
  def resolve(deferred: Vector[Deferred[Any]], ctx: SangriaGraphQlContext, queryState: Any)(
      implicit ec: ExecutionContext): Vector[Future[Any]] = {

    val naptimeRequests = deferred.zipWithIndex.collect {
      case (d: DeferredNaptime, idx: Int) => d.toNaptimeRequest(idx)
    }

    val dataByResource = naptimeRequests
      .groupBy(_.resourceName)
      .map {
        case (resourceName, requests) =>
          val (forwardRequests, reverseRequests) =
            requests.partition(_.arguments.exists(_._1 == "ids"))

          // Handle MultiGet and Non-Multigets differently, since multigets can be batched
          val forwardRelations =
            fetchForwardRelations(forwardRequests, resourceName, ctx)
          val reverseRelations =
            fetchReverseRelations(reverseRequests, resourceName, ctx)
          Future
            .sequence(List(forwardRelations, reverseRelations))
            .map(_.flatten.toMap)

      }
    val allData = Future.sequence(dataByResource).map(_.flatten.toMap)

    deferred.zipWithIndex.map {
      case (_, idx) =>
        allData.map(_.getOrElse(RequestId(idx), {
          throw new RuntimeException("Error in NaptimeResolver. Could not find outgoing request.")
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
      context: SangriaGraphQlContext)(implicit ec: ExecutionContext)
    : Future[Map[RequestId, Either[NaptimeError, NaptimeResponse]]] = {

    Future
      .sequence {
        mergeMultigetRequests(context.requestHeader, requests, resourceName)
          .map {
            case (request, sourceRequests) =>
              context.fetcher.data(request, context.debugMode).map {
                case Right(successfulResponse) =>
                  val parsedElements =
                    parseElements(request, successfulResponse, requests.head.resourceSchema)
                  val parsedElementsMap = parsedElements.map { element =>
                    val id = Option(element.element.get("id"))
                      .map(NaptimeResourceUtils.parseToJson)
                      .getOrElse(JsNull)
                    id -> element
                  }.toMap
                  sourceRequests.map { sourceRequest =>
                    // TODO(bryan): Clean this up
                    val elements = parseIds(sourceRequest)
                      .flatMap(parsedElementsMap.get)
                      .toList
                    val url = successfulResponse.url.getOrElse("???")
                    sourceRequest.idx ->
                      Right[NaptimeError, NaptimeResponse](
                        NaptimeResponse(elements, sourceRequest.paginationOverride, url, 200, None))
                  }.toMap
                case Left(error) =>
                  sourceRequests.map { sourceRequest =>
                    sourceRequest.idx ->
                      Left(NaptimeError(error.url.getOrElse("???"), error.code, error.message))
                  }.toMap
              }
          }
      }
      .map(_.flatten.toMap)
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
      header: RequestHeader,
      requests: Vector[NaptimeRequest],
      resourceName: ResourceName): Map[Request, Vector[NaptimeRequest]] = {

    requests
      .groupBy(_.arguments.filterNot(_._1 == "ids"))
      .map {
        case (nonIdArguments, innerRequests) =>
          // TODO(bryan): Limit multiget requests by number of ids as well, to avoid http limits
          Request(
            header,
            resourceName,
            nonIdArguments + ("ids" -> JsArray(parseAndMergeIds(innerRequests)))) -> innerRequests
      }
  }

  private[this] def parseAndMergeIds(requests: Vector[NaptimeRequest]): Seq[JsValue] = {
    requests.flatMap(parseIds).distinct
  }

  private[this] def parseIds(request: NaptimeRequest): Seq[JsValue] = {
    // .toSeq here is to preserve id ordering in related resource id arrays
    request.arguments.toSeq
      .filter { case (key, _) => key == "ids" }
      .map(_._2)
      .flatMap {
        case JsArray(idValues) => idValues
        case value: JsValue    => List(value)
      }
      .distinct
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
      context: SangriaGraphQlContext)(implicit ec: ExecutionContext)
    : Future[Map[RequestId, Either[NaptimeError, NaptimeResponse]]] = {
    Future
      .sequence {
        requests.map { request =>
          val fetcherRequest =
            Request(context.requestHeader, resourceName, request.arguments)
          context.fetcher
            .data(fetcherRequest, context.debugMode)
            .map {
              case Right(response) =>
                val elements = parseElements(fetcherRequest, response, requests.head.resourceSchema)
                Right(
                  NaptimeResponse(
                    elements,
                    Some(response.pagination),
                    response.url.getOrElse("???")))
              case Left(error) =>
                Left(NaptimeError(error.url.getOrElse("???"), error.code, error.message))
            }
            .map(res => Map(request.idx -> res))
        }
      }
      .map(_.flatten.toMap)
  }

  /**
   * Helper to parse the elements in a response into a map of JsValue -> DataMapWithParent
   * @param response Response from the network call, containing data returned
   * @param resourceSchema schema that defines the shape of the response, for later use
   * @return Map of JsValue ids to DataMapWithParents
   */
  def parseElements(
      request: Request,
      response: Response,
      resourceSchema: RecordDataSchema): List[DataMapWithParent] = {
    response.data.map { element =>
      DataMapWithParent(element, ParentModel(request.resource, element, resourceSchema))
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
