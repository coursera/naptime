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

package org.coursera.naptime.router2

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.Timeout
import org.coursera.common.stringkey.StringKeyFormat
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * Tests for the parser helpers in [[CollectionResourceRouter]]:
 *   - [[CollectionResourceRouter.OptionBooleanFlagParser]] (0 % coverage before this file)
 *   - [[CollectionResourceRouter.OptionalQueryParser]] (branch gaps)
 *   - [[CollectionResourceRouter.StrictQueryParser]] (branch gaps)
 *
 * The parsers were unchanged by the 2.13 migration but were essentially untested.
 */
class CollectionResourceRouterParsersTest extends AssertionsForJUnit with ScalaFutures {

  // Akka infrastructure needed to run EssentialAction
  implicit val system: ActorSystem = ActorSystem("CollectionResourceRouterParsersTest")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

  // ─────────────────────────────────────────────────────────────────────────────
  // OptionBooleanFlagParser
  // ─────────────────────────────────────────────────────────────────────────────

  private def optBoolParser(paramName: String) =
    CollectionResourceRouter.OptionBooleanFlagParser(paramName, getClass)

  @Test
  def optBoolParser_missingParam_returnsNone(): Unit = {
    val req = FakeRequest("GET", "/test")
    assertResult(Right(None))(optBoolParser("flag").evaluate(req))
  }

  @Test
  def optBoolParser_emptyStringValue_returnsLeft(): Unit = {
    // query string present with empty string value → not a valid boolean
    val req = FakeRequest("GET", "/test?flag=")
    assert(optBoolParser("flag").evaluate(req).isLeft)
  }

  @Test
  def optBoolParser_trueValue_returnsSomeTrue(): Unit = {
    val req = FakeRequest("GET", "/test?flag=true")
    assertResult(Right(Some(true)))(optBoolParser("flag").evaluate(req))
  }

  @Test
  def optBoolParser_falseValue_returnsSomeFalse(): Unit = {
    val req = FakeRequest("GET", "/test?flag=false")
    assertResult(Right(Some(false)))(optBoolParser("flag").evaluate(req))
  }

  @Test
  def optBoolParser_unknownValue_returnsLeft(): Unit = {
    val req = FakeRequest("GET", "/test?flag=yes")
    assert(optBoolParser("flag").evaluate(req).isLeft)
  }

  @Test
  def optBoolParser_duplicateParam_returnsLeft(): Unit = {
    // Two values for the same parameter — should reject
    val req = FakeRequest("GET", "/test?flag=true&flag=false")
    assert(optBoolParser("flag").evaluate(req).isLeft)
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // BooleanFlagParser — existing tests in QueryParserTests; add missing branches
  // ─────────────────────────────────────────────────────────────────────────────

  private def boolParser(paramName: String) =
    CollectionResourceRouter.BooleanFlagParser(paramName, getClass)

  @Test
  def boolParser_missingParam_returnsLeft(): Unit = {
    val req = FakeRequest("GET", "/test")
    assert(boolParser("flag").evaluate(req).isLeft)
  }

  @Test
  def boolParser_duplicateParam_returnsLeft(): Unit = {
    val req = FakeRequest("GET", "/test?flag=true&flag=false")
    assert(boolParser("flag").evaluate(req).isLeft)
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // OptionalQueryParser
  // ─────────────────────────────────────────────────────────────────────────────

  private def optParser(paramName: String) =
    CollectionResourceRouter.OptionalQueryParser(
      paramName,
      StringKeyFormat.stringFormat,
      getClass)

  @Test
  def optQueryParser_missingParam_returnsNone(): Unit = {
    val req = FakeRequest("GET", "/test")
    assertResult(Right(None))(optParser("p").evaluate(req))
  }

  @Test
  def optQueryParser_presentParam_returnsSomeValue(): Unit = {
    val req = FakeRequest("GET", "/test?p=hello")
    assertResult(Right(Some("hello")))(optParser("p").evaluate(req))
  }

  @Test
  def optQueryParser_duplicateParam_returnsLeft(): Unit = {
    val req = FakeRequest("GET", "/test?p=a&p=b")
    assert(optParser("p").evaluate(req).isLeft)
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // StrictQueryParser
  // ─────────────────────────────────────────────────────────────────────────────

  private def strictParser(paramName: String) =
    CollectionResourceRouter.StrictQueryParser(
      paramName,
      StringKeyFormat.stringFormat,
      getClass)

  @Test
  def strictQueryParser_missingParam_returnsLeft(): Unit = {
    val req = FakeRequest("GET", "/test")
    assert(strictParser("p").evaluate(req).isLeft)
  }

  @Test
  def strictQueryParser_presentParam_returnsValue(): Unit = {
    val req = FakeRequest("GET", "/test?p=world")
    assertResult(Right("world"))(strictParser("p").evaluate(req))
  }

  @Test
  def strictQueryParser_duplicateParam_returnsLeft(): Unit = {
    val req = FakeRequest("GET", "/test?p=x&p=y")
    assert(strictParser("p").evaluate(req).isLeft)
  }

  @Test
  def strictQueryParser_namespacesParam_stripsLeadingComma(): Unit = {
    // The parser has a special-case hack for 'namespaces' with a leading comma.
    val req = FakeRequest("GET", "/test?namespaces=,foo")
    val result = CollectionResourceRouter
      .StrictQueryParser("namespaces", StringKeyFormat.stringFormat, getClass)
      .evaluate(req)
    assertResult(Right("foo"))(result)
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Invoke the error RouteAction returned by parsers (covers errorRoute body + tagRequest)
  // ─────────────────────────────────────────────────────────────────────────────

  @Test
  def errorRoute_apply_returnsBadRequest(): Unit = {
    val errorAction = CollectionResourceRouter.errorRoute("test error", getClass)
    val request = FakeRequest("GET", "/test")
    val result = Helpers.await(errorAction(request).run())
    assert(result.header.status === Status.BAD_REQUEST)
  }

  @Test
  def errorRoute_tagRequest_addsNaptimeResourceTag(): Unit = {
    val errorAction = CollectionResourceRouter.errorRoute("test error", getClass)
    val request = FakeRequest("GET", "/test")
    val tagged = errorAction.tagRequest(request)
    val tags = tagged.attrs.get(NaptimeAttrKey.tags).getOrElse(Map.empty)
    assert(tags.contains(Router.NAPTIME_RESOURCE_NAME))
  }

  @Test
  def strictQueryParser_missingParam_errorRoute_returnsBadRequest(): Unit = {
    val req = FakeRequest("GET", "/test")
    val errorRoute = strictParser("p").evaluate(req).left.get
    val result = Helpers.await(errorRoute(req).run())
    assert(result.header.status === Status.BAD_REQUEST)
  }

  @Test
  def optQueryParser_duplicateParam_errorRoute_returnsBadRequest(): Unit = {
    val req = FakeRequest("GET", "/test?p=a&p=b")
    val errorRoute = optParser("p").evaluate(req).left.get
    val result = Helpers.await(errorRoute(req).run())
    assert(result.header.status === Status.BAD_REQUEST)
  }
}
