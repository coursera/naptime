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

/**
 * Output of [[HeaderAuthenticationParser]].
 *
 * @tparam P Parsed authentication data.
 */
sealed trait ParseResult[+P] {
  def flatMap[Q](f: P => ParseResult[Q]): ParseResult[Q]
}

object ParseResult {

  /** Authentication data successfully parsed from header. */
  case class Success[P](parsed: P) extends ParseResult[P] {
    override def flatMap[Q](f: P => ParseResult[Q]): ParseResult[Q] = f(parsed)
  }

  /**
   * Parsing skipped for this authentication type. This authentication was likely not used
   * in this request.
   */
  case object Skip extends ParseResult[Nothing] {
    override def flatMap[Q](f: Nothing => ParseResult[Q]): ParseResult[Q] = this
  }

  /**
   * Fatal error while parsing this authentication type. Always returns HTTP 401 to the client.
   */
  case class Error(message: String) extends ParseResult[Nothing] {
    override def flatMap[Q](f: Nothing => ParseResult[Q]): ParseResult[Q] = this
  }

}
