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
import play.api.libs.json.JsArray
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.test.FakeRequest

import scala.collection.immutable

class SangriaGraphQlParserTest extends AssertionsForJUnit {

  val requestHeader = FakeRequest()
  val emptyVariables = Json.obj()

  @Test
  def parseEmpty(): Unit = {
    val query =
      """
        query EmptyQuery {
          root
        }
      """
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
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
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
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
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
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
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
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
            getAll {
              id
              partner {
                shortUrl: slug
              }
            }
          }
        }
      """
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
    val expectedRequest = Request(requestHeader, immutable.Seq(
      TopLevelRequest(
        ResourceName("courses", 1),
        RequestField(
          name = "getAll",
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
            getAll {
              id
            }
          }
          InstructorsV1Resource {
            multiGet {
              id
              firstName
            }
          }
        }
      """
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
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
              selections = List.empty)))),
      TopLevelRequest(
        ResourceName("instructors", 1),
        RequestField(
          name = "multiGet",
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
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
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
  def augmentGetArguments(): Unit = {
    val query =
      """
        query EmptyQuery {
          CoursesV1Resource {
            get(id: "abc123") {
              id
            }
          }
        }
      """
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
    val expectedRequest = Request(requestHeader, immutable.Seq(
      TopLevelRequest(
        ResourceName("courses", 1),
        RequestField(
          name = "get",
          alias = None,
          args = Set(("ids", JsArray(List(JsString("abc123"))))),
          selections = List(
            RequestField(
              name = "id",
              alias = None,
              args = Set.empty,
              selections = List.empty))))))
    assert(response.get === expectedRequest)
  }

  @Test
  def augmentFinderArguments(): Unit = {
    val query =
      """
        query EmptyQuery {
          CoursesV1Resource {
            slug(slug: "machine-learning") {
              id
            }
          }
        }
      """
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
    val expectedRequest = Request(requestHeader, immutable.Seq(
      TopLevelRequest(
        ResourceName("courses", 1),
        RequestField(
          name = "slug",
          alias = None,
          args = Set(
            ("slug", JsString("machine-learning")),
            ("q", JsString("slug"))),
          selections = List(
            RequestField(
              name = "id",
              alias = None,
              args = Set.empty,
              selections = List.empty))))))
    assert(response.get === expectedRequest)
  }

  @Test
  def parseFragments(): Unit = {
    val query =
      """
        query EmptyQuery {
          CoursesV1Resource {
            ...getAllFragment
          }
        }

        fragment getAllFragment on CoursesV1Resource {
          getAll(limit: 10) {
            id
          }
        }
      """
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
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
  def parseFragmentOnRoot(): Unit = {
    val query =
      """
        query EmptyQuery {
          ...CoursesV1GetAllFragment
        }

        fragment CoursesV1GetAllFragment on root {
          CoursesV1Resource {
            getAll(limit: 10) {
              id
            }
          }
        }
      """
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
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
  def parseInlineFragmentOnRoot(): Unit = {
    val query =
      """
        query EmptyQuery {
          ... on root {
            CoursesV1Resource {
              getAll(limit: 10) {
                id
              }
            }
          }
        }
      """
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
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
  def parseMissingFragment(): Unit = {
    val query =
      """
        query EmptyQuery {
          ...MissingFragment
        }
      """
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
    val expectedRequest = Request(requestHeader, immutable.Seq.empty)
    assert(response.get === expectedRequest)
  }

  @Test
  def parseMalformedQuery(): Unit = {
    val query =
      """
        query EmptyQuery {
      """
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
    val expectedRequest = Request(requestHeader, immutable.Seq.empty)
    assert(response.get === expectedRequest)
  }

  @Test
  def parseFragmentOnNestedResource(): Unit = {
    val query =
      """
        query EmptyQuery {
          CoursesV1Resource {
            getAll(limit: 10) {
              id
              instructor {
                ...InstructorFields
              }
            }
          }
        }

        fragment InstructorFields on InstructorsV1 {
          id
        }
      """
    val response = SangriaGraphQlParser.parse(query, emptyVariables, requestHeader)
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
              selections = List.empty),
            RequestField(
              name = "instructor",
              alias = None,
              args = Set.empty,
              selections = List(
                RequestField(
                  name = "id",
                  alias = None,
                  args = Set.empty,
                  selections = List.empty))))))))
    assert(response.get === expectedRequest)
  }

  @Test
  def parseVariables(): Unit = {
    val query =
      """
        query EmptyQuery($limit: Int!) {
          CoursesV1Resource {
            getAll(limit: $limit) {
              id
            }
          }
        }
      """
    val variables = Json.obj("limit" -> 20)
    val response = SangriaGraphQlParser.parse(query, variables, requestHeader)
    val expectedRequest = Request(requestHeader, immutable.Seq(
      TopLevelRequest(
        ResourceName("courses", 1),
        RequestField(
          name = "getAll",
          alias = None,
          args = Set(("limit", JsNumber(20))),
          selections = List(
            RequestField(
              name = "id",
              alias = None,
              args = Set.empty,
              selections = List.empty))))))
    assert(response.get === expectedRequest)
  }

  @Test
  def parseMissingVariables(): Unit = {
    val query =
      """
        query EmptyQuery($limit: Int!) {
          CoursesV1Resource {
            getAll(limit: $limit) {
              id
            }
          }
        }
      """
    val variables = Json.obj()
    val response = SangriaGraphQlParser.parse(query, variables, requestHeader)
    val expectedRequest = Request(requestHeader, immutable.Seq(
      TopLevelRequest(
        ResourceName("courses", 1),
        RequestField(
          name = "getAll",
          alias = None,
          args = Set(("limit", JsNull)),
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
