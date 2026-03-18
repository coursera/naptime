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

package org.coursera.naptime.resources

import akka.stream.Materializer
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.Ok
import org.coursera.naptime.ResourceTestImplicits
import org.coursera.naptime.RestError
import org.coursera.naptime.RestResponse
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import scala.concurrent.ExecutionContext

/**
 * Tests for the helper methods on [[CollectionResource]] and [[TopLevelCollectionResource]]
 * that were previously uncovered:
 *   - `OkIfPresent[T](Option[T])` — simple Option lifting
 *   - `OkIfPresent(key, Option[M])` — keyed variant
 *   - `Nap` builder availability
 *   - `pathParser` construction
 */
class CollectionResourceTest extends AssertionsForJUnit with ResourceTestImplicits {

  import CollectionResourceTest._

  private def makeResource: TestResource = new TestResource

  // ─── OkIfPresent(Option[T]) ──────────────────────────────────────────────────

  @Test
  def okIfPresent_some_returnsOk(): Unit = {
    val resource = makeResource
    resource.OkIfPresent(Some("hello")) match {
      case ok: Ok[_] => assertResult("hello")(ok.content)
      case other     => fail(s"Expected Ok but got $other")
    }
  }

  @Test
  def okIfPresent_none_returnsRestError404(): Unit = {
    val resource = makeResource
    resource.OkIfPresent(None: Option[String]) match {
      case RestError(ex) => assertResult(404)(ex.httpCode)
      case other         => fail(s"Expected RestError but got $other")
    }
  }

  // ─── OkIfPresent(key, Option[M]) ─────────────────────────────────────────────

  @Test
  def okIfPresentKeyed_someValue_returnsKeyedOk(): Unit = {
    val resource = makeResource
    resource.OkIfPresent(42, Some(Widget("sprocket"))) match {
      case ok: Ok[_] =>
        val keyed = ok.content.asInstanceOf[Keyed[Int, Widget]]
        assertResult(42)(keyed.key)
        assertResult(Widget("sprocket"))(keyed.value)
      case other => fail(s"Expected Ok(Keyed(...)) but got $other")
    }
  }

  @Test
  def okIfPresentKeyed_none_returnsRestError404(): Unit = {
    val resource = makeResource
    resource.OkIfPresent(42, None: Option[Widget]) match {
      case RestError(ex) => assertResult(404)(ex.httpCode)
      case other         => fail(s"Expected RestError but got $other")
    }
  }

  // ─── Nap builder ─────────────────────────────────────────────────────────────

  @Test
  def nap_builderIsNonNull(): Unit = {
    val resource = makeResource
    // Just ensure Nap can be constructed without throwing
    val builder = resource.Nap[Nothing, Nothing]
    assert(builder != null)
  }

  // ─── pathParser ──────────────────────────────────────────────────────────────

  @Test
  def pathParser_isNonNull(): Unit = {
    val resource = makeResource
    assert(resource.pathParser != null)
  }

  @Test
  def pathParser_parsesKnownPath(): Unit = {
    val resource = makeResource
    val result =
      resource.pathParser.parse(s"/${resource.resourceName}.v${resource.resourceVersion}/123")
    assert(!result.isEmpty)
  }
}

object CollectionResourceTest {

  case class Widget(name: String)
  object Widget {
    implicit val format: OFormat[Widget] = Json.format[Widget]
  }

  class TestResource(
      implicit val executionContext: ExecutionContext,
      val materializer: Materializer)
      extends TopLevelCollectionResource[Int, Widget] {

    override def keyFormat: KeyFormat[Int] = KeyFormat.intKeyFormat
    override implicit def resourceFormat: OFormat[Widget] = Widget.format
    override def resourceName: String = "widgets"
    implicit val fields = Fields
  }
}
