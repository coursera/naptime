/*
 * Copyright 2024 Coursera Inc.
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
import org.coursera.naptime.ResourceTestImplicits
import org.coursera.naptime.access.authenticator.Authenticator
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest

import scala.concurrent.Future

/**
 * Tests for [[And]], [[AnyOf]], and [[FirstOf]] authenticator combiners.
 *
 * [[Authenticator]] extends all three traits, so all combiners are accessible
 * via `Authenticator.and(...)`, `Authenticator.anyOf(...)`, `Authenticator.firstOf(...)`.
 */
class AuthenticatorCombinersTest
    extends AssertionsForJUnit
    with ScalaFutures
    with ResourceTestImplicits {

  private val fakeRequest: RequestHeader = FakeRequest()

  // ─── Helpers ───────────────────────────────────────────────────────────────

  private def alwaysAuth[A](value: A): Authenticator[A] =
    new Authenticator[A] {
      override def maybeAuthenticate(rh: RequestHeader)(
          implicit ec: scala.concurrent.ExecutionContext)
        : Future[Option[Either[NaptimeActionException, A]]] =
        Future.successful(Some(Right(value)))
    }

  private def alwaysSkip[A]: Authenticator[A] =
    new Authenticator[A] {
      override def maybeAuthenticate(rh: RequestHeader)(
          implicit ec: scala.concurrent.ExecutionContext)
        : Future[Option[Either[NaptimeActionException, A]]] =
        Future.successful(None)
    }

  private def alwaysFail[A](ex: NaptimeActionException): Authenticator[A] =
    new Authenticator[A] {
      override def maybeAuthenticate(rh: RequestHeader)(
          implicit ec: scala.concurrent.ExecutionContext)
        : Future[Option[Either[NaptimeActionException, A]]] =
        Future.successful(Some(Left(ex)))
    }

  private val error401 = NaptimeActionException(401, Some("auth.test"), Some("unauthorized"), None)
  private val error403 = NaptimeActionException(403, Some("auth.test"), Some("forbidden"), None)

  // ─── And combiner ──────────────────────────────────────────────────────────

  @Test
  def and_bothAuthenticate_combinesViaPartialFunction(): Unit = {
    val combined = Authenticator.and(alwaysAuth("alice"), alwaysAuth("bob")) {
      case (Some(a), Some(b)) => s"$a+$b"
    }
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right("alice+bob")))(result)
  }

  @Test
  def and_partialFunctionNotDefined_returnsNone(): Unit = {
    // partialCombine doesn't match (None, None) so combined result is None
    val combined = Authenticator.and(alwaysSkip[String], alwaysSkip[String]) {
      case (Some(a), Some(b)) => s"$a+$b"
    }
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(None)(result)
  }

  @Test
  def and_firstAuthFails_returnsError(): Unit = {
    val combined = Authenticator.and(alwaysFail[String](error401), alwaysAuth("bob")) {
      case (Some(a), Some(b)) => s"$a+$b"
    }
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Left(error401)))(result)
  }

  @Test
  def and_secondAuthFails_returnsError(): Unit = {
    val combined = Authenticator.and(alwaysAuth("alice"), alwaysFail[String](error403)) {
      case (Some(a), Some(b)) => s"$a+$b"
    }
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Left(error403)))(result)
  }

  @Test
  def and_firstSkipsSecondAuthenticated_combinedWithNoneA(): Unit = {
    val combined = Authenticator.and(alwaysSkip[String], alwaysAuth("bob")) {
      case (None, Some(b)) => s"_+$b"
    }
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right("_+bob")))(result)
  }

  // ─── AnyOf combiner ────────────────────────────────────────────────────────

  @Test
  def anyOf_set_oneSucceeds_returnsSuccess(): Unit = {
    val combined = Authenticator.anyOf(Set(alwaysAuth("alice"), alwaysSkip[String]))
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    result match {
      case Some(Right(v)) => assertResult("alice")(v)
      case other          => fail(s"Expected Some(Right(alice)) but got $other")
    }
  }

  @Test
  def anyOf_set_allSkip_returnsNone(): Unit = {
    val combined = Authenticator.anyOf(Set(alwaysSkip[String], alwaysSkip[String]))
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(None)(result)
  }

  @Test
  def anyOf_set_allFail_returnsError(): Unit = {
    val combined =
      Authenticator.anyOf(Set(alwaysFail[String](error401), alwaysFail[String](error403)))
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    result match {
      case Some(Left(_)) => // expected
      case other         => fail(s"Expected Some(Left(...)) but got $other")
    }
  }

  @Test
  def anyOf_set_empty_returnsNone(): Unit = {
    val combined = Authenticator.anyOf(Set.empty[Authenticator[String]])
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(None)(result)
  }

  @Test
  def anyOf_twoArg_bothAuthenticate_combinedViaTransformer(): Unit = {
    implicit val t1: AuthenticationTransformer[String, String] =
      AuthenticationTransformer.function(identity)
    implicit val t2: AuthenticationTransformer[Int, String] =
      AuthenticationTransformer.function(_.toString)

    val combined = Authenticator.anyOf(alwaysAuth("alice"), alwaysAuth(42))
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    result match {
      case Some(Right(_)) => // expected — one of the two transformers wins
      case other          => fail(s"Expected Some(Right(...)) but got $other")
    }
  }

  @Test
  def anyOf_twoArg_firstAuthenticates_secondSkips(): Unit = {
    implicit val t1: AuthenticationTransformer[String, String] =
      AuthenticationTransformer.function(identity)
    implicit val t2: AuthenticationTransformer[Int, String] =
      AuthenticationTransformer.function(_.toString)

    val combined = Authenticator.anyOf(alwaysAuth("alice"), alwaysSkip[Int])
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right("alice")))(result)
  }

  @Test
  def anyOf_twoArg_bothSkip_returnsNone(): Unit = {
    implicit val t1: AuthenticationTransformer[String, String] =
      AuthenticationTransformer.function(identity)
    implicit val t2: AuthenticationTransformer[Int, String] =
      AuthenticationTransformer.function(_.toString)

    val combined = Authenticator.anyOf(alwaysSkip[String], alwaysSkip[Int])
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(None)(result)
  }

  @Test
  def anyOf_threeArg_firstSucceeds(): Unit = {
    implicit val t1: AuthenticationTransformer[String, String] =
      AuthenticationTransformer.function(identity)
    implicit val t2: AuthenticationTransformer[Int, String] =
      AuthenticationTransformer.function(_.toString)
    implicit val t3: AuthenticationTransformer[Boolean, String] =
      AuthenticationTransformer.function(_.toString)

    val combined = Authenticator.anyOf(alwaysAuth("alice"), alwaysSkip[Int], alwaysSkip[Boolean])
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right("alice")))(result)
  }

  // ─── FirstOf combiner ──────────────────────────────────────────────────────

  @Test
  def firstOf_list_firstSucceeds_returnsFirstSuccess(): Unit = {
    val combined = Authenticator.firstOf(List(alwaysAuth("alice"), alwaysAuth("bob")))
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right("alice")))(result)
  }

  @Test
  def firstOf_list_firstSkipsSecondSucceeds_returnsSecond(): Unit = {
    val combined = Authenticator.firstOf(List(alwaysSkip[String], alwaysAuth("bob")))
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right("bob")))(result)
  }

  @Test
  def firstOf_list_allSkip_returnsNone(): Unit = {
    val combined = Authenticator.firstOf(List(alwaysSkip[String], alwaysSkip[String]))
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(None)(result)
  }

  @Test
  def firstOf_list_empty_returnsNone(): Unit = {
    val combined = Authenticator.firstOf(List.empty[Authenticator[String]])
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(None)(result)
  }

  @Test
  def firstOf_list_firstFailsSecondSucceeds_returnsSecondSuccess(): Unit = {
    // Success wins: if ANY authenticator succeeds, firstOf returns the first success,
    // ignoring earlier errors.
    val combined = Authenticator.firstOf(List(alwaysFail[String](error401), alwaysAuth("bob")))
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right("bob")))(result)
  }

  @Test
  def firstOf_list_firstFailsSecondSkips_returnsError(): Unit = {
    // No success anywhere; falls back to the first error.
    val combined = Authenticator.firstOf(List(alwaysFail[String](error401), alwaysSkip[String]))
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Left(error401)))(result)
  }

  @Test
  def firstOf_twoArg_firstSucceeds(): Unit = {
    implicit val t1: AuthenticationTransformer[String, String] =
      AuthenticationTransformer.function(identity)
    implicit val t2: AuthenticationTransformer[Int, String] =
      AuthenticationTransformer.function(_.toString)

    val combined = Authenticator.firstOf(alwaysAuth("alice"), alwaysAuth(42))
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right("alice")))(result)
  }

  @Test
  def firstOf_twoArg_firstSkipsSecondSucceeds(): Unit = {
    implicit val t1: AuthenticationTransformer[String, String] =
      AuthenticationTransformer.function(identity)
    implicit val t2: AuthenticationTransformer[Int, String] =
      AuthenticationTransformer.function(_.toString)

    val combined = Authenticator.firstOf(alwaysSkip[String], alwaysAuth(42))
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right("42")))(result)
  }

  @Test
  def firstOf_threeArg_firstSkipsSecondSkipsThirdSucceeds(): Unit = {
    implicit val t1: AuthenticationTransformer[String, String] =
      AuthenticationTransformer.function(identity)
    implicit val t2: AuthenticationTransformer[Int, String] =
      AuthenticationTransformer.function(_.toString)
    implicit val t3: AuthenticationTransformer[Boolean, String] =
      AuthenticationTransformer.function(_.toString)

    val combined = Authenticator.firstOf(alwaysSkip[String], alwaysSkip[Int], alwaysAuth(true))
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right("true")))(result)
  }

  @Test
  def firstOf_fourArg_allSkip_returnsNone(): Unit = {
    implicit val t1: AuthenticationTransformer[String, String] =
      AuthenticationTransformer.function(identity)
    implicit val t2: AuthenticationTransformer[Int, String] =
      AuthenticationTransformer.function(_.toString)
    implicit val t3: AuthenticationTransformer[Boolean, String] =
      AuthenticationTransformer.function(_.toString)
    implicit val t4: AuthenticationTransformer[Double, String] =
      AuthenticationTransformer.function(_.toString)

    val combined = Authenticator.firstOf(
      alwaysSkip[String],
      alwaysSkip[Int],
      alwaysSkip[Boolean],
      alwaysSkip[Double])
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(None)(result)
  }

  @Test
  def firstOf_fiveArg_thirdSucceeds(): Unit = {
    implicit val t1: AuthenticationTransformer[String, String] =
      AuthenticationTransformer.function(identity)
    implicit val t2: AuthenticationTransformer[Int, String] =
      AuthenticationTransformer.function(_.toString)
    implicit val t3: AuthenticationTransformer[Boolean, String] =
      AuthenticationTransformer.function(_.toString)
    implicit val t4: AuthenticationTransformer[Double, String] =
      AuthenticationTransformer.function(_.toString)
    implicit val t5: AuthenticationTransformer[Long, String] =
      AuthenticationTransformer.function(_.toString)

    val combined = Authenticator.firstOf(
      alwaysSkip[String],
      alwaysSkip[Int],
      alwaysAuth(true),
      alwaysSkip[Double],
      alwaysSkip[Long])
    val result = combined.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right("true")))(result)
  }
}
