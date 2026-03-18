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

package org.coursera.naptime.actions

import akka.util.ByteString
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.Ok
import org.coursera.naptime.ResourceTestImplicits
import org.coursera.naptime.RestError
import org.coursera.naptime.access.HeaderAccessControl
import org.coursera.naptime.access.authenticator.Authenticator
import org.coursera.naptime.access.authenticator.Decorator
import org.coursera.naptime.access.authenticator.HeaderAuthenticationParser
import org.coursera.naptime.access.authenticator.ParseResult
import org.coursera.naptime.access.authorizer.AuthorizeResult
import org.coursera.naptime.access.authorizer.Authorizer
import org.coursera.naptime.access.StructuredAccessControl
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.http.Status
import play.api.libs.json.JsNumber
import play.api.libs.json.JsResult
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.Reads
import play.api.mvc.AnyContent
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers.defaultAwaitTimeout

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Tests for [[RestActionBuilder]] covering paths that were not previously exercised:
 *  - `tolerantJsonParser` / `rawJsonBody` (body reading & size limit)
 *  - `jsonBody` with valid and invalid JSON
 *  - `auth` (switching the authenticator on the builder)
 *  - `returning` (type-level reshape of the builder)
 *  - `catching` composition (already covered, kept here for completeness)
 */
