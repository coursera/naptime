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

    Authenticator.authenticateAndRecover(authenticator, requestHeader).map { decoratedOption =>
      decoratedOption.map { either =>
        either.right.flatMap { decorated =>
          Authorizer.toResponse(authorizer, decorated)
        }
      }.getOrElse {
        StructuredAccessControl.missingResponse
      }
    }
  }

  override private[naptime] def check(authInfo: A): Either[NaptimeActionException, A] = {
    Authorizer.toResponse(authorizer, authInfo)
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
   * Implementation note: The [[Authenticator]] is supposed to generate the authentication data.
   * In this case, the resulting authentication data depends on the _authorizers_ in the input
   * access controls because it reflects which authorizers accepted. Thus we run the individual
   * authorizers in the combined access control's authenticator.
   */
  def anyOf[A, B](
      controlA: StructuredAccessControl[A],
      controlB: StructuredAccessControl[B]):
    StructuredAccessControl[(Option[A], Option[B])] = {

    type AA = (Option[A], Option[B])

    val authenticator: Authenticator[AA] = new Authenticator[AA] {
      def maybeAuthenticate(
          requestHeader: RequestHeader)
          (implicit ec: ExecutionContext): Future[Option[Either[NaptimeActionException, AA]]] = {
        for {
          maybeAuthenticationA <- Authenticator.authenticateAndRecover(
            controlA.authenticator, requestHeader)
          maybeAuthenticationB <- Authenticator.authenticateAndRecover(
            controlB.authenticator, requestHeader)
        } yield {
          val resultA = maybeAuthenticationA
            .map(_.right.flatMap(Authorizer.toResponse(controlA.authorizer, _)))
          val resultB = maybeAuthenticationB
            .map(_.right.flatMap(Authorizer.toResponse(controlB.authorizer, _)))

          (resultA, resultB) match {
            case (None, None) => None
            case (Some(Left(errorA)), Some(Left(_))) =>
              Some(Left(errorA))  // Ignore the other error.
            case _ =>
              Some(Right((resultA.flatMap(_.right.toOption), resultB.flatMap(_.right.toOption))))
          }
        }
      }
    }

    val authorizer: Authorizer[AA] = Authorizer(_ => AuthorizeResult.Authorized)

    StructuredAccessControl(authenticator, authorizer)
  }

}
