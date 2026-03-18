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

package org.coursera.naptime.access.authorizer

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.http.Status

/**
 * Tests for [[Authorizer]], [[Authorizer.anyOf]], and [[Authorizer.and]].
 */
class AuthorizerTest extends AssertionsForJUnit {

  private val allowAll: Authorizer[String] = Authorizer(_ => AuthorizeResult.Authorized)
  private val denyAll: Authorizer[String] = Authorizer(_ => AuthorizeResult.Rejected("denied"))
  private val failAll: Authorizer[String] = Authorizer(_ => AuthorizeResult.Failed("failed"))

  // ─── AuthorizeResult predicates ────────────────────────────────────────────

  @Test
  def authorized_isAuthorized_true(): Unit = {
    assert(AuthorizeResult.Authorized.isAuthorized)
  }

  @Test
  def rejected_isAuthorized_false(): Unit = {
    assert(!AuthorizeResult.Rejected("no").isAuthorized)
  }

  @Test
  def authorized_isRejected_false(): Unit = {
    assert(!AuthorizeResult.Authorized.isRejected)
  }

  @Test
  def rejected_isRejected_true(): Unit = {
    assert(AuthorizeResult.Rejected("no").isRejected)
  }

  @Test
  def authorized_isFailed_false(): Unit = {
    assert(!AuthorizeResult.Authorized.isFailed)
  }

  @Test
  def failed_isFailed_true(): Unit = {
    assert(AuthorizeResult.Failed("boom").isFailed)
  }

  // ─── Authorizer.apply + authorize ──────────────────────────────────────────

  @Test
  def apply_authorized(): Unit = {
    assertResult(AuthorizeResult.Authorized)(allowAll.authorize("test"))
  }

  @Test
  def apply_rejected(): Unit = {
    assertResult(AuthorizeResult.Rejected("denied"))(denyAll.authorize("test"))
  }

  @Test
  def apply_failed(): Unit = {
    assertResult(AuthorizeResult.Failed("failed"))(failAll.authorize("test"))
  }

  // ─── Authorizer.on ─────────────────────────────────────────────────────────

  @Test
  def on_transformsInputBeforeAuthorizing(): Unit = {
    val lengthAuthorizer: Authorizer[Int] = Authorizer(
      n => if (n > 0) AuthorizeResult.Authorized else AuthorizeResult.Rejected("empty"))
    val strAuthorizer = lengthAuthorizer.on[String](_.length)
    assertResult(AuthorizeResult.Authorized)(strAuthorizer.authorize("hello"))
    assertResult(AuthorizeResult.Rejected("empty"))(strAuthorizer.authorize(""))
  }

  // ─── Authorizer.toResponse ─────────────────────────────────────────────────

  @Test
  def toResponse_authorized_returnsRight(): Unit = {
    assertResult(Right("data"))(Authorizer.toResponse(AuthorizeResult.Authorized, "data"))
  }

  @Test
  def toResponse_rejected_returnsForbidden(): Unit = {
    val result = Authorizer.toResponse(AuthorizeResult.Rejected("no"), "data")
    result match {
      case Left(ex) => assertResult(Status.FORBIDDEN)(ex.httpCode)
      case Right(_) => fail("Expected Left")
    }
  }

  @Test
  def toResponse_failed_returnsInternalServerError(): Unit = {
    val result = Authorizer.toResponse(AuthorizeResult.Failed("boom"), "data")
    result match {
      case Left(ex) => assertResult(Status.INTERNAL_SERVER_ERROR)(ex.httpCode)
      case Right(_) => fail("Expected Left")
    }
  }

  // ─── Authorizer.check ──────────────────────────────────────────────────────

  @Test
  def check_authorized_doesNotThrow(): Unit = {
    allowAll.check("anything") // should not throw
  }

  @Test
  def check_rejected_throwsNaptimeActionException(): Unit = {
    intercept[org.coursera.naptime.NaptimeActionException] {
      denyAll.check("anything")
    }
  }

  // ─── Authorizer.anyOf ──────────────────────────────────────────────────────

  @Test
  def anyOf_oneAuthorized_returnsAuthorized(): Unit = {
    val combined = Authorizer.anyOf(Set(allowAll, denyAll))
    assertResult(AuthorizeResult.Authorized)(combined.authorize("test"))
  }

  @Test
  def anyOf_allRejected_returnsRejected(): Unit = {
    val combined = Authorizer.anyOf(Set(denyAll, denyAll))
    assert(combined.authorize("test").isRejected)
  }

  @Test
  def anyOf_allFailed_returnsFailed(): Unit = {
    val combined = Authorizer.anyOf(Set(failAll, failAll))
    assert(combined.authorize("test").isFailed)
  }

  @Test
  def anyOf_oneRejectedOneFailed_returnsRejected(): Unit = {
    val combined = Authorizer.anyOf(Set(denyAll, failAll))
    // rejected takes precedence over failed
    assert(combined.authorize("test").isRejected)
  }

  @Test
  def anyOf_empty_returnsFailed(): Unit = {
    val combined = Authorizer.anyOf(Set.empty[Authorizer[String]])
    assert(combined.authorize("test").isFailed)
  }

  // ─── Authorizer.and ────────────────────────────────────────────────────────

  @Test
  def and_allAuthorized_returnsAuthorized(): Unit = {
    val combined = Authorizer.and(Set(allowAll, allowAll))
    assertResult(AuthorizeResult.Authorized)(combined.authorize("test"))
  }

  @Test
  def and_oneRejected_returnsRejected(): Unit = {
    val combined = Authorizer.and(Set(allowAll, denyAll))
    assert(combined.authorize("test").isRejected)
  }

  @Test
  def and_oneFailed_returnsFailed(): Unit = {
    val combined = Authorizer.and(Set(allowAll, failAll))
    assert(combined.authorize("test").isFailed)
  }

  @Test
  def and_rejectedAndFailed_returnsRejected(): Unit = {
    val combined = Authorizer.and(Set(denyAll, failAll))
    assert(combined.authorize("test").isRejected)
  }

  @Test
  def and_empty_returnsFailed(): Unit = {
    // With an empty set, areAllAuthorized = true (vacuously), so it returns Authorized
    val combined = Authorizer.and(Set.empty[Authorizer[String]])
    assertResult(AuthorizeResult.Authorized)(combined.authorize("test"))
  }
}
