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

import org.coursera.common.stringkey.StringKey
import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.naptime.model.KeyFormat
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class PathKeyParserTest extends AssertionsForJUnit {
  import PathKeyParserTest._

  @Test
  def rootParser(): Unit = {
    assert(
      ParseSuccess(Some("/foo.v3/asdf"), RootParsedPathKey) ===
        RootPathParser.parse("/foo.v3/asdf"))
  }

  @Test
  def oneLevelNesting(): Unit = {
    val parser = CollectionResourcePathParser[String]("foo", 1) :: RootPathParser
    assert(ParseSuccess(None, "asdf" ::: RootParsedPathKey) === parser.parse("/foo.v1/asdf"))
    assert(ParseSuccess(None, "asdf" ::: RootParsedPathKey) === parser.parse("/foo.v1/asdf/"))
    assert(ParseFailure === parser.parse("/bar.v1/asdf"))
  }

  @Test
  def multiLevelNesting(): Unit = {
    val parser = CollectionResourcePathParser[Int]("bar", 3) ::
      CollectionResourcePathParser[String]("foo", 1) ::
      RootPathParser
    assert(
      ParseSuccess(None, 3 ::: "asdf" ::: RootParsedPathKey) ===
        parser.parse("/foo.v1/asdf/bar.v3/3"))
    assert(
      ParseSuccess(None, 3 ::: "asdf" ::: RootParsedPathKey) ===
        parser.parse("/foo.v1/asdf/bar.v3/3/"))
    assert(ParseFailure === parser.parse("/bar.v3/3"))
    assert(ParseFailure === parser.parse("/bar.v3/3/"))
    assert(ParseFailure === parser.parse("/foo.v1/asdf"))
    assert(ParseFailure === parser.parse("/foo.v1/asdf/"))
  }

  @Test
  def finalOptParser(): Unit = {
    val parser = CollectionResourcePathParser[Int]("bar", 3) ::
      CollectionResourcePathParser[String]("foo", 1) ::
      RootPathParser

    assert(
      ParseSuccess(None, Some(3) ::: "asdf" ::: RootParsedPathKey) ===
        parser.parseFinalLevel("/foo.v1/asdf/bar.v3/3"))
    assert(
      ParseSuccess(None, Some(3) ::: "asdf" ::: RootParsedPathKey) ===
        parser.parseFinalLevel("/foo.v1/asdf/bar.v3/3/"))
    assert(
      ParseSuccess(None, None ::: "asdf" ::: RootParsedPathKey) ===
        parser.parseFinalLevel("/foo.v1/asdf/bar.v3/"))
    assert(
      ParseSuccess(None, None ::: "asdf" ::: RootParsedPathKey) ===
        parser.parseFinalLevel("/foo.v1/asdf/bar.v3"))

    assert(ParseFailure === parser.parseFinalLevel("/bar.v3/3"))
    assert(ParseFailure === parser.parseFinalLevel("/foo.v1/asdf"))
    assert(ParseFailure === parser.parseFinalLevel("/foo.v1/asdf/bar"))
    assert(ParseFailure === parser.parseFinalLevel("/foo.v1/asdf/bar.v3/notANumber"))
  }

  @Test
  def sophisticatedTypes(): Unit = {
    val parser = CollectionResourcePathParser[Compound]("myResource", 1) :: RootPathParser

    val element = Compound("abc123", 3)
    val elementString = Compound.stringKeyFormat.writes(element).key

    assert(
      ParseSuccess(None, Some(element) ::: RootParsedPathKey) ===
        parser.parseFinalLevel(s"/myResource.v1/$elementString"))

    // Test incomplete ID.
    assert(ParseFailure === parser.parseFinalLevel("/myResource.v1/abc123"))
  }
}

object PathKeyParserTest {
  case class Compound(uuid: String, version: Int)

  object Compound {
    val SEPARATOR = "@"

    private[this] val ValidRegex = ("""(.+)""" + SEPARATOR + """(\d+)""").r

    def unapply(key: StringKey): Option[(StringKey, Int)] = key.key match {
      case ValidRegex(obj, version) => Some((StringKey(obj), version.toInt))
      case _                        => None
    }

    implicit val stringKeyFormat: StringKeyFormat[Compound] = new StringKeyFormat[Compound] {
      def reads(key: StringKey) = {
        for {
          (obj, version) <- unapply(key)
        } yield {
          Compound(obj.key, version)
        }
      }

      def writes(versioned: Compound) = {
        val Compound(obj, version) = versioned
        StringKey(s"${StringKey(obj).key}$SEPARATOR$version")
      }
    }

    implicit val keyFormat: KeyFormat[Compound] = KeyFormat.idAsStringOnly
  }
}
