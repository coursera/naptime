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

package org.coursera.naptime.ari

import com.linkedin.data.DataMap
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

class AriModelsTest extends AssertionsForJUnit {

  // ─── FullSchema ────────────────────────────────────────────────────────────

  @Test
  def fullSchema_empty_hasEmptyCollections(): Unit = {
    val fs = FullSchema.empty
    assert(fs.resources.isEmpty)
    assert(fs.types.isEmpty)
  }

  @Test
  def fullSchema_withResources_storesResources(): Unit = {
    val fs = FullSchema(Set.empty, Set.empty)
    assert(fs.resources.isEmpty)
    assert(fs.types.isEmpty)
  }

  // ─── Response ─────────────────────────────────────────────────────────────

  @Test
  def response_empty_hasEmptyData(): Unit = {
    val r = Response.empty
    assert(r.data.isEmpty)
    assert(r.url === None)
    assert(r.pagination === ResponsePagination.empty)
  }

  @Test
  def response_withData_storesData(): Unit = {
    val dm = new DataMap()
    dm.put("id", "123")
    val r = Response(
      data = List(dm),
      pagination = ResponsePagination(Some("next"), Some(1L), None),
      url = Some("http://example.com/api/v1/items"))
    assert(r.data.size === 1)
    assert(r.pagination.next === Some("next"))
    assert(r.pagination.total === Some(1L))
    assert(r.url === Some("http://example.com/api/v1/items"))
  }

  // ─── FetcherError ─────────────────────────────────────────────────────────

  @Test
  def fetcherError_storesFields(): Unit = {
    val e = FetcherError(404, "Not found", Some("/api/v1/items"))
    assert(e.code === 404)
    assert(e.message === "Not found")
    assert(e.url === Some("/api/v1/items"))
  }

  @Test
  def fetcherError_noUrl(): Unit = {
    val e = FetcherError(500, "Server error", None)
    assert(e.url === None)
  }
}
