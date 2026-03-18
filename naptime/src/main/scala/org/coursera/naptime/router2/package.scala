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

package org.coursera.naptime

import play.api.libs.typedmap.TypedKey
import play.api.mvc.EssentialAction
import play.api.mvc.RequestHeader

package object router2 {

  /**
   * Naptime's replacement for Play's removed RequestTaggingHandler.
   * Allows route actions to annotate the request with resource metadata.
   */
  trait NaptimeRequestTaggingHandler {
    def tagRequest(request: RequestHeader): RequestHeader
  }

  type RouteAction = EssentialAction with NaptimeRequestTaggingHandler

  /**
   * Typed attribute keys for naptime request metadata (replaces RequestAttrKey.Tags).
   */
  object NaptimeAttrKey {
    val tags: TypedKey[Map[String, String]] = TypedKey("Naptime.Tags")
  }

}
