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

import com.linkedin.data.DataMap
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.schema.Resource
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * The fetcher calls the Naptime APIs (either local or remote) to acquire all of the data necessary.
 */
trait FetcherApi {
  type FetcherResponse = Either[FetcherError, Response]
  def data(request: Request, isDebugMode: Boolean)(
      implicit executionContext: ExecutionContext): Future[FetcherResponse]
}

case class FetcherError(code: Int, message: String, url: Option[String])

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
 * @param resource The name and version of the resource that this request is hitting
 * @param arguments A list of query parameters to be used on the request. These are used to
 *                  differentiate between various endpoints (i.e. multiget, finder, etc.).
 */
case class Request(
    requestHeader: RequestHeader,
    resource: ResourceName,
    arguments: Set[(String, JsValue)])

/**
 * This model represents a response from a [[Request]], including elements and pagination
 *
 * @param data A list of elements in the response, in the order that they're returned
 * @param pagination The response pagination information (including total and next)
 * @param url The source url that was used to make the request.
 *            This is implementation specific by the fetcher, so it cannot be part of the Request.
 */
case class Response(data: List[DataMap], pagination: ResponsePagination, url: Option[String])

object Response {
  def empty = Response(List.empty, ResponsePagination.empty, None)
}
