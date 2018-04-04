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

package org.coursera.naptime.access.authenticator

import com.typesafe.scalalogging.StrictLogging
import org.coursera.common.concurrent.Futures
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.access.authenticator.combiner.And
import org.coursera.naptime.access.authenticator.combiner.AnyOf
import org.coursera.naptime.access.authenticator.combiner.FirstOf
import play.api.http.Status.FORBIDDEN
import play.api.http.Status.UNAUTHORIZED
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * Authenticates request client based on data extracted from the request (see
 * [[HeaderAuthenticationParser]]) as well as looked up from other sources (see [[Decorator]]).
 *
 * @tparam A Any authentication information obtained from the request and other sources, like
 *           user id, user role, device, etc.
 */
trait Authenticator[+A] {

  /**
   * Attempt to authenticate the requester based on the request header. May return `Future[None]`
   * if authentication is skipped, or an error if authentication fails.
   */
  def maybeAuthenticate(requestHeader: RequestHeader)(
      implicit ec: ExecutionContext): Future[Option[Either[NaptimeActionException, A]]]

  def collect[B](f: PartialFunction[A, B]): Authenticator[B] = {
    val self = this
    new Authenticator[B] {
      override def maybeAuthenticate(requestHeader: RequestHeader)(
          implicit ec: ExecutionContext): Future[Option[Either[NaptimeActionException, B]]] = {

        Futures
          .safelyCall(self.maybeAuthenticate(requestHeader))
          .map(_.map(_.right.map(f.lift)))
          .map {
            case Some(Right(None))    => None
            case Some(Right(Some(b))) => Some(Right(b))
            case Some(Left(error))    => Some(Left(error))
            case None                 => None
          }
          .recover(Authenticator.errorRecovery)
      }
    }
  }

  def map[B](f: A => B): Authenticator[B] = collect(PartialFunction(f))

}

object Authenticator extends StrictLogging with AnyOf with FirstOf with And {

  def apply[P, A](
      parser: HeaderAuthenticationParser[P],
      decorator: Decorator[P, A]): Authenticator[A] = {

    new Authenticator[A] {
      def maybeAuthenticate(requestHeader: RequestHeader)(
          implicit ec: ExecutionContext): Future[Option[Either[NaptimeActionException, A]]] = {

        parser.parseHeader(requestHeader) match {
          case ParseResult.Success(parsed) =>
            Futures
              .safelyCall(decorator(parsed))
              .map { either =>
                either.left
                  .map { message =>
                    Some(Left(NaptimeActionException(FORBIDDEN, None, Some(message))))
                  }
                  .right
                  .map { decorated =>
                    Some(Right(decorated))
                  }
                  .merge
              }
              .recover(errorRecovery)
          case ParseResult.Error(message, status) =>
            Future.successful(
              Some(Left(NaptimeActionException(status, Some("auth.parse"), Some(message)))))
          case ParseResult.Skip => Future.successful(None)
        }
      }
    }

  }

  private[access] def authenticateAndRecover[A](
      authenticator: Authenticator[A],
      requestHeader: RequestHeader)(
      implicit ec: ExecutionContext): Future[Option[Either[NaptimeActionException, A]]] = {
    Futures
      .safelyCall(authenticator.maybeAuthenticate(requestHeader))
      .recover(errorRecovery)
  }

  def errorRecovery[A]: PartialFunction[Throwable, Option[Either[NaptimeActionException, A]]] = {
    case NonFatal(e) =>
      logger.error("Unexpected authentication error", e)
      val message = s"Unexpected authentication error: ${e.getMessage}"
      Some(Left(NaptimeActionException(UNAUTHORIZED, Some("auth.perms"), Some(message))))
  }

}
