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

    authenticator.maybeAuthenticate(requestHeader).map { decoratedOption =>
      decoratedOption.map { either =>
        either.right.flatMap { decorated =>
          Authorizer.toResponse(authorizer.authorize(decorated), decorated)
        }
      }.getOrElse {
        StructuredAccessControl.missingResponse
      }
    }
  }
}

object StructuredAccessControl extends StructuredAccessControlCombinators {
  private[access] val missingResponse =
    Left(NaptimeActionException(Status.UNAUTHORIZED, Some("auth.perms"), Some("Missing authentication")))
}

trait StructuredAccessControlCombinators {
  /**
   * A left-biased combinator that allows the request if either the left or the right access
   * control allow it.
   *
   * Example usage:
   * {{{
   *   Nap.auth(
   *     StructuredAccessControl.either(Auth.superuser, Auth.admin)).get(ctx => ???)
   * }}}
   *
   * If there is only one parsing error, it is ignored. If there are two, the right one masks the
   * left one.
   */
  def either[A, B](
      left: StructuredAccessControl[A],
      right: StructuredAccessControl[B]): StructuredAccessControl[Either[A, B]] = {
    val authn = new Authenticator[Either[A, B]] {
      override def maybeAuthenticate(
          requestHeader: RequestHeader)
          (implicit ec: ExecutionContext): Future[Option[Either[NaptimeActionException, Either[A, B]]]] = {
        val lhsF = left.authenticator.maybeAuthenticate(requestHeader)
        val rhsF = right.authenticator.maybeAuthenticate(requestHeader)
        for {
          lhs <- lhsF
          rhs <- rhsF
        } yield {
          if (lhs.isEmpty && rhs.isEmpty) {
            Option(StructuredAccessControl.missingResponse)
          } else {
            /**
             * Takes the result of an authenticator, filters it through the corresponding authorizer, and
             * converts it to the appropriate Either type.
             */
            def filterResponse[T](
                authResult: Option[Either[NaptimeActionException, T]],
                authorizer: Authorizer[T])
                (fn: T => Either[A, B]): Either[NaptimeActionException, Either[A, B]] = {
              authResult.map { authEither =>
                authEither.right.flatMap { decorated =>
                  Authorizer.toResponse(authorizer.authorize(decorated), fn(decorated))
                }
              }.getOrElse {
                StructuredAccessControl.missingResponse
              }
            }
            val leftSide = filterResponse(lhs, left.authorizer)(Left.apply)
            Some(leftSide.left.flatMap { failure =>
              filterResponse(rhs, right.authorizer)(Right.apply)
            })
          }
        }
      }
    }
    StructuredAccessControl(
      authenticator = authn,
      authorizer = Authorizer(_ => AuthorizeResult.Authorized))
  }

  /**
   * Allows the request through iff both left and right allow it.
   *
   * Example usage:
   * {{{
   *   Nap.auth(
   *     StructuredAccessControl.and(Auth.superuser, Auth.admin)).get(ctx => ???)
   * }}}
   *
   * If there is only one parsing error, it is ignored. If there are two, the left one masks the
   * right one.
   */
  def and[A, B](
      left: StructuredAccessControl[A],
      right: StructuredAccessControl[B]): StructuredAccessControl[(A, B)] = {
    val authn = new Authenticator[(A, B)] {
      override def maybeAuthenticate(
        requestHeader: RequestHeader)
        (implicit ec: ExecutionContext):
      Future[Option[Either[NaptimeActionException, (A, B)]]] = {
        val lhsF = left.authenticator.maybeAuthenticate(requestHeader)
        val rhsF = right.authenticator.maybeAuthenticate(requestHeader)
        for {
          lhs <- lhsF
          rhs <- rhsF
        } yield {
          for {
            lh <- lhs
            rh <- rhs
          } yield {
            for {
              l <- lh.right
              r <- rh.right
            } yield {
              (l, r)
            }
          }
        }
      }
    }
    val authz = Authorizer.apply[(A, B)] { pair =>
      val lhs = left.authorizer.authorize(pair._1)
      val rhs = right.authorizer.authorize(pair._2)
      if (lhs.isAuthorized && rhs.isAuthorized) {
        AuthorizeResult.Authorized
      } else if (lhs.isRejected) {
        lhs
      } else if (rhs.isRejected) {
        rhs
      } else if (lhs.isFailed) {
        lhs
      } else if (rhs.isFailed) {
        rhs
      } else {
        lhs
      }
    }
    StructuredAccessControl(authn, authz)
  }

