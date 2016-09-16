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

package org.coursera.naptime.ari.graphql

import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.TopLevelRequest
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.JsNumber
import play.api.test.FakeRequest

import scala.collection.immutable

class SangriaGraphQlParserTest extends AssertionsForJUnit {

  val requestHeader = FakeRequest()

  @Test
  def parseEmpty(): Unit = {
    val query =
      """
        query EmptyQuery {
          root
        }
      """
    val response = SangriaGraphQlParser.parse(query, requestHeader)
    assert(response.get === Request(requestHeader, immutable.Seq.empty))
  }

  @Test
  def parseSimple(): Unit = {
    val query =
      """
        query EmptyQuery {
          CoursesV1Resource
        }
      """
    val response = SangriaGraphQlParser.parse(query, requestHeader)
    assert(response.get === Request(requestHeader, immutable.Seq.empty))
  }

  @Test
  def parseFields(): Unit = {
    val query =
      """
        query MyQuery {
          CoursesV1Resource {
            getAll {
              id
            }
          }
        }
      """
    val response = SangriaGraphQlParser.parse(query, requestHeader)
    val expectedRequest = Request(requestHeader, immutable.Seq(
      TopLevelRequest(
        ResourceName("courses", 1),
        RequestField(
          name = "getAll",
          alias = None,
          args = Set.empty,
          selections = List(
            RequestField(
              name = "id",
              alias = None,
              args = Set.empty,
              selections = List.empty))))))
    assert(response.get === expectedRequest)
  }

  @Test
  def parseAliases(): Unit = {
    val query =
      """
        query MyRenamedQuery {
          course: CoursesV1Resource {
            allCourses: getAll {
              myId: id
            }
          }
        }
      """
    val response = SangriaGraphQlParser.parse(query, requestHeader)
    val expectedRequest = Request(requestHeader, immutable.Seq(
      TopLevelRequest(
        ResourceName("courses", 1),
        RequestField(
          name = "getAll",
          alias = Some("allCourses"),
          args = Set.empty,
          selections = List(
            RequestField(
              name = "id",
              alias = Some("myId"),
              args = Set.empty,
              selections = List.empty))),
        alias = Some("course"))))
    assert(response.get === expectedRequest)
  }

  @Test
  def parseDeeplyNested(): Unit = {
    val query =
      """
        query DeeplyNestedQuery {
          courses: CoursesV1Resource {
            myCourses {
              id
              partner {
                shortUrl: slug
              }
            }
          }
        }
      """
    val response = SangriaGraphQlParser.parse(query, requestHeader)
    val expectedRequest = Request(requestHeader, immutable.Seq(
      TopLevelRequest(
        ResourceName("courses", 1),
        RequestField(
          name = "myCourses",
          alias = None,
          args = Set.empty,
          selections = List(
            RequestField(name = "id", alias = None, args = Set.empty, selections = List.empty),
            RequestField(
              name = "partner",
              alias = None,
              args = Set.empty,
              selections = List(
                RequestField(
                  name = "slug",
                  alias = Some("shortUrl"),
                  args = Set.empty,
                  selections = List.empty))))),
        alias = Some("courses"))))
    assert(response.get === expectedRequest)
  }

  @Test
  def parseMulti(): Unit = {
    val query =
      """
        query EmptyQuery {
          CoursesV1Resource {
            myCourses {
              id
            }
          }
          InstructorsV1Resource {
            favorites {
              id
              firstName
            }
          }
        }
      """
    val response = SangriaGraphQlParser.parse(query, requestHeader)
    val expectedRequest = Request(requestHeader, immutable.Seq(
      TopLevelRequest(
        ResourceName("courses", 1),
        RequestField(
          name = "myCourses",
          alias = None,
          args = Set.empty,
          selections = List(
            RequestField(
              name = "id",
              alias = None,
              args = Set.empty,
              selections = List.empty)))),
      TopLevelRequest(
        ResourceName("instructors", 1),
        RequestField(
          name = "favorites",
          alias = None,
          args = Set.empty,
          selections = List(
            RequestField(
              name = "id",
              alias = None,
              args = Set.empty,
              selections = List.empty),
            RequestField(
              name = "firstName",
              alias = None,
              args = Set.empty,
              selections = List.empty))))))
    assert(response.get === expectedRequest)
  }

  @Test
  def parseArguments(): Unit = {
    val query =
      """
        query EmptyQuery {
          CoursesV1Resource {
            getAll(limit: 10) {
              id
            }
          }
        }
      """
    val response = SangriaGraphQlParser.parse(query, requestHeader)
    val expectedRequest = Request(requestHeader, immutable.Seq(
      TopLevelRequest(
        ResourceName("courses", 1),
        RequestField(
          name = "getAll",
          alias = None,
          args = Set(("limit", JsNumber(10))),
          selections = List(
            RequestField(
              name = "id",
              alias = None,
              args = Set.empty,
              selections = List.empty))))))
    assert(response.get === expectedRequest)
  }

  @Test
  def resourceNameParse(): Unit = {
    assert(SangriaGraphQlParser.fieldNameToNaptimeResource("CoursesV0Resource") ===
      Some(ResourceName("courses", 0)))
    assert(SangriaGraphQlParser.fieldNameToNaptimeResource("V1DetailsV1Resource") ===
      Some(ResourceName("v1Details", 1)))
    assert(SangriaGraphQlParser.fieldNameToNaptimeResource("Courses.V0") === None)
    assert(SangriaGraphQlParser.fieldNameToNaptimeResource("Courses") === None)
    assert(SangriaGraphQlParser.fieldNameToNaptimeResource("CoursesV") === None)
    assert(SangriaGraphQlParser.fieldNameToNaptimeResource("V0Resource") === None)
    assert(SangriaGraphQlParser.fieldNameToNaptimeResource("?V0") === None)
  }

}
