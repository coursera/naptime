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

import org.coursera.common.concurrent.Futures
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.access.authenticator.Authenticator
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

private[authenticator] trait AnyOf {

  /**
   * Combines an UNORDERED collection of [[Authenticator]]s into an authenticator that:
   *   1. If any authentication succeeds, return an arbitrary successful authenticator's response.
   *   2. If any returns an error, return an arbitrary authenticator's error.
   *   3. If all skip, return a skip response.
   *
   * Note: the weakness of ordering guarantees here allows optimization like not waiting for all
   * authenticators to respond if an early one is successful.
   *
   * TODO(josh): Should existence of even a single error trigger failure?
   * TODO(josh): Write unit tests.
   */
  def anyOf[A](authenticators: Set[Authenticator[A]]): Authenticator[A] =
    new Authenticator[A] {

      override def maybeAuthenticate(requestHeader: RequestHeader)(
          implicit ec: ExecutionContext): Future[Option[Either[NaptimeActionException, A]]] = {

        val authenticationResponses = authenticators
          .map(Authenticator.authenticateAndRecover(_, requestHeader))

        val successOptionFuture = Futures.findMatch(authenticationResponses) {
          case Some(Right(authentication)) => Right(authentication)
        }
        lazy val errorOptionFuture =
          Futures.findMatch(authenticationResponses) {
            case Some(Left(error)) => Left(error)
          }

        Common.combineAuthenticationResponses(successOptionFuture, errorOptionFuture)
      }

    }

  def anyOf[A, A1, A2](authenticator1: Authenticator[A1], authenticator2: Authenticator[A2])(
      implicit transformer1: AuthenticationTransformer[A1, A],
      transformer2: AuthenticationTransformer[A2, A]): Authenticator[A] = {

    anyOf(
      Set(
        authenticator1.collect(transformer1.partial),
        authenticator2.collect(transformer2.partial)))
  }

  def anyOf[A, A1, A2, A3](
      authenticator1: Authenticator[A1],
      authenticator2: Authenticator[A2],
      authenticator3: Authenticator[A3])(
      implicit transformer1: AuthenticationTransformer[A1, A],
      transformer2: AuthenticationTransformer[A2, A],
      transformer3: AuthenticationTransformer[A3, A]): Authenticator[A] = {

    anyOf(
      Set(
        authenticator1.collect(transformer1.partial),
        authenticator2.collect(transformer2.partial),
        authenticator3.collect(transformer3.partial)))
  }

  // TODO(josh): Generate for remaining arities.

}
