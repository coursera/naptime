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

package org.coursera.naptime.path

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class CollectionResourcePathParserTest extends AssertionsForJUnit {

  @Test
  def testSimpleFinalLevelParsing(): Unit = {
    val path = CollectionResourcePathParser[String]("foo", 1)
    assert(ParseSuccess(None, Some("hello")) === path.parseOptUrl("/foo.v1/hello"))
    assert(ParseSuccess(None, None) === path.parseOptUrl("/foo.v1/"))
    assert(ParseSuccess(None, None) === path.parseOptUrl("/foo.v1"))
    assert(ParseFailure === path.parseOptUrl("/foo.v3"))
  }

  @Test
  def testSimpleParsing(): Unit = {
    val path = CollectionResourcePathParser[String]("foo", 1)
    assert(ParseSuccess(None, "hello") === path.parseUrl("/foo.v1/hello"))
    assert(ParseSuccess(Some("/bar.v2"), "world") === path.parseUrl("/foo.v1/world/bar.v2"))
    assert(
      ParseSuccess(Some("/bar.v2/123/baz.v3/321"), "goodbye") ===
        path.parseUrl("/foo.v1/goodbye/bar.v2/123/baz.v3/321"))
    assert(ParseFailure === path.parseUrl("/bar.v1/hello"))
  }

  @Test
  def testTypeConversion(): Unit = {
    val path = CollectionResourcePathParser[Int]("bar", 3)
    assert(ParseSuccess(None, 42) === path.parseUrl("/bar.v3/42"))
    assert(ParseSuccess(Some("/baz.v2"), 111) === path.parseUrl("/bar.v3/111/baz.v2"))
    assert(ParseFailure === path.parseUrl("/baz.v2/hello"))
  }
}
