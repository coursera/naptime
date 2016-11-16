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
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.ari.TopLevelResponse
import org.coursera.naptime.ari.graphql.marshaller.NaptimeMarshaller._
import org.coursera.naptime.ari.graphql.models.AnyData
import org.coursera.naptime.ari.graphql.models.CoursePlatform
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedCourse.PlatformSpecificData.OldPlatformDataMember
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.coursera.naptime.ari.graphql.models.OldPlatformData
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.JsArray
import play.api.libs.json.JsNull
import play.api.libs.json.JsString
import play.api.libs.json.Json
import sangria.execution.Executor
import sangria.execution.QueryReducer
import sangria.parser.QueryParser
import sangria.schema.Schema

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

class SangriaGraphQlSchemaExecutionTest extends AssertionsForJUnit with ScalaFutures {

  val allResources = Set(Models.courseResource, Models.instructorResource, Models.partnersResource)

  val schemaTypes = Map(
    "org.coursera.naptime.ari.graphql.models.MergedCourse" -> MergedCourse.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedPartner" -> MergedPartner.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedInstructor" -> MergedInstructor.SCHEMA)

  val builder = new SangriaGraphQlSchemaBuilder(allResources, schemaTypes)

  val courseOne = MergedCourse(
    id = "1",
    name = "Test Course",
    slug = "test-course",
    instructors = List.empty,
    partner = 1,
    originalId = "",
    platformSpecificData = OldPlatformDataMember(OldPlatformData("Not Available.")),
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
    platformSpecificData = OldPlatformDataMember(OldPlatformData("Not Available.")),
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
      topLevelResponses = Map(topLevelRequest ->
        TopLevelResponse(new DataList(List("1").asJava), ResponsePagination.empty)),
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
            elements {
              coursePlatform
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
            name = "elements",
            alias = None,
            args = Set.empty,
            selections = List(RequestField(
              name = "coursePlatform",
              alias = None,
              args = Set.empty,
              selections = List.empty))))),
      alias = Some("courseContainer"))
    val response = Response(
      topLevelResponses = Map(topLevelRequest ->
        TopLevelResponse(new DataList(List("1").asJava), ResponsePagination.empty)),
      data = Map(ResourceName("courses", 1) -> Map(
        "1" -> courseOne.data(),
        "2" -> courseTwo.data())))
    val context = SangriaGraphQlContext(response)
    val execution = Executor.execute(schema, queryAst, context).futureValue
    assert(
      ((execution \ "data" \ "courseContainer" \ "coursesById" \ "elements").head
        \ "coursePlatform").get.as[List[String]] === List("NewPlatform"))
  }

  @Test
  def parseUnions(): Unit = {
    val schema = builder.generateSchema().asInstanceOf[Schema[SangriaGraphQlContext, Any]]
    val query =
      """
      query {
        CoursesV1Resource {
          get(id: "1") {
            platformSpecificData {
              ... on org_coursera_naptime_ari_graphql_models_OldPlatformDataMember {
                org_coursera_naptime_ari_graphql_models_OldPlatformData {
                  notAvailableMessage
                }
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
        name = "get",
        alias = None,
        args = Set(("id", JsString("1"))),
        selections = List.empty))
    val response = Response(
      topLevelResponses = Map(topLevelRequest ->
        TopLevelResponse(new DataList(List("1").asJava), ResponsePagination.empty)),
      data = Map(ResourceName("courses", 1) -> Map(
        "1" -> courseOne.data())))
    val context = SangriaGraphQlContext(response)
    val execution = Executor.execute(schema, queryAst, context).futureValue
    assert(
      (execution \ "data" \ "CoursesV1Resource" \ "get" \ "platformSpecificData" \
        "org_coursera_naptime_ari_graphql_models_OldPlatformData" \ "notAvailableMessage")
        .get.as[String] === "Not Available.")
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
      topLevelResponses = Map(topLevelRequest ->
        TopLevelResponse(new DataList(List("1").asJava), ResponsePagination.empty)),
      data = Map(ResourceName("courses", 1) -> Map("1" -> courseOne.data())))
    val context = SangriaGraphQlContext(response)
    val execution = Executor.execute(schema, queryAst, context).futureValue
    assert(
      (execution \ "data" \ "CoursesV1Resource" \ "get" \ "arbitraryData").get.as[Map[String, Map[String, String]]]
        === Map("moduleIds" -> Map("moduleOne" -> "abc", "moduleTwo" -> "defg")))
  }

  @Test
  def errorHandling_get_404(): Unit = {
    val schema = builder.generateSchema().asInstanceOf[Schema[SangriaGraphQlContext, Any]]
    val query =
      """
      query {
        CoursesV1Resource {
          idOne: get(id: "1") {
            id
          }
          idTwo: get(id: "2") {
            id
          }
        }
      }
      """.stripMargin
    val queryAst = QueryParser.parse(query).get

    val topLevelRequestOne = TopLevelRequest(
      resource = ResourceName("courses", 1),
      selection = RequestField(
        name = "get",
        alias = Some("idOne"),
        args = Set(("id", JsString("1"))),
        selections = List(
          RequestField(
            name = "id",
            alias = None,
            args = Set.empty,
            selections = List.empty))))
    val response = Response(
      topLevelResponses = Map(topLevelRequestOne ->
        TopLevelResponse(new DataList(List("1").asJava), ResponsePagination.empty)),
      data = Map(ResourceName("courses", 1) -> Map("1" -> courseOne.data())))
    val context = SangriaGraphQlContext(response)
    val execution = Executor.execute(schema, queryAst, context).futureValue

    assert((execution \ "data" \ "CoursesV1Resource" \ "idOne" \ "id").as[String] === "1")

    assert((execution \ "data" \ "CoursesV1Resource" \ "idTwo").get === JsNull)

    assert((execution \ "errors" \\ "message").head.as[String] === "Cannot find courses.v1/2")
  }

  @Test
  def complexityCalculation(): Unit = {
    val schema = builder.generateSchema().asInstanceOf[Schema[SangriaGraphQlContext, Any]]
    val query =
      """
      query {
        CoursesV1Resource {             # 421 complexity points
          getAll(limit: 20) {           # 420 [2 * 10 * 21] complexity points
            elements {                  # 21 complexity points
              instructors(limit: 5) {   # 20 [1 * 10 * 2] complexity points
                elements {              # 2 complexity points
                  id                    # 1 complexity point
                }
              }
            }
          }
        }
      }
      """.stripMargin
    val queryAst = QueryParser.parse(query).get

    var complexity = 0D
    val complReducer = QueryReducer.measureComplexity[SangriaGraphQlContext] { (c, ctx) ⇒
      complexity = c
      ctx
    }
    Executor.execute(
      schema,
      queryAst,
      SangriaGraphQlContext(Response.empty),
      variables = Json.obj(),
      queryReducers = List(complReducer)).futureValue

    assert(complexity === 421.0D)
  }

}
