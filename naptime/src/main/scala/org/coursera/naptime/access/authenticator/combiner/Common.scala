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

package org.coursera.naptime.access.authenticator.combiner

import org.coursera.naptime.NaptimeActionException

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

private[combiner] object Common {

  /**
   * Creates a combined authentication result, given futures for the first success and the first
   * failure. If neither success nor failure exists, returns skip.
   */
  def combineAuthenticationResponses[A](
      successOptionFuture: Future[Option[Either[NaptimeActionException, A]]],
      errorOptionFuture: Future[Option[Either[NaptimeActionException, A]]])(
      implicit ec: ExecutionContext): Future[Option[Either[NaptimeActionException, A]]] = {

    successOptionFuture.flatMap { successOption =>
      // If there's a successful authentication, use it.
      successOption.map(success => Future.successful(Some(success))).getOrElse {
        // Otherwise, if there's an authentication error, use that.
        // TODO(josh): Should this only return errors if ALL authentications returned errors?
        errorOptionFuture
      }
      // If there's nothing, all authentications skipped, so the combined one should skip.
    }
  }

}