  /**
   * Allows the request through as long as at least one of Access Controllers would allow the request.
   *
   * The parse results are exposed to user code as a tuple containing options. At least one of the options
   * must be a [[Some]].
   */
  def anyOf[A, B](
      left: StructuredAccessControl[A],
      right: StructuredAccessControl[B]): StructuredAccessControl[(Option[A], Option[B])] = {
    val authn = new Authenticator[(Option[A], Option[B])] {
      override def maybeAuthenticate(
          requestHeader: RequestHeader)
          (implicit ec: ExecutionContext):
          Future[Option[Either[NaptimeActionException, (Option[A], Option[B])]]] = {
        val lhsF = left.authenticator.maybeAuthenticate(requestHeader)
        val rhsF = right.authenticator.maybeAuthenticate(requestHeader)
        for {
          lhs <- lhsF
          rhs <- rhsF
        } yield {
          if (lhs.isEmpty && rhs.isEmpty) {
            None
          } else {
            def filterResponse[T](
              authResult: Option[Either[NaptimeActionException, T]],
              authorizer: Authorizer[T]): Either[NaptimeActionException, T] = {
              authResult.map { authEither =>
                authEither.right.flatMap { decorated =>
                  Authorizer.toResponse(authorizer.authorize(decorated), decorated)
                }
              }.getOrElse(StructuredAccessControl.missingResponse)
            }
            val lh = filterResponse(lhs, left.authorizer)
            val rh = filterResponse(rhs, right.authorizer)
            if (lh.isLeft && rh.isLeft) {
              // Neither of them work, so serve an error response
              Some(lh.asInstanceOf[Left[NaptimeActionException, Nothing]])
            } else {
              // At least one of them is good, so carry on.
              val l = lh.right.toOption
              val r = rh.right.toOption
              Some(Right(l, r))
            }
          }
        }
      }
    }
    StructuredAccessControl(authn, Authorizer(_ => AuthorizeResult.Authorized))
  }

  def anyOf[A, B, C](
      first: StructuredAccessControl[A],
      second: StructuredAccessControl[B],
      third: StructuredAccessControl[C]): StructuredAccessControl[(Option[A], Option[B], Option[C])] = {
    val authn = new Authenticator[(Option[A], Option[B], Option[C])] {
      override def maybeAuthenticate(
          requestHeader: RequestHeader)
          (implicit ec: ExecutionContext):
          Future[Option[Either[NaptimeActionException, (Option[A], Option[B], Option[C])]]] = {
        val firstF = first.authenticator.maybeAuthenticate(requestHeader)
        val secondF = second.authenticator.maybeAuthenticate(requestHeader)
        val thirdF = third.authenticator.maybeAuthenticate(requestHeader)
        for {
          oneOptEither <- firstF
          twoOptEither <- secondF
          threeOptEither <- thirdF
        } yield {
          if (oneOptEither.isEmpty && twoOptEither.isEmpty && threeOptEither.isEmpty) {
            None
          } else {
            def filterResponse[T](
              authResult: Option[Either[NaptimeActionException, T]],
              authorizer: Authorizer[T]): Either[NaptimeActionException, T] = {
              authResult.map { authEither =>
                authEither.right.flatMap { decorated =>
                  Authorizer.toResponse(authorizer.authorize(decorated), decorated)
                }
              }.getOrElse(StructuredAccessControl.missingResponse)
            }
            val oneEither = filterResponse(oneOptEither, first.authorizer)
            val twoEither = filterResponse(twoOptEither, second.authorizer)
            val thirdEither = filterResponse(threeOptEither, third.authorizer)
            if (oneEither.isLeft && twoEither.isLeft && thirdEither.isLeft) {
              Some(oneEither.asInstanceOf[Left[NaptimeActionException, Nothing]])
            } else {
              // At least one is acceptable.
              val one = oneEither.right.toOption
              val two = twoEither.right.toOption
              val three = thirdEither.right.toOption
              Some(Right(one, two, three))
            }
          }
        }
      }
    }
    StructuredAccessControl(authn, Authorizer(_ => AuthorizeResult.Authorized))
  }
}
