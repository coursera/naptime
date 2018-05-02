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
import org.coursera.naptime.access.StructuredAccessControl
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Allows the request if at least one of access controls allow it.
 *
 * The authentication data are exposed as a tuple containing [[Option]]s where at least one
 * is defined.
 */
private[access] trait AnyOf {

  /** See [[AnyOf]]. */
  def anyOf[A, B](
      controlA: HeaderAccessControl[A],
      controlB: HeaderAccessControl[B]): HeaderAccessControl[(Option[A], Option[B])] = {

    new HeaderAccessControl[(Option[A], Option[B])] {
      override def run(requestHeader: RequestHeader)(implicit ec: ExecutionContext)
        : Future[Either[NaptimeActionException, (Option[A], Option[B])]] = {

        val futureA = Futures.safelyCall(controlA.run(requestHeader))
        val futureB = Futures.safelyCall(controlB.run(requestHeader))

        for {
          resultA <- futureA
          resultB <- futureB
        } yield {
          (resultA, resultB) match {
            case (Left(errorA), Left(_)) =>
              Left(errorA) // Ignore the other error.
            case _ =>
              Right((resultA.right.toOption, resultB.right.toOption))
          }
        }
      }

      override private[naptime] def check(authInfo: (Option[A], Option[B]))
        : Either[NaptimeActionException, (Option[A], Option[B])] = {
        val resultA = computeCheckResult(authInfo._1, controlA)
        val resultB = computeCheckResult(authInfo._2, controlB)

        (resultA, resultB) match {
          case (Left(errorA), Left(_)) => Left(errorA)
          case _ =>
            Right((resultA.right.toOption, resultB.right.toOption))
        }
      }
    }
  }

  /** See [[AnyOf]]. */
  def anyOf[A, B, C](
      controlA: HeaderAccessControl[A],
      controlB: HeaderAccessControl[B],
      controlC: HeaderAccessControl[C]): HeaderAccessControl[(Option[A], Option[B], Option[C])] = {

    new HeaderAccessControl[(Option[A], Option[B], Option[C])] {
      override def run(requestHeader: RequestHeader)(implicit ec: ExecutionContext)
        : Future[Either[NaptimeActionException, (Option[A], Option[B], Option[C])]] = {

        val futureA = Futures.safelyCall(controlA.run(requestHeader))
        val futureB = Futures.safelyCall(controlB.run(requestHeader))
        val futureC = Futures.safelyCall(controlC.run(requestHeader))

        for {
          resultA <- futureA
          resultB <- futureB
          resultC <- futureC
        } yield {
          (resultA, resultB, resultC) match {
            case (Left(errorA), Left(_), Left(_)) =>
              Left(errorA) // Ignore the other errors.
            case _ =>
              Right((resultA.right.toOption, resultB.right.toOption, resultC.right.toOption))
          }
        }
      }

      override private[naptime] def check(authInfo: (Option[A], Option[B], Option[C]))
        : Either[NaptimeActionException, (Option[A], Option[B], Option[C])] = {

        val resultA = computeCheckResult(authInfo._1, controlA)
        val resultB = computeCheckResult(authInfo._2, controlB)
        val resultC = computeCheckResult(authInfo._3, controlC)

        (resultA, resultB, resultC) match {
          case (Left(errorA), Left(_), Left(_)) => Left(errorA)
          case _ =>
            Right((resultA.right.toOption, resultB.right.toOption, resultC.right.toOption))
        }
      }
    }
  }

  /**
   * Helper for the `check` functions to run the check functionality of a HeaderAccessControl.
   */
  private[this] def computeCheckResult[T](
      elem: Option[T],
      accessControl: HeaderAccessControl[T]): Either[NaptimeActionException, T] = {
    elem
      .map(e => accessControl.check(e))
      .getOrElse(StructuredAccessControl.missingResponse)
  }

}
