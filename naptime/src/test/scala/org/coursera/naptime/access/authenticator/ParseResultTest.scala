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

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.http.Status

class ParseResultTest extends AssertionsForJUnit {

  // ─── Success ──────────────────────────────────────────────────────────────────

  @Test def success_flatMap_transformsParsed(): Unit = {
    val result = ParseResult.Success("token").flatMap(t => ParseResult.Success(t.length))
    assertResult(ParseResult.Success(5))(result)
  }

  @Test def success_flatMap_toSkip(): Unit = {
    val result = ParseResult.Success("x").flatMap(_ => ParseResult.Skip)
    assertResult(ParseResult.Skip)(result)
  }

  @Test def success_flatMap_toError(): Unit = {
    val err = ParseResult.Error("bad token")
    val result = ParseResult.Success("x").flatMap(_ => err)
    assertResult(err)(result)
  }

  // ─── Skip ─────────────────────────────────────────────────────────────────────

  @Test def skip_flatMap_returnsItself(): Unit = {
    val result = ParseResult.Skip.flatMap(_ => ParseResult.Success("should not run"))
    assertResult(ParseResult.Skip)(result)
  }

  // ─── Error ────────────────────────────────────────────────────────────────────

  @Test def error_flatMap_returnsItself(): Unit = {
    val err = ParseResult.Error("unauthorized", Status.UNAUTHORIZED)
    val result = err.flatMap(_ => ParseResult.Success("should not run"))
    assertResult(err)(result)
  }

  @Test def error_defaultCode_is401(): Unit = {
    val err = ParseResult.Error("bad header")
    assertResult(Status.UNAUTHORIZED)(err.code)
  }

  @Test def error_customCode(): Unit = {
    val err = ParseResult.Error("forbidden", Status.FORBIDDEN)
    assertResult(Status.FORBIDDEN)(err.code)
    assertResult("forbidden")(err.message)
  }
}
