package org.coursera.naptime.ari

import com.linkedin.data.DataMap
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.schema.Resource
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

import scala.concurrent.Future
import scala.collection.immutable

/**
 * The engine layer presents this EngineAPI to the presentation layer. The engine layer handles query validation,
 * execution planning & optimization, and finally orchestrates the fetching of data by leveraging the [[FetcherApi]].
 *
 * Engine's must be threadsafe as they are called from multiple threads simultaneously.
 */
trait EngineApi {
  /**
   * Fetches all data needed to formulate a response to the query.
   *
   * TODO: Consider returning an `Either[Set[Errors], Future[Response]]` instead for more structured validation errors
   *
   * @param request The resource request to execute.
   * @return A future of all available data needed to construct a response to the query. It is up to the presentation
   *         layer to turn that into the response format as required.
   */
  def execute(request: Request): Future[Response]

  /**
   * Gets the Naptime schema currently in use by the presentation layer.
   *
   * TODO: include the Courier model schemas
   */
  def schema: Seq[Resource]
}

/**
 * The fetcher calls the Naptime APIs (either local or remote) to acquire all of the data necessary.
 */
trait FetcherApi {
  // TODO: use better types here
  def data(request: Request): Future[Response]
}

/**
 * This encapsulates all of the information needed to compute a response.
 *
 * @param requestHeader The request header is used for authentication parsing in the underlying requests made. Path
 *                      and query parameters included in the request header are ignored in favor of the data within the
 *                      topLevelRequests fields.
 * @param topLevelRequests A non-empty list of "roots" used to begin queries. Every query begins with a "base" resource
 *                         and related models are joined upon it from there.
 */
case class Request(
  requestHeader: RequestHeader,
  topLevelRequests: Seq[TopLevelRequest])

/**
 * Encapsulates the starting root of a query into the naptime resource tree.
 * @param resource The name of the resource that forms the root of the request.
 * @param selection The field selection on the first resource.
 */
case class TopLevelRequest(resource: ResourceName, selection: RequestField)

/**
 * Represents a requested field within a requested resource.
 *
 * @param name The name of the requested field.
 * @param alias The name the field should be renamed to in the response.
 * @param args If the field takes parameters, they are encapsulated here.
 * @param selections The list of fields in the related resource.
 */
case class RequestField(
  name: String,
  alias: Option[String],
  args: Set[(String, JsValue)],
  selections: List[RequestField])

// TODO: Should JsValue be a specific ARI type?

/**
 * All of the data required to assemble a response to an automatic includes query.
 *
 * @param output A map from resource name to a response
 */
case class Response(
  output: Map[ResourceName, ResourceResponse]
)

// TODO: figure out directives

// TODO: key may change to a more sophisticated type. It should be a ResourceName, but with hydrated path parameters.
case class ResourceResponse(key: String, models: Seq[DataMap], pagination: ResponsePagination)
