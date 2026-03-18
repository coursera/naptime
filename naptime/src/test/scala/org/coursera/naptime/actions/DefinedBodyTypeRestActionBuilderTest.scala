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
import org.coursera.naptime.access.StructuredAccessControl
import org.coursera.naptime.access.authenticator.Authenticator
import org.coursera.naptime.access.authenticator.Decorator
import org.coursera.naptime.access.authenticator.HeaderAuthenticationParser
import org.coursera.naptime.access.authenticator.ParseResult
import org.coursera.naptime.access.authorizer.AuthorizeResult
import org.coursera.naptime.access.authorizer.Authorizer
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.http.Status
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

/**
 * Tests for [[DefinedBodyTypeRestActionBuilder]] covering `auth`, `catching`, and `returning`.
 */
class DefinedBodyTypeRestActionBuilderTest
    extends AssertionsForJUnit
    with ScalaFutures
    with ResourceTestImplicits {

  import DefinedBodyTypeRestActionBuilderTest._

  private def runAction(
      action: play.api.mvc.EssentialAction,
      request: FakeRequest[_],
      body: ByteString = ByteString.empty): Result = {
    val accumulator = action(request.withBody(()))
    val resultFuture = accumulator.run(akka.stream.scaladsl.Source.single(body))
    Helpers.await(resultFuture)
  }

  @Test
  def bodyDependentAuth_allowed(): Unit = {
    val resource = new TestResource
    val action = resource.bodyDependentAuthAction
    val body = ByteString("""{"name":"Alice","value":42}""")
    val result = runAction(action, FakeRequest("POST", "/"), body)
    assert(result.header.status >= 200 && result.header.status < 300)
  }

  @Test
  def bodyDependentAuth_denied_returnsForbidden(): Unit = {
    val resource = new TestResource
    val action = resource.bodyDependentAuthDenyAction
    val body = ByteString("""{"name":"Alice","value":42}""")
    val result = runAction(action, FakeRequest("POST", "/"), body)
    assertResult(Status.FORBIDDEN)(result.header.status)
  }

  @Test
  def definedBodyCatching_withError_returnsCustomError(): Unit = {
    val resource = new TestResource
    val action = resource.catchingAction
    val body = ByteString("""{"name":"Alice","value":42}""")
    val result = runAction(action, FakeRequest("POST", "/"), body)
    assertResult(Status.CONFLICT)(result.header.status)
  }

  @Test
  def definedBodyReturning_reshapedBuilder_actionStillRuns(): Unit = {
    val resource = new TestResource
    val action = resource.returningAction
    val body = ByteString("""{}""")
    val result = runAction(action, FakeRequest("POST", "/"), body)
    assert(result.header.status >= 200 && result.header.status < 300)
  }
}

object DefinedBodyTypeRestActionBuilderTest {

  case class Payload(name: String, value: Int)
  object Payload {
    implicit val reads: Reads[Payload] = Json.reads[Payload]
    implicit val format: OFormat[Payload] = Json.format[Payload]
  }

  case class SimpleModel(id: Int)
  object SimpleModel {
    implicit val format: OFormat[SimpleModel] = Json.format[SimpleModel]
  }

  private val allowingBodyAuth: Payload => HeaderAccessControl[Unit] = { _ =>
    val parser = HeaderAuthenticationParser.constant(())
    val authorizer = Authorizer[Unit](_ => AuthorizeResult.Authorized)
    StructuredAccessControl(Authenticator(parser, Decorator.identity[Unit]), authorizer)
  }

  private val denyingBodyAuth: Payload => HeaderAccessControl[Unit] = { _ =>
    val parser = HeaderAuthenticationParser.constant(())
    val authorizer = Authorizer[Unit](_ => AuthorizeResult.Rejected("denied"))
    StructuredAccessControl(Authenticator(parser, Decorator.identity[Unit]), authorizer)
  }

  class TestResource(
      implicit val executionContext: ExecutionContext,
      val materializer: akka.stream.Materializer)
      extends TopLevelCollectionResource[Int, SimpleModel] {

    override def keyFormat: KeyFormat[Int] = KeyFormat.intKeyFormat
    override def resourceName: String = "testDefinedBodyResource"
    override implicit val resourceFormat: OFormat[SimpleModel] = SimpleModel.format
    implicit val fields = Fields

    /** Uses body-dependent auth (allows). */
    def bodyDependentAuthAction = Nap.jsonBody[Payload].auth(allowingBodyAuth).action[Unit] { ctx =>
      Ok(())
    }

    /** Uses body-dependent auth (denies). */
    def bodyDependentAuthDenyAction = Nap.jsonBody[Payload].auth(denyingBodyAuth).action[Unit] {
      ctx =>
        Ok(())
    }

    /** Uses `catching` on a DefinedBodyTypeRestActionBuilder. */
    def catchingAction =
      Nap
        .jsonBody[Payload]
        .catching {
          case _: IllegalStateException =>
            RestError(NaptimeActionException(Status.CONFLICT, Some("conflict"), None))
        }
        .action[Unit] { _ =>
          throw new IllegalStateException("conflict!")
        }

    /** Uses `returning` on a DefinedBodyTypeRestActionBuilder. */
    def returningAction = Nap.rawJsonBody().returning[Unit]().action[Unit] { ctx =>
      Ok(())
    }
  }
}
