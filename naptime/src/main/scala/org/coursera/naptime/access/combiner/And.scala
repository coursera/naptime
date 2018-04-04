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

/**
 * Allows the request if all access controls allow it.
 */
private[access] trait And {

  /** See [[And]]. */
  def and[A, B](
      controlA: HeaderAccessControl[A],
      controlB: HeaderAccessControl[B]): HeaderAccessControl[(A, B)] = {

    new HeaderAccessControl[(A, B)] {
      override def run(requestHeader: RequestHeader)(
          implicit ec: ExecutionContext): Future[Either[NaptimeActionException, (A, B)]] = {

        val futureA = Futures.safelyCall(controlA.run(requestHeader))
        val futureB = Futures.safelyCall(controlB.run(requestHeader))

        for {
          resultA <- futureA
          resultB <- futureB
        } yield {
          (resultA, resultB) match {
            case (Right(authenticationA), Right(authenticationB)) =>
              Right((authenticationA, authenticationB))
            case _ =>
              List(resultA, resultB).collectFirst {
                case Left(error) => Left(error)
              }.head
          }
        }
      }
      override private[naptime] def check(
          authInfo: (A, B)): Either[NaptimeActionException, (A, B)] = {
        (controlA.check(authInfo._1), controlB.check(authInfo._2)) match {
          case (Right(resultA), Right(resultB)) => Right((resultA, resultB))
          case (Left(err), _)                   => Left(err)
          case (_, Left(err))                   => Left(err)
        }
      }
    }
  }

  /** See [[And]]. */
  def and[A, B, C](
      controlA: HeaderAccessControl[A],
      controlB: HeaderAccessControl[B],
      controlC: HeaderAccessControl[C]): HeaderAccessControl[(A, B, C)] = {

    new HeaderAccessControl[(A, B, C)] {
      override def run(requestHeader: RequestHeader)(
          implicit ec: ExecutionContext): Future[Either[NaptimeActionException, (A, B, C)]] = {

        val futureA = Futures.safelyCall(controlA.run(requestHeader))
        val futureB = Futures.safelyCall(controlB.run(requestHeader))
        val futureC = Futures.safelyCall(controlC.run(requestHeader))

        for {
          resultA <- futureA
          resultB <- futureB
          resultC <- futureC
        } yield {
          (resultA, resultB, resultC) match {
            case (Right(authenticationA), Right(authenticationB), Right(authenticationC)) =>
              Right((authenticationA, authenticationB, authenticationC))
            case _ =>
              List(resultA, resultB).collectFirst {
                case Left(error) => Left(error)
              }.head
          }
        }
      }

      override private[naptime] def check(
          authInfo: (A, B, C)): Either[NaptimeActionException, (A, B, C)] = {
        (controlA.check(authInfo._1), controlB.check(authInfo._2), controlC.check(authInfo._3)) match {
          case (Right(a), Right(b), Right(c)) => Right((a, b, c))
          case (Left(err), _, _)              => Left(err)
          case (_, Left(err), _)              => Left(err)
          case (_, _, Left(err))              => Left(err)
        }
      }
    }
  }
}
