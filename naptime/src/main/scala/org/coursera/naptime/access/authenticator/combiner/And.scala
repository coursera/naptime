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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

private[authenticator] trait And {

  def and[A, A1, A2](authenticator1: Authenticator[A1], authenticator2: Authenticator[A2])(
      partialCombine: PartialFunction[(Option[A1], Option[A2]), A]): Authenticator[A] = {
    new Authenticator[A] {

      override def maybeAuthenticate(requestHeader: RequestHeader)(
          implicit executionContext: ExecutionContext)
        : Future[Option[Either[NaptimeActionException, A]]] = {

        for {
          response1 <- Authenticator.authenticateAndRecover(authenticator1, requestHeader)
          response2 <- Authenticator.authenticateAndRecover(authenticator2, requestHeader)
        } yield {
          val combine = partialCombine.lift
          (response1, response2) match {
            case (Some(Left(error)), _) => Some(Left(error))
            case (_, Some(Left(error))) => Some(Left(error))
            case _ =>
              val a1Option = response1.flatMap(_.right.toOption)
              val a2Option = response2.flatMap(_.right.toOption)
              combine(a1Option, a2Option).map(Right.apply)
          }
        }
      }
    }
  }

}
