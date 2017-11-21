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

package org.coursera.naptime.actions

import org.coursera.naptime.Fields
import org.coursera.naptime.QueryIncludes
import org.coursera.naptime.RequestFields
import org.coursera.naptime.RequestPagination
import org.coursera.naptime.RestResponse
import play.api.mvc.RequestHeader
import play.api.mvc.Result

import scala.annotation.implicitNotFound

/**
 * Maps a high-level REST response to a low-level HTTP response.
 */
@implicitNotFound("""No RestActionCategoryEngine found for category: ${Category},
    key type: ${Key} and resource type: ${Resource} and response type: ${Response}.
    Most likely, you have an inappropriate response type for your action. Please ensure you are
    returning the right type for this action.""")
// sealed // TODO(saeta): Re-add back in sealing by moving 2nd gen engines to this file.
trait RestActionCategoryEngine[Category, Key, Resource, Response] {
  private[naptime] def mkResult(
      request: RequestHeader,
      resourceFields: Fields[Resource],
      requestFields: RequestFields,
      requestIncludes: QueryIncludes,
      pagination: RequestPagination,
      response: RestResponse[Response]): Result
}

object RestActionCategoryEngine extends RestActionCategoryEngine2Impls // Courier-centric engines
