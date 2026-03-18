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

package org.coursera.naptime.path

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

class UrlParseResultTest extends AssertionsForJUnit {

  // ─── isEmpty ─────────────────────────────────────────────────────────────────

  @Test def parseSuccess_isEmpty_false(): Unit = {
    assert(!ParseSuccess(None, "hello").isEmpty)
  }

  @Test def parseFailure_isEmpty_true(): Unit = {
    assert(ParseFailure.isEmpty)
  }

  // ─── getOrElse ───────────────────────────────────────────────────────────────

  @Test def parseSuccess_getOrElse_returnsElem(): Unit = {
    assertResult("hello")(ParseSuccess(None, "hello").getOrElse("default"))
  }

  @Test def parseFailure_getOrElse_returnsDefault(): Unit = {
    assertResult("default")(ParseFailure.getOrElse("default"))
  }

  // ─── filter ──────────────────────────────────────────────────────────────────

  @Test def parseSuccess_filter_condTrue_returnsSelf(): Unit = {
    val result = ParseSuccess(None, "hello").filter(_.startsWith("h"))
    assertResult(ParseSuccess(None, "hello"))(result)
  }

  @Test def parseSuccess_filter_condFalse_returnsFailure(): Unit = {
    val result = ParseSuccess(None, "hello").filter(_.startsWith("z"))
    assertResult(ParseFailure)(result)
  }

  @Test def parseFailure_filter_returnsFailure(): Unit = {
    val result = ParseFailure.filter(_ => true)
    assertResult(ParseFailure)(result)
  }

  // ─── map ─────────────────────────────────────────────────────────────────────

  @Test def parseSuccess_map_transformsElem(): Unit = {
    val result = ParseSuccess(Some("/rest"), "hello").map(_.length)
    assertResult(ParseSuccess(Some("/rest"), 5))(result)
  }

  @Test def parseFailure_map_returnsFailure(): Unit = {
    val typed: UrlParseResult[String] = ParseFailure
    val result = typed.map(_.length)
    assertResult(ParseFailure)(result)
  }

  // ─── flatMap ─────────────────────────────────────────────────────────────────

  @Test def parseSuccess_flatMap_appliesFunction(): Unit = {
    val result = ParseSuccess(Some("/rest"), "hello").flatMap { (url, elem) =>
      ParseSuccess(url, elem.toUpperCase)
    }
    assertResult(ParseSuccess(Some("/rest"), "HELLO"))(result)
  }

  @Test def parseSuccess_flatMap_canReturnFailure(): Unit = {
    val result = ParseSuccess(None, "hello").flatMap { (_, _) =>
      ParseFailure
    }
    assertResult(ParseFailure)(result)
  }

  @Test def parseFailure_flatMap_returnsFailure(): Unit = {
    val typed: UrlParseResult[String] = ParseFailure
    val result = typed.flatMap((url, elem) => ParseSuccess(url, elem))
    assertResult(ParseFailure)(result)
  }
}
