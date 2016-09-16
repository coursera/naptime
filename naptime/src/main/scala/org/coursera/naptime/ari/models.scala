package org.coursera.naptime.ari

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.schema.Resource
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

import scala.concurrent.Future

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
}

/**
 * The fetcher calls the Naptime APIs (either local or remote) to acquire all of the data necessary.
 */
trait FetcherApi {
  def data(request: Request): Future[Response]
}

/**
 * Provides the metadata required to power the engine.
 *
 * For local-only operation, a LocalSchemaProvider implements this interface. For distributed
 * operations, a more sophisticated SchemaProvider must be implemented.
 */
trait SchemaProvider {
  /**
   * A mapping from a given resource name to the resource's schema.
   * @return The resource's schema.
   */
  def resourceSchema(resourceName: ResourceName): Option[Resource]

  /**
   * A mapping from resource name to a record data schema.
   * @return The merged type schema corresponding to the merged type name.
   */
  def mergedType(resourceName: ResourceName): Option[RecordDataSchema]
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
 *
 * @param resource The name of the resource that forms the root of the request.
 * @param selection The field selection on the first resource.
 */
case class TopLevelRequest(resource: ResourceName, selection: RequestField, alias: Option[String] = None)

/**
 * Represents a requested field within a requested resource.
 *
 * TODO: figure out directives
 *
 * @param name The name of the requested field.
 * @param alias The name the field should be renamed to in the response.
 * @param args If the field takes parameters, they are encapsulated here.
 * @param selections The list of fields in the related resource.
 */
case class RequestField(
  name: String,
  alias: Option[String],
  args: Set[(String, JsValue)], // TODO: Should JsValue be a specific ARI type?
  selections: List[RequestField])

/**
 * All of the data required to assemble a response to an automatic includes query.
 *
 * TODO: performance test, and determine if mutable collections yield non-trivial performance improvements.
 *
 * @param topLevelIds A map from the top level requests to a DataList containing the ordered list of IDs for the
 *                    top of the response
 * @param data A map from the resource name to a Map of IDs to DataMaps.
 */
case class Response(
  topLevelIds: Map[TopLevelRequest, DataList],
  data: Map[ResourceName, Map[AnyRef, DataMap]]) {

  // TODO: performance test this implementation, and consider optimizing it.
  // Note: this operation potentially mutates the current response due to interior mutability.
  def ++(other: Response): Response = {
    val mergedTopLevel = topLevelIds ++ other.topLevelIds
    val mergedData = (data.keySet ++ other.data.keySet).map { resourceName =>
      val lhs = data.getOrElse(resourceName, Map.empty)
      val rhs = other.data.getOrElse(resourceName, Map.empty)
      val mergedMap = (lhs.keySet ++ rhs.keySet).map { key =>
        val lh = lhs.get(key)
        val rh = rhs.get(key)
        val merged = (lh, rh) match {
          case (None, None) =>
            throw new IllegalStateException(s"Neither map had an entry for key $key.")
          case (Some(dm), None) => dm
          case (None, Some(dm)) => dm
          case (Some(l), Some(r)) =>
            l.putAll(r)
            l
        }
        key -> merged
      }.toMap
      resourceName -> mergedMap
    }.toMap
    Response(mergedTopLevel, mergedData)
  }
}

object Response {
  val empty = Response(Map.empty, Map.empty)
}
