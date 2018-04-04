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
import org.coursera.naptime.access.authenticator.Authenticator
import org.coursera.naptime.access.authenticator.Decorator
import org.coursera.naptime.access.authenticator.HeaderAuthenticationParser
import org.coursera.naptime.access.authorizer.AuthorizeResult
import org.coursera.naptime.access.authorizer.Authorizer
import org.coursera.naptime.access.combiner.And
import org.coursera.naptime.access.combiner.AnyOf
import org.coursera.naptime.access.combiner.EitherOf
import org.coursera.naptime.access.combiner.SuccessfulOf
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Control access to an API based on the [[RequestHeader]].
 *
 * See [[StructuredAccessControl]] for a way to implement access control based on separated
 * authentication and authorization.
 *
 * Note that access control based on the request body is not supported right now. Consider
 * construction your [[HeaderAccessControl]] (using body parameters as necessary) and invoking
 * `runAndCheck` in your API body.
 */
trait HeaderAccessControl[A] {

  def runAndCheck(requestHeader: RequestHeader)(
      implicit executionContext: ExecutionContext): Future[A] =
    run(requestHeader).map(_.left.map(throw _).merge)

  def run(requestHeader: RequestHeader)(
      implicit executionContext: ExecutionContext): Future[Either[NaptimeActionException, A]]

  /**
   * Used for testing access control configurations in Naptime tests.
   */
  private[naptime] def check(authInfo: A): Either[NaptimeActionException, A]
}

object HeaderAccessControl extends AnyOf with And with EitherOf with SuccessfulOf {

  def allowAll: HeaderAccessControl[Unit] = {
    val parser = HeaderAuthenticationParser.constant(())
    val authorizer = Authorizer[Unit](_ => AuthorizeResult.Authorized)
    StructuredAccessControl(Authenticator(parser, Decorator.identity[Unit]), authorizer)
  }

}
