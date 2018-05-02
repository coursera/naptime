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

package org.coursera.naptime.access.authorizer

import org.coursera.naptime.NaptimeActionException
import play.api.http.Status.FORBIDDEN
import play.api.http.Status.INTERNAL_SERVER_ERROR

/**
 * @tparam A Authentication data (from a parser and optionally a
 *           [[org.coursera.naptime.access.authenticator.Decorator]].
 */
trait Authorizer[-A] {

  def authorize(authentication: A): AuthorizeResult

  def check(authentication: A): Unit = {
    Authorizer.toResponse(authorize(authentication), ()).left.foreach(throw _)
  }

  /**
   * Construct an `Authorizer` for new type [[AA]], given a function to compute [[A]] from [[AA]].
   */
  def on[AA](f: AA => A): Authorizer[AA] =
    Authorizer[AA](aa => authorize(f(aa)))

}

object Authorizer {

  def apply[A](f: A => AuthorizeResult): Authorizer[A] = new Authorizer[A] {
    override def authorize(authentication: A): AuthorizeResult =
      f(authentication)
  }

  def toResponse[T](result: AuthorizeResult, rawResponse: T): Either[NaptimeActionException, T] = {
    result match {
      case AuthorizeResult.Authorized => Right(rawResponse)
      case AuthorizeResult.Rejected(message, details) =>
        Left(NaptimeActionException(FORBIDDEN, Some("auth.perms"), Some(message), details))
      case AuthorizeResult.Failed(message, details) =>
        Left(
          NaptimeActionException(INTERNAL_SERVER_ERROR, Some("auth.perms"), Some(message), details))
    }
  }

  /**
   * Combines an UNORDERED collection of [[Authorizer]]s into an authorizer that:
   *   1. If any authorizer succeeds, return authorized.
   *   2. If any rejects, return rejected.
   *   3. Otherwise, return an arbitrary error.
   *
   * TODO(josh): Should existence of even a single error trigger failure?
   * TODO(josh): Write unit tests.
   */
  def anyOf[A](authorizers: Set[Authorizer[A]]): Authorizer[A] = apply { authentication: A =>
    val authorizeResults = authorizers.map(_.authorize(authentication))

    lazy val authorizedOption = authorizeResults.find(_.isAuthorized)
    lazy val rejectedOption = authorizeResults.find(_.isRejected)
    lazy val failedOption = authorizeResults.find(_.isFailed)

    (authorizedOption, rejectedOption, failedOption) match {
      case (Some(authorized), _, _) => authorized
      case (_, Some(rejected), _)   => rejected
      case (_, _, Some(failed))     => failed
      case _                        => AuthorizeResult.Failed("Invalid authorization configuration")
    }
  }

  /**
   * Combines an UNORDERED collection of [[Authorizer]]s into an authorizer that:
   *   1. If any authorizer rejects, return rejected.
   *   2. If any authorizer fails, return failed.
   *   3. If all accept, return accepted.
   *
   * TODO(josh): Unit tests.
   */
  def and[A](authorizers: Set[Authorizer[A]]): Authorizer[A] = apply { authentication: A =>
    val authorizeResults = authorizers.map(_.authorize(authentication))

    lazy val rejectedOption = authorizeResults.find(_.isRejected)
    lazy val failedOption = authorizeResults.find(_.isFailed)
    lazy val areAllAuthorized = authorizeResults.forall(_.isAuthorized)

    (rejectedOption, failedOption, areAllAuthorized) match {
      case (Some(rejected), _, _) => rejected
      case (_, Some(failed), _)   => failed
      case (None, None, true)     => AuthorizeResult.Authorized
      case _                      => AuthorizeResult.Failed("Invalid authorization configuration")
    }
  }

}
