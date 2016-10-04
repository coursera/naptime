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

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.ari.graphql.marshaller.NaptimeMarshaller._
import org.coursera.naptime.ari.graphql.models.AnyData
import org.coursera.naptime.ari.graphql.models.CoursePlatform
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.Parameter
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import sangria.execution.Executor
import sangria.parser.QueryParser
import sangria.schema.Schema

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

class SangriaGraphQlSchemaExecutionTest extends AssertionsForJUnit with ScalaFutures {

  val courseResource = Resource(
    kind = ResourceKind.COLLECTION,
    name = "courses",
    version = Some(1),
    keyType = "",
    valueType = "",
    mergedType = "org.coursera.naptime.ari.graphql.models.MergedCourse",
    handlers = List(
      Handler(
        kind = HandlerKind.GET,
        name = "get",
        parameters = List(Parameter(name = "id", `type` = "Integer", attributes = List.empty)),
        attributes = List.empty),
      Handler(
        kind = HandlerKind.MULTI_GET,
        name = "multiGet",
        parameters = List(Parameter(name = "ids", `type` = "List[Integer]", attributes = List.empty)),
        attributes = List.empty)),
    className = "",
    attributes = List.empty)

  val allResources = Set(courseResource)

  val schemaTypes = Map(
    "org.coursera.naptime.ari.graphql.models.MergedCourse" -> MergedCourse.SCHEMA)

  val builder = new SangriaGraphQlSchemaBuilder(allResources, schemaTypes)

  val courseOne = MergedCourse(
    id = "1",
    name = "Test Course",
    slug = "test-course",
    instructors = List.empty,
    partner = 1,
    originalId = "",
    coursePlatform = List(CoursePlatform.NewPlatform),
    arbitraryData = AnyData(new DataMap(
      Map("moduleIds" ->
        new DataMap(Map("moduleOne" -> "abc", "moduleTwo" -> "defg").asJava))
        .asJava),
      DataConversion.SetReadOnly))
  val courseTwo = MergedCourse(
    id = "2",
    name = "Test Course 2",
    slug = "test-course-2",
    instructors = List.empty,
    partner = 1,
    originalId = "",
    coursePlatform = List(CoursePlatform.NewPlatform),
    arbitraryData = AnyData(new DataMap(), DataConversion.SetReadOnly))


  @Test
  def parseComplexLists(): Unit = {
    val schema = builder.generateSchema().asInstanceOf[Schema[SangriaGraphQlContext, Any]]
    val query =
      """
      query {
        CoursesV1Resource {
          get(id: "1") {
            coursePlatform
          }
        }
      }
      """.stripMargin
    val queryAst = QueryParser.parse(query).get

    val topLevelRequest = TopLevelRequest(
      resource = ResourceName("courses", 1),
      selection = RequestField(
          name = "get",
          alias = None,
          args = Set(("id", JsString("1"))),
          selections = List(
            RequestField(
              name = "coursePlatform",
              alias = None,
              args = Set.empty,
              selections = List.empty))))
    val response = Response(
      topLevelIds = Map(topLevelRequest -> new DataList(List("1").asJava)),
      data = Map(ResourceName("courses", 1) -> Map("1" -> courseOne.data())))
    val context = SangriaGraphQlContext(response)
    val execution = Executor.execute(schema, queryAst, context).futureValue
    assert(
      (execution \ "data" \ "CoursesV1Resource" \ "get" \ "coursePlatform").get.as[List[String]]
        === List("NewPlatform"))
  }

  @Test
  def parseAliases(): Unit = {
    val schema = builder.generateSchema().asInstanceOf[Schema[SangriaGraphQlContext, Any]]
    val query =
      """
      query {
        courseContainer: CoursesV1Resource {
          coursesById: multiGet(ids: ["1", "2"]) {
            edges {
              node {
                coursePlatform
              }
            }
          }
        }
      }
      """.stripMargin
    val queryAst = QueryParser.parse(query).get

    val topLevelRequest = TopLevelRequest(
      resource = ResourceName("courses", 1),
      selection = RequestField(
        name = "multiGet",
        alias = Some("coursesById"),
        args = Set(("ids", JsArray(List(JsString("1"), JsString("2"))))),
        selections = List(
          RequestField(
            name = "coursePlatform",
            alias = None,
            args = Set.empty,
            selections = List.empty))),
      alias = Some("courseContainer"))
    val response = Response(
      topLevelIds = Map(topLevelRequest -> new DataList(List("1").asJava)),
      data = Map(ResourceName("courses", 1) -> Map(
        "1" -> courseOne.data(),
        "2" -> courseTwo.data())))
    val context = SangriaGraphQlContext(response)
    val execution = Executor.execute(schema, queryAst, context).futureValue
    assert(
      ((execution \ "data" \ "courseContainer" \ "coursesById" \ "edges" \\ "node").head
        \ "coursePlatform").get.as[List[String]] === List("NewPlatform"))
  }

  @Test
  def parseDataMapTypes(): Unit = {
    val schema = builder.generateSchema().asInstanceOf[Schema[SangriaGraphQlContext, Any]]
    val query =
      """
      query {
        CoursesV1Resource {
          get(id: "1") {
            arbitraryData
          }
        }
      }
      """.stripMargin
    val queryAst = QueryParser.parse(query).get

    val topLevelRequest = TopLevelRequest(
      resource = ResourceName("courses", 1),
      selection = RequestField(
        name = "get",
        alias = None,
        args = Set(("id", JsString("1"))),
        selections = List(
          RequestField(
            name = "arbitraryData",
            alias = None,
            args = Set.empty,
            selections = List.empty))))
    val response = Response(
      topLevelIds = Map(topLevelRequest -> new DataList(List("1").asJava)),
      data = Map(ResourceName("courses", 1) -> Map("1" -> courseOne.data())))
    val context = SangriaGraphQlContext(response)
    val execution = Executor.execute(schema, queryAst, context).futureValue
    assert(
      (execution \ "data" \ "CoursesV1Resource" \ "get" \ "arbitraryData").get.as[Map[String, Map[String, String]]]
        === Map("moduleIds" -> Map("moduleOne" -> "abc", "moduleTwo" -> "defg")))
  }

}
