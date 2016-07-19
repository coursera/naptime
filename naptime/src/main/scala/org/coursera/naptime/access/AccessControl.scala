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

package org.coursera.naptime.access

import org.coursera.naptime.NaptimeActionException

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Control access to a resource method. Arbitrary, asynchronous logic may be executed, but
 * successful access grant must produce an authentication object of type [[A]].
 *
 * @tparam R request data (for example, for Play HTTP requests, this is
 *           [[play.api.mvc.RequestHeader]])
 * @tparam A structured authentication result which is available to the API implementation after
 *           access control is successful
 */
trait AccessControl[-R, A] {

  def run(request: R)(implicit ec: ExecutionContext): Future[Either[NaptimeActionException, A]]

  final def runAndCheck(request: R)(implicit ec: ExecutionContext): Future[A] =
    run(request).map(_.left.map(throw _).merge)

  /**
   * Used for exercising access control configurations in resource tests. Simulates the result of
   * the authentication process (since that often involves data fetching, and should be unit tested
   * separately from resources).
   */
  private[naptime] def simulateAuthentication(authentication: A): Either[NaptimeActionException, A]

}
