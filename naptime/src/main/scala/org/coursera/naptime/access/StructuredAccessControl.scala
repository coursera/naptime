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

import org.coursera.common.concurrent.Futures
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.access.authenticator.Authenticator
import org.coursera.naptime.access.authorizer.AuthorizeResult
import org.coursera.naptime.access.authorizer.Authorizer
import play.api.http.Status
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Implementation of [[HeaderAccessControl]] with more structure, separating the authentication
 * and authorization phases of access control to promote reusability.
 */
case class StructuredAccessControl[A](
    authenticator: Authenticator[A],
    authorizer: Authorizer[A])
  extends HeaderAccessControl[A] {

  override def run(
      requestHeader: RequestHeader)
      (implicit executionContext: ExecutionContext): Future[Either[NaptimeActionException, A]] = {

    authenticator.maybeAuthenticate(requestHeader).map { decoratedOption =>
      decoratedOption.map { either =>
        either.right.flatMap { decorated =>
          Authorizer.toResponse(authorizer.authorize(decorated), decorated)
        }
      }.getOrElse {
        StructuredAccessControl.missingResponse
      }
    }
  }
}

object StructuredAccessControl {

  private[access] val missingResponse = {
    Left(NaptimeActionException(
      Status.UNAUTHORIZED,
      Some("auth.perms"),
      Some("Missing authentication")))
  }

  /**
   * Left-leaning combiner. That is, it tries to return each of these, in order:
   *   - Left access control's successful result.
   *   - Right access control's successful result.
   *   - Left access control's error.
   *   - Right access control's error.
   */
  def eitherOf[A, B](
      left: StructuredAccessControl[A],
      right: StructuredAccessControl[B])
      (implicit ec: ExecutionContext): StructuredAccessControl[Either[A, B]] = {
    StructuredAccessControl(
      Authenticator.eitherOf(left.authenticator, right.authenticator),
      Authorizer.eitherOf(left.authorizer, right.authorizer))
  }

}
