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
import play.api.http.Status
import play.api.mvc.RequestHeader

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Allows the request if at least one access control allows it and exposes all authentications
 * from successful access controls.
 *
 * If all fail, arbitrarily picks one of the errors to return.
 */
private[access] trait SuccessfulOf {

  import SuccessfulOf._

  /**
   * See [[SuccessfulOf]].
   *
   * @param controls Note: this is `immutable.Seq` because it's covariant, but order doesn't matter
   */
  def successfulOf[A](
      controls: immutable.Seq[HeaderAccessControl[A]]): HeaderAccessControl[Set[A]] = {
    new HeaderAccessControl[Set[A]] {
      override def run(requestHeader: RequestHeader)(
          implicit ec: ExecutionContext): Future[Either[NaptimeActionException, Set[A]]] = {
        Future
          .traverse(controls) { control =>
            Futures.safelyCall(control.run(requestHeader))
          }
          .map { results =>
            val successes = results.collect {
              case Right(authentication) => authentication
            }
            lazy val firstErrorOption = results.collectFirst {
              case Left(error) => error
            }
            if (successes.nonEmpty) {
              Right(successes.toSet)
            } else {
              firstErrorOption match {
                case Some(error) => Left(error)
                case None        => badAccessControlsResponse
              }
            }
          }
      }

      override def check(authInfo: Set[A]): Either[NaptimeActionException, Set[A]] = {
        if (authInfo.nonEmpty) Right(authInfo) else badAccessControlsResponse
      }
    }
  }

}

object SuccessfulOf {

  private[combiner] val badAccessControlsResponse = {
    Left(
      NaptimeActionException(
        Status.UNAUTHORIZED,
        Some("auth.perms"),
        Some("Invalid access control configuration")))
  }

}
