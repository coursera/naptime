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

import org.coursera.common.jsonformat.JsonFormats.Implicits.dateTimeFormat
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.path.ParseFailure
import org.coursera.naptime.path.ParseSuccess
import org.coursera.naptime.path.RootParsedPathKey
import org.coursera.naptime.resources.NestingTests.FriendInfoResource
import org.coursera.naptime.resources.NestingTests.PeopleResource
import org.joda.time.DateTime
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.Json
import play.api.libs.json.OFormat

object NestingTests {
  case class Person(name: String)
  object Person {
    implicit val jsonFormat: OFormat[Person] = Json.format[Person]
  }

  object PeopleResource
    extends TopLevelCollectionResource[String, Person] {

    override def keyFormat = KeyFormat.stringKeyFormat
    override implicit def resourceFormat = implicitly
    override def resourceName: String = "people"
  }

  case class FriendInfo(since: DateTime, important: Boolean)
  object FriendInfo {
    implicit val jsonFormat: OFormat[FriendInfo] = Json.format[FriendInfo]
  }

  object FriendInfoResource
    extends CollectionResource[PeopleResource.type, String, FriendInfo] {

    override def keyFormat = KeyFormat.stringKeyFormat
    override val parentResource = PeopleResource
    override implicit def resourceFormat = implicitly
    override def resourceName: String = "friendInfo"
  }
}

class NestingTests extends AssertionsForJUnit {

  @Test
  def topLevelRouting(): Unit = {
    assert(ParseSuccess(None, "asdf" ::: RootParsedPathKey) ===
      PeopleResource.pathParser.parse("/people.v1/asdf"))
    assert(ParseSuccess(Some("/friendInfo.v1/fdsa"), "asdf" ::: RootParsedPathKey) ===
      PeopleResource.pathParser.parse("/people.v1/asdf/friendInfo.v1/fdsa"))
    assert(ParseFailure === PeopleResource.pathParser.parse("/friendInfo.v1/asdf"))
  }

  @Test
  def nestedRouting(): Unit = {
    assert(ParseSuccess(None, "fdsa" ::: "asdf" ::: RootParsedPathKey) ===
      FriendInfoResource.pathParser.parse("/people.v1/asdf/friendInfo.v1/fdsa"))
    assert(ParseFailure === FriendInfoResource.pathParser.parse("/friendInfo.v1/fdsa"))
    assert(ParseFailure === FriendInfoResource.pathParser.parse("/people.v1/asdf"))
  }
}
