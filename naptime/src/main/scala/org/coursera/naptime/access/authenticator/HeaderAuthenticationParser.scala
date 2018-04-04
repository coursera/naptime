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

package org.coursera.naptime.access.authenticator

import play.api.mvc.RequestHeader

/**
 * Immediate header parser. Tries to find authentication information.
 *
 * [[HeaderAuthenticationParser]]s should not throw exceptions. On error, they should return
 * [[ParseResult.Error]] with an error message suitable for returning to the Naptime client.
 *
 * @tparam P Parsed authentication data, entirely from the request header.
 */
trait HeaderAuthenticationParser[+P] {
  def parseHeader(requestHeader: RequestHeader): ParseResult[P]
}

object HeaderAuthenticationParser {

  /** A no-op parser that always returns `value`. */
  def constant[P](value: P): HeaderAuthenticationParser[P] =
    new HeaderAuthenticationParser[P] {
      override def parseHeader(requestHeader: RequestHeader): ParseResult[P] = {
        ParseResult.Success(value)
      }
    }

}
