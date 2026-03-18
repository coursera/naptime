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

package org.coursera.naptime.access.authenticator

import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.ResourceTestImplicits
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest

import scala.concurrent.Future

/**
 * Tests for [[Authenticator]] combinators.
 *
 * The `collect` / `map` methods use `PartialFunction.lift`, and `errorRecovery`
 * returns a `PartialFunction[Throwable, ...]`.  The Scala 2.13 migration
 * replaced deprecated `PartialFunction(f)` constructor usage elsewhere in this
 * package — these tests confirm the remaining PartialFunction-based logic works
 * correctly in Scala 2.13.
 */
class AuthenticatorTest extends AssertionsForJUnit with ScalaFutures with ResourceTestImplicits {

  private val fakeRequest: RequestHeader = FakeRequest()

  /** An authenticator that always succeeds with the given value. */
  private def alwaysAuth[A](value: A): Authenticator[A] =
    new Authenticator[A] {
      override def maybeAuthenticate(rh: RequestHeader)(
          implicit ec: scala.concurrent.ExecutionContext)
          : Future[Option[Either[NaptimeActionException, A]]] =
        Future.successful(Some(Right(value)))
    }

  /** An authenticator that always skips (returns None). */
  private def alwaysSkip[A]: Authenticator[A] =
    new Authenticator[A] {
      override def maybeAuthenticate(rh: RequestHeader)(
          implicit ec: scala.concurrent.ExecutionContext)
          : Future[Option[Either[NaptimeActionException, A]]] =
        Future.successful(None)
    }

  /** An authenticator that always fails with the given exception. */
  private def alwaysFail[A](ex: NaptimeActionException): Authenticator[A] =
    new Authenticator[A] {
      override def maybeAuthenticate(rh: RequestHeader)(
          implicit ec: scala.concurrent.ExecutionContext)
          : Future[Option[Either[NaptimeActionException, A]]] =
        Future.successful(Some(Left(ex)))
    }

  // ─── Authenticator.map ───────────────────────────────────────────────────────

  @Test
  def map_transformsSuccessValue(): Unit = {
    val auth = alwaysAuth(42).map(_ * 2)
    val result = auth.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right(84)))(result)
  }

  @Test
  def map_onSkip_returnsNone(): Unit = {
    val auth = alwaysSkip[Int].map(_ * 2)
    val result = auth.maybeAuthenticate(fakeRequest).futureValue
    assertResult(None)(result)
  }

  @Test
  def map_onFailure_propagatesError(): Unit = {
    val ex = NaptimeActionException(401, Some("auth.test"), Some("test error"), None)
    val auth = alwaysFail[Int](ex).map(_ * 2)
    val result = auth.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Left(ex)))(result)
  }

  // ─── Authenticator.collect ───────────────────────────────────────────────────

  @Test
  def collect_matchingCase_transformsValue(): Unit = {
    val auth = alwaysAuth(Some(42): Option[Int]).collect { case Some(n) => n + 1 }
    val result = auth.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Right(43)))(result)
  }

  @Test
  def collect_nonMatchingCase_returnsNone(): Unit = {
    // When the partial function does not match, collect should return None (skip)
    val auth = alwaysAuth(None: Option[Int]).collect { case Some(n) => n }
    val result = auth.maybeAuthenticate(fakeRequest).futureValue
    assertResult(None)(result)
  }

  @Test
  def collect_onSkip_returnsNone(): Unit = {
    val auth = alwaysSkip[Option[Int]].collect { case Some(n) => n }
    val result = auth.maybeAuthenticate(fakeRequest).futureValue
    assertResult(None)(result)
  }

  @Test
  def collect_onFailure_propagatesError(): Unit = {
    val ex = NaptimeActionException(403, Some("auth.forbidden"), Some("forbidden"), None)
    val auth = alwaysFail[Option[Int]](ex).collect { case Some(n) => n }
    val result = auth.maybeAuthenticate(fakeRequest).futureValue
    assertResult(Some(Left(ex)))(result)
  }

  // ─── Authenticator.errorRecovery ─────────────────────────────────────────────

  @Test
  def errorRecovery_nonFatalException_returnsUnauthorized(): Unit = {
    val pf = Authenticator.errorRecovery[String]
    val ex = new RuntimeException("boom")
    val result: Option[Either[NaptimeActionException, String]] = pf(ex)
    result match {
      case Some(Left(nae)) =>
        assertResult(401)(nae.httpCode)
        assert(nae.message.exists(_.contains("boom")))
      case other => fail(s"Expected Some(Left(NaptimeActionException)) but got $other")
    }
  }

  @Test
  def errorRecovery_isDefinedForNonFatal(): Unit = {
    val pf = Authenticator.errorRecovery[Int]
    assert(pf.isDefinedAt(new RuntimeException("test")))
    assert(pf.isDefinedAt(new IllegalArgumentException("test")))
  }

  @Test
  def errorRecovery_isNotDefinedForFatal(): Unit = {
    val pf = Authenticator.errorRecovery[Int]
    assert(!pf.isDefinedAt(new StackOverflowError()))
  }
}
