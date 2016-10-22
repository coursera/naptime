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

package org.coursera.naptime.ari

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.schema.Resource
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

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
   * A mapping from resource name to a record data schema.
   * @return The merged type schema corresponding to the merged type name.
   */
  def mergedType(resourceName: ResourceName): Option[RecordDataSchema]

  /**
   * The collection of all Naptime resources available.
   */
  def fullSchema: FullSchema
}

/**
 * Contains the complete set of static type information to fully specify a Naptime service.
 *
 * @param resources The Resource schemas for all available resources.
 * @param types All of the data types that compose the service.
 */
case class FullSchema(resources: Set[Resource], types: Set[DataSchema])

object FullSchema {
  val empty = FullSchema(Set.empty, Set.empty)
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

case class ResponseMetrics(
  numRequests: Int = 0,
  duration: FiniteDuration = FiniteDuration(0, "seconds"))

/**
 * All of the data required to assemble a response to an automatic includes query.
 *
 * TODO: performance test, and determine if mutable collections yield non-trivial performance improvements.
 *
 * @param topLevelResponses A map from the top level requests to a TopLevelResponse,
  *                         containing ids and pagination
 * @param data A map from the resource name to a Map of IDs to DataMaps.
 */
case class Response(
  topLevelResponses: Map[TopLevelRequest, TopLevelResponse],
  data: Map[ResourceName, Map[AnyRef, DataMap]],
  metrics: ResponseMetrics = ResponseMetrics()) {

  // TODO: performance test this implementation, and consider optimizing it.
  // Note: this operation potentially mutates the current response due to interior mutability.
  def ++(other: Response): Response = {
    val mergedTopLevel = topLevelResponses ++ other.topLevelResponses
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
    val mergedMetrics = ResponseMetrics(
      numRequests = metrics.numRequests + other.metrics.numRequests,
      duration = metrics.duration + other.metrics.duration)
    Response(mergedTopLevel, mergedData, mergedMetrics)
  }
}

object Response {
  val empty = Response(Map.empty, Map.empty)
}

/**
  * Represents the response data from a TopLevelRequest, including returned ids and pagination
  *
  * @param ids a list of IDs returned by the top level request.
  *            (i.e. ids 5, 6, and 7 were returned by the bySlug finder)
  * @param pagination pagination info from the top level request, including total and next cursor
  */
case class TopLevelResponse(ids: DataList, pagination: ResponsePagination)
