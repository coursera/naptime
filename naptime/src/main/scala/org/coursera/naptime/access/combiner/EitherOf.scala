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

package org.coursera.naptime.access.combiner

import org.coursera.common.concurrent.Futures
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.access.HeaderAccessControl
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

private[access] trait EitherOf {

  /**
   * Right-leaning combiner. That is, it tries to return each of these, in order:
   *   - Right access control's successful result.
   *   - Left access control's successful result.
   *   - Right access control's error.
   *   - Left access control's error.
   */
  def eitherOf[A, B](
      controlA: HeaderAccessControl[A],
      controlB: HeaderAccessControl[B]): HeaderAccessControl[Either[A, B]] = {

    new HeaderAccessControl[Either[A, B]] {
      override def run(requestHeader: RequestHeader)(
          implicit ec: ExecutionContext): Future[Either[NaptimeActionException, Either[A, B]]] = {

        val futureA = Futures.safelyCall(controlA.run(requestHeader))
        val futureB = Futures.safelyCall(controlB.run(requestHeader))

        for {
          resultA <- futureA
          resultB <- futureB
        } yield {
          (resultA, resultB) match {
            case (_, Right(authentication)) => Right(Right(authentication))
            case (Right(authentication), _) => Right(Left(authentication))
            case (_, Left(error))           => Left(error)
            case (Left(error), _)           => Left(error)
          }
        }
      }

      override private[naptime] def check(
          authInfo: Either[A, B]): Either[NaptimeActionException, Either[A, B]] = {
        authInfo match {
          case Left(authA) =>
            controlA.check(authA).right.map(Left.apply)
          case Right(authB) =>
            controlB.check(authB).right.map(Right.apply)
        }
      }
    }
  }

}
