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
import org.coursera.naptime.access.authenticator.Authenticator
import play.api.mvc.RequestHeader

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

private[authenticator] trait FirstOf {

  /**
   * Combines an ORDERED collection of [[Authenticator]]s into an authenticator that:
   *   1. If any authentication succeeds, returns the response from the first in the list that
   *      succeeded.
   *   2. If any returns an error, return the response from the first in the list that failed.
   *   3. If all skip, return a skip response.
   *
   * TODO(josh): Write unit tests.
   */
  def firstOf[A](authenticators: immutable.Seq[Authenticator[A]]): Authenticator[A] = {
    new Authenticator[A] {

      override def maybeAuthenticate(requestHeader: RequestHeader)(
          implicit ec: ExecutionContext): Future[Option[Either[NaptimeActionException, A]]] = {

        val authenticationResponses = authenticators
          .map(Authenticator.authenticateAndRecover(_, requestHeader))

        /**
         * Takes the first defined.
         *
         * @return the first non-`None` element from the list of `Future`s, or `None` if all
         * elements are `None`.
         */
        def takeFirstDefined(futures: List[Future[Option[Either[NaptimeActionException, A]]]])
          : Future[Option[Either[NaptimeActionException, A]]] = {

          futures match {
            case head :: tail =>
              head.flatMap { successOrSkip =>
                successOrSkip
                  .map(success => Future.successful(Some(success)))
                  .getOrElse(takeFirstDefined(tail))
              }
            case Nil => Future.successful(None)
          }
        }

        val successOptionFuture = {
          val successesOrSkips = authenticationResponses.map { future =>
            future.map {
              case success @ Some(Right(_)) => success
              case _                        => None
            }
          }
          takeFirstDefined(successesOrSkips.toList)
        }

        lazy val errorOptionFuture = {
          val errorsOrSkips = authenticationResponses.map { future =>
            future.map {
              case error @ Some(Left(_)) => error
              case _                     => None
            }
          }
          takeFirstDefined(errorsOrSkips.toList)
        }

        Common.combineAuthenticationResponses(successOptionFuture, errorOptionFuture)
      }

    }

  }

  def firstOf[A, A1, A2](authenticator1: Authenticator[A1], authenticator2: Authenticator[A2])(
      implicit transformer1: AuthenticationTransformer[A1, A],
      transformer2: AuthenticationTransformer[A2, A]): Authenticator[A] = {

    firstOf(
      List(
        authenticator1.collect(transformer1.partial),
        authenticator2.collect(transformer2.partial)))
  }

  def firstOf[A, A1, A2, A3](
      authenticator1: Authenticator[A1],
      authenticator2: Authenticator[A2],
      authenticator3: Authenticator[A3])(
      implicit transformer1: AuthenticationTransformer[A1, A],
      transformer2: AuthenticationTransformer[A2, A],
      transformer3: AuthenticationTransformer[A3, A]): Authenticator[A] = {

    firstOf(
      List(
        authenticator1.collect(transformer1.partial),
        authenticator2.collect(transformer2.partial),
        authenticator3.collect(transformer3.partial)))
  }

  def firstOf[A, A1, A2, A3, A4](
      authenticator1: Authenticator[A1],
      authenticator2: Authenticator[A2],
      authenticator3: Authenticator[A3],
      authenticator4: Authenticator[A4])(
      implicit transformer1: AuthenticationTransformer[A1, A],
      transformer2: AuthenticationTransformer[A2, A],
      transformer3: AuthenticationTransformer[A3, A],
      transformer4: AuthenticationTransformer[A4, A]): Authenticator[A] = {

    firstOf(
      List(
        authenticator1.collect(transformer1.partial),
        authenticator2.collect(transformer2.partial),
        authenticator3.collect(transformer3.partial),
        authenticator4.collect(transformer4.partial)
      ))
  }

  def firstOf[A, A1, A2, A3, A4, A5](
      authenticator1: Authenticator[A1],
      authenticator2: Authenticator[A2],
      authenticator3: Authenticator[A3],
      authenticator4: Authenticator[A4],
      authenticator5: Authenticator[A5])(
      implicit transformer1: AuthenticationTransformer[A1, A],
      transformer2: AuthenticationTransformer[A2, A],
      transformer3: AuthenticationTransformer[A3, A],
      transformer4: AuthenticationTransformer[A4, A],
      transformer5: AuthenticationTransformer[A5, A]): Authenticator[A] = {

    firstOf(
      List(
        authenticator1.collect(transformer1.partial),
        authenticator2.collect(transformer2.partial),
        authenticator3.collect(transformer3.partial),
        authenticator4.collect(transformer4.partial),
        authenticator5.collect(transformer5.partial)
      ))
  }

  // TODO(josh): Generate for remaining arities.

}