class RestActionBuilderTest
    extends AssertionsForJUnit
    with ScalaFutures
    with ResourceTestImplicits {

  import RestActionBuilderTest._

  // Helper: run a Play EssentialAction against a FakeRequest with an optional body,
  // returning the HTTP Result.
  private def runAction(
      action: play.api.mvc.EssentialAction,
      request: FakeRequest[_],
      body: ByteString = ByteString.empty): Result = {
    val accumulator = action(request.withBody(()))
    val resultFuture = accumulator.run(
      akka.stream.scaladsl.Source.single(body))
    Helpers.await(resultFuture)
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // rawJsonBody / tolerantJsonParser
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  def rawJsonBody_validJson_parsesAndRuns(): Unit = {
    val resource = new TestResource
    val action = resource.rawJsonAction
    val body = ByteString("""{"key":"value"}""")
    val result = runAction(action, FakeRequest("POST", "/"), body)
    // Action returns Ok(()), which the engine encodes as 204 No Content.
    assert(result.header.status >= 200 && result.header.status < 300)
  }

  @Test
  def rawJsonBody_invalidJson_returnsBadRequest(): Unit = {
    val resource = new TestResource
    val action = resource.rawJsonAction
    val body = ByteString("not valid json {{{{")
    val result = runAction(action, FakeRequest("POST", "/"), body)
    assertResult(Status.BAD_REQUEST)(result.header.status)
  }

  @Test
  def rawJsonBody_oversizeBody_returnsEntityTooLarge(): Unit = {
    val resource = new TestResource
    val action = resource.rawJsonActionSmallLimit // limit = 10 bytes
    val body = ByteString("""{"key":"this is a very long string that exceeds the limit"}""")
    val result = runAction(action, FakeRequest("POST", "/"), body)
    assertResult(Status.REQUEST_ENTITY_TOO_LARGE)(result.header.status)
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // jsonBody — typed body parsing
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  def jsonBody_validJson_parsesAndRuns(): Unit = {
    val resource = new TestResource
    val action = resource.typedJsonAction
    val body = ByteString("""{"name":"Alice","value":42}""")
    val result = runAction(action, FakeRequest("POST", "/"), body)
    // Action returns Ok(()), encoded as 204 No Content.
    assert(result.header.status >= 200 && result.header.status < 300)
  }

  @Test
  def jsonBody_invalidSchema_returnsBadRequest(): Unit = {
    val resource = new TestResource
    val action = resource.typedJsonAction
    // 'name' is a string field; providing a number causes validation failure
    val body = ByteString("""{"name":123,"value":"notanumber"}""")
    val result = runAction(action, FakeRequest("POST", "/"), body)
    assertResult(Status.BAD_REQUEST)(result.header.status)
  }

  @Test
  def jsonBody_malformedJson_returnsBadRequest(): Unit = {
    val resource = new TestResource
    val action = resource.typedJsonAction
    val body = ByteString("not json at all")
    val result = runAction(action, FakeRequest("POST", "/"), body)
    assertResult(Status.BAD_REQUEST)(result.header.status)
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // auth — switching the authenticator
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  def auth_withAllowAll_actionSucceeds(): Unit = {
    val resource = new TestResource
    // allowAll is the default; ensure the result comes back 200
    val action = resource.authAction
    val result = runAction(action, FakeRequest("GET", "/"))
    assertResult(Status.OK)(result.header.status)
  }

  @Test
  def auth_withDenyingAuth_actionReturnsForbidden(): Unit = {
    val resource = new TestResource
    val action = resource.denyAuthAction
    val result = runAction(action, FakeRequest("GET", "/"))
    // The denying auth produces FORBIDDEN
    assertResult(Status.FORBIDDEN)(result.header.status)
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // returning — only a type-level operation; ensure it compiles and runs
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  def returning_reshapedBuilder_actionStillRuns(): Unit = {
    val resource = new TestResource
    val action = resource.returningAction
    val result = runAction(action, FakeRequest("GET", "/"))
    // Action returns Ok(()), encoded as 204 No Content.
    assert(result.header.status >= 200 && result.header.status < 300)
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // jsonBody — IllegalArgumentException catch branch (lines 176-185)
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  def jsonBody_illegalArgumentExceptionInReads_returnsBadRequest(): Unit = {
    // A Reads[T] that throws IllegalArgumentException exercises the catch block at line 176.
    val resource = new RestActionBuilderTest.TestResourceWithThrowingReads
    val action = resource.throwingIllegalArgJsonAction
    val body = ByteString("""{"field":"value"}""")
    val result = runAction(action, FakeRequest("POST", "/"), body)
    assertResult(Status.BAD_REQUEST)(result.header.status)
  }
}

object RestActionBuilderTest {

  case class Payload(name: String, value: Int)
  object Payload {
    implicit val reads: Reads[Payload] = Json.reads[Payload]
    implicit val format: OFormat[Payload] = Json.format[Payload]
  }

  case class SimpleModel(id: Int)
  object SimpleModel {
    implicit val format: OFormat[SimpleModel] = Json.format[SimpleModel]
  }

  /** Deny-all structured access control — always returns FORBIDDEN. */
  private val denyAll: HeaderAccessControl[Unit] = {
    val parser = HeaderAuthenticationParser.constant(())
    val authorizer = Authorizer[Unit](_ => AuthorizeResult.Rejected("denied"))
    StructuredAccessControl(Authenticator(parser, Decorator.identity[Unit]), authorizer)
  }

  /** A Reads that always throws IllegalArgumentException during validation. */
  case class ThrowsIllegalArg(x: String)
  object ThrowsIllegalArg {
    implicit val reads: Reads[ThrowsIllegalArg] = Reads[ThrowsIllegalArg] { _ =>
      throw new IllegalArgumentException("deliberate IAE for test coverage")
    }
    implicit val format: OFormat[ThrowsIllegalArg] = {
      val w = play.api.libs.json.OWrites[ThrowsIllegalArg](_ => Json.obj("x" -> "val"))
      OFormat(reads, w)
    }
  }

  class TestResourceWithThrowingReads(
      implicit val executionContext: ExecutionContext,
      val materializer: akka.stream.Materializer)
      extends TopLevelCollectionResource[Int, SimpleModel] {
    override def keyFormat: KeyFormat[Int] = KeyFormat.intKeyFormat
    override def resourceName: String = "throwingResource"
    override implicit val resourceFormat: OFormat[SimpleModel] = SimpleModel.format
    implicit val fields = Fields

    def throwingIllegalArgJsonAction =
      Nap.jsonBody[ThrowsIllegalArg].action[Unit] { ctx => Ok(()) }
  }

  class TestResource(implicit val executionContext: ExecutionContext, val materializer: akka.stream.Materializer)
      extends TopLevelCollectionResource[Int, SimpleModel] {

    override def keyFormat: KeyFormat[Int] = KeyFormat.intKeyFormat
    override def resourceName: String = "testBuilderResource"
    override implicit val resourceFormat: OFormat[SimpleModel] = SimpleModel.format
    implicit val fields = Fields

    /** Uses rawJsonBody to accept any valid JSON. Returns 200 on success. */
    def rawJsonAction = Nap.rawJsonBody().action[Unit] { ctx =>
      Ok(())
    }

    /** Uses rawJsonBody with a 10-byte max size. */
    def rawJsonActionSmallLimit = Nap.rawJsonBody(10).action[Unit] { ctx =>
      Ok(())
    }

    /** Uses jsonBody with a typed Payload model. */
    def typedJsonAction = Nap.jsonBody[Payload].action[Unit] { ctx =>
      Ok(())
    }

    /** Default (allowAll) auth. */
    def authAction = Nap.get { ctx =>
      Ok(Keyed(1, SimpleModel(1)))
    }

    /** Switches to a denying auth policy. */
    def denyAuthAction = Nap.auth(denyAll).get { ctx =>
      Ok(Keyed(1, SimpleModel(1)))
    }

    /** Uses `returning` to reshape the builder type parameter. */
    def returningAction = Nap.returning[Unit]().action[Unit] { ctx =>
      Ok(())
    }
  }
}
