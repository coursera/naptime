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

import org.coursera.naptime.Errors
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.Ok
import org.coursera.naptime.QueryFields
import org.coursera.naptime.QueryIncludes
import org.coursera.naptime.Redirect
import org.coursera.naptime.RequestPagination
import org.coursera.naptime.ResourceFields
import org.coursera.naptime.RestError
import org.coursera.naptime.ResourceName
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import play.api.test.FakeRequest
import play.api.test.Helpers.defaultAwaitTimeout

import scala.concurrent.Future

/**
 * Direct engine tests for [[PlayJsonRestActionCategoryEngine]] targeting many uncovered paths.
 *
 * Rather than going through a full resource, we call `mkResult` / `mkResponse` directly.
 */
class PlayJsonEngineTest extends AssertionsForJUnit {

  import PlayJsonEngineTest._

  private val pagination = RequestPagination(20, None, isDefault = true)
  private val fields: ResourceFields[Model] = ResourceFields[Model]
  private val reqFields = QueryFields.empty
  private val reqIncludes = QueryIncludes.empty
  private val req = FakeRequest("GET", "/")

  // ─── getActionCategoryEngine ──────────────────────────────────────────────

  @Test
  def get_ok_returns200(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .getActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(Keyed(1, Model("hello")))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.OK)
  }

  @Test
  def get_error_returnsErrorStatus(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .getActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = RestError(Errors.NotFound(msg = "not found"))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.NOT_FOUND)
  }

  @Test
  def get_redirect_returnsRedirectStatus(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .getActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Redirect("/new-location", isTemporary = true)
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.TEMPORARY_REDIRECT)
  }

  @Test
  def get_notModified_whenETagMatches(): Unit = {
    import play.api.http.HeaderNames
    val engine = PlayJsonRestActionCategoryEngine
      .getActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    // First compute the ETag
    val response = Ok(Keyed(1, Model("hello")))
    val firstResult = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    val etagValue = firstResult.header.headers.get(HeaderNames.ETAG).get
    // Now send request with that ETag
    val reqWithEtag = FakeRequest("GET", "/").withHeaders(HeaderNames.IF_NONE_MATCH -> etagValue)
    val result = engine.mkResult(reqWithEtag, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.NOT_MODIFIED)
  }

  // ─── createActionCategoryEngine ──────────────────────────────────────────

  @Test
  def create_ok_withBody_returns201(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .createActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(Keyed(1, Some(Model("hello"))))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.CREATED)
  }

  @Test
  def create_ok_withoutBody_returns201(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .createActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(Keyed(1, None: Option[Model]))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.CREATED)
  }

  @Test
  def create_pathWithSlash_appendsKey(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .createActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(Keyed(42, Some(Model("hello"))))
    val reqWithSlash = FakeRequest("POST", "/api/items/")
    val result = engine.mkResult(reqWithSlash, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.CREATED)
    assert(result.header.headers.get("Location").exists(_.contains("42")))
  }

  @Test
  def create_error_returnsErrorStatus(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .createActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = RestError(Errors.Forbidden(msg = "forbidden"))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.FORBIDDEN)
  }

  // ─── updateActionCategoryEngine ──────────────────────────────────────────

  @Test
  def update_ok_withContent_returns200(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .updateActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(Some(Keyed(1, Model("updated"))))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.OK)
  }

  @Test
  def update_ok_withoutContent_returns204(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .updateActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(None: Option[Keyed[Int, Model]])
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.NO_CONTENT)
  }

  @Test
  def update_error_returnsErrorStatus(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .updateActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = RestError(Errors.BadRequest(msg = "bad"))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.BAD_REQUEST)
  }

  // ─── patchActionCategoryEngine ────────────────────────────────────────────

  @Test
  def patch_ok_returns200(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .patchActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(Keyed(1, Model("patched")))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.OK)
  }

  @Test
  def patch_error_returnsErrorStatus(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .patchActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = RestError(Errors.Conflict(msg = "conflict"))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.CONFLICT)
  }

  // ─── deleteActionCategoryEngine ──────────────────────────────────────────

  @Test
  def delete_ok_returns204(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .deleteActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(())
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.NO_CONTENT)
  }

  @Test
  def delete_error_returnsErrorStatus(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .deleteActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = RestError(Errors.NotFound(msg = "not found"))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.NOT_FOUND)
  }

  // ─── multiGetActionCategoryEngine ────────────────────────────────────────

  @Test
  def multiGet_ok_returns200(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .multiGetActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(Seq(Keyed(1, Model("a")), Keyed(2, Model("b"))))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.OK)
  }

  @Test
  def multiGet_notModified_whenETagMatches(): Unit = {
    import play.api.http.HeaderNames
    val engine = PlayJsonRestActionCategoryEngine
      .multiGetActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(Seq(Keyed(1, Model("a"))))
    val firstResult = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    val etagValue = firstResult.header.headers.get(HeaderNames.ETAG).get
    val reqWithEtag = FakeRequest("GET", "/").withHeaders(HeaderNames.IF_NONE_MATCH -> etagValue)
    val result = engine.mkResult(reqWithEtag, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.NOT_MODIFIED)
  }

  // ─── getAllActionCategoryEngine ───────────────────────────────────────────

  @Test
  def getAll_ok_returns200(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .getAllActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(Seq(Keyed(1, Model("a"))))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.OK)
  }

  @Test
  def getAll_notModified_whenETagMatches(): Unit = {
    import play.api.http.HeaderNames
    val engine = PlayJsonRestActionCategoryEngine
      .getAllActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(Seq(Keyed(1, Model("a"))))
    val firstResult = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    val etagValue = firstResult.header.headers.get(HeaderNames.ETAG).get
    val reqWithEtag = FakeRequest("GET", "/").withHeaders(HeaderNames.IF_NONE_MATCH -> etagValue)
    val result = engine.mkResult(reqWithEtag, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.NOT_MODIFIED)
  }

  // ─── finderActionCategoryEngine ──────────────────────────────────────────

  @Test
  def finder_ok_returns200(): Unit = {
    val engine = PlayJsonRestActionCategoryEngine
      .finderActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(Seq(Keyed(1, Model("a"))))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.OK)
  }

  @Test
  def finder_notModified_whenETagMatches(): Unit = {
    import play.api.http.HeaderNames
    val engine = PlayJsonRestActionCategoryEngine
      .finderActionCategoryEngine[Int, Model](Model.writes, KeyFormat.intKeyFormat)
    val response = Ok(Seq(Keyed(1, Model("a"))))
    val firstResult = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    val etagValue = firstResult.header.headers.get(HeaderNames.ETAG).get
    val reqWithEtag = FakeRequest("GET", "/").withHeaders(HeaderNames.IF_NONE_MATCH -> etagValue)
    val result = engine.mkResult(reqWithEtag, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.NOT_MODIFIED)
  }

  // ─── actionActionCategoryEngine ──────────────────────────────────────────

  @Test
  def action_ok_returns200(): Unit = {
    import play.api.libs.json.Writes
    implicit val strWrites = Writes.StringWrites
    val engine = PlayJsonRestActionCategoryEngine
      .actionActionCategoryEngine[Int, Model, String](strWrites, Model.writes, KeyFormat.intKeyFormat)
    val response = Ok("action result")
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.OK)
  }

  @Test
  def action_error_returnsErrorStatus(): Unit = {
    import play.api.libs.json.Writes
    implicit val strWrites = Writes.StringWrites
    val engine = PlayJsonRestActionCategoryEngine
      .actionActionCategoryEngine[Int, Model, String](strWrites, Model.writes, KeyFormat.intKeyFormat)
    val response = RestError(Errors.InternalServerError(msg = "error"))
    val result = engine.mkResult(req, fields, reqFields, reqIncludes, pagination, response)
    assert(result.header.status === Status.INTERNAL_SERVER_ERROR)
  }
}

object PlayJsonEngineTest {
  case class Model(name: String)
  object Model {
    implicit val writes: OWrites[Model] = Json.writes[Model]
    implicit val fmt: OFormat[Model] = Json.format[Model]
  }
}
