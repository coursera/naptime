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

package org.coursera.naptime

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.http.Status

class ResponseTest extends AssertionsForJUnit {

  // ─── Redirect ──────────────────────────────────────────────────────────────

  @Test
  def redirect_isRedirect(): Unit = {
    val r = Redirect("/somewhere", isTemporary = true)
    assert(r.isOk === false)
    assert(r.isError === false)
    assert(r.isRedirect === true)
  }

  @Test
  def redirect_temporary_result(): Unit = {
    val r = Redirect("/somewhere", isTemporary = true)
    val result = r.result
    assert(result.header.status === Status.TEMPORARY_REDIRECT)
  }

  @Test
  def redirect_permanent_result(): Unit = {
    val r = Redirect("/somewhere", isTemporary = false)
    val result = r.result
    assert(result.header.status === Status.MOVED_PERMANENTLY)
  }

  @Test
  def redirect_map_returnsSelf(): Unit = {
    val r = Redirect("/somewhere", isTemporary = true)
    val mapped = r.map(_ => "ignored")
    assert(mapped === r)
  }

  // ─── Ok ────────────────────────────────────────────────────────────────────

  @Test
  def ok_isOk(): Unit = {
    val ok = Ok("hello")
    assert(ok.isOk === true)
    assert(ok.isError === false)
    assert(ok.isRedirect === false)
  }

  @Test
  def ok_map_transformsContent(): Unit = {
    val ok = Ok(42)
    val mapped = ok.map(_ * 2)
    assert(mapped.asInstanceOf[Ok[Int]].content === 84)
  }

  @Test
  def ok_withPagination_nextString(): Unit = {
    val ok = Ok(List(1, 2, 3))
    val paged = ok.withPagination("cursor-abc")
    assert(paged.pagination.isDefined)
    assert(paged.pagination.get.next === Some("cursor-abc"))
  }

  @Test
  def ok_withPagination_optionalNextAndTotal(): Unit = {
    val ok = Ok(List(1, 2, 3))
    val paged = ok.withPagination(next = Some("next-token"), total = Some(100L))
    assert(paged.pagination.isDefined)
    assert(paged.pagination.get.next === Some("next-token"))
    assert(paged.pagination.get.total === Some(100L))
  }

  @Test
  def ok_withPagination_responsePagination(): Unit = {
    val ok = Ok(List(1, 2, 3))
    val pagination = ResponsePagination(Some("cursor"), Some(50L), None)
    val paged = ok.withPagination(pagination)
    assert(paged.pagination === Some(pagination))
  }

  @Test
  def ok_withETag_setsETag(): Unit = {
    val ok = Ok("data")
    val withEtag = ok.withETag(ETag.Strong("abc123"))
    assert(withEtag.eTag === Some(ETag.Strong("abc123")))
  }

  // ─── RestError ─────────────────────────────────────────────────────────────

  @Test
  def restError_isError(): Unit = {
    val e = Errors.NotFound(msg = "not found")
    val restError = RestError(e)
    assert(restError.isOk === false)
    assert(restError.isError === true)
    assert(restError.isRedirect === false)
  }

  @Test
  def restError_map_returnsSelf(): Unit = {
    val e = Errors.NotFound(msg = "not found")
    val restError = RestError(e)
    val mapped = restError.map(identity)
    assert(mapped === restError)
  }
}
