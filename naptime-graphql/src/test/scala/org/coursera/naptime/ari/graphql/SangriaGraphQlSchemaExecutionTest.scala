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

import org.coursera.naptime.ari.graphql.models.CoursePlatform
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.Parameter
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import sangria.execution.Executor
import sangria.parser.QueryParser
import sangria.schema.Schema
import sangria.marshalling.playJson._

import scala.concurrent.ExecutionContext.Implicits.global

class SangriaGraphQlSchemaExecutionTest extends AssertionsForJUnit with ScalaFutures {

  val courseResource = Resource(
    kind = ResourceKind.COLLECTION,
    name = "courses",
    version = Some(1),
    keyType = "",
    valueType = "",
    mergedType = "org.coursera.naptime.ari.graphql.models.MergedCourse",
    handlers = List(Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = List(Parameter(name = "id", `type` = "Integer", attributes = List.empty)),
      attributes = List.empty)),
    className = "",
    attributes = List.empty)

  val allResources = Set(courseResource)

  val schemaTypes = Map(
    "org.coursera.naptime.ari.graphql.models.MergedCourse" -> MergedCourse.SCHEMA)

  val builder = new SangriaGraphQlSchemaBuilder(allResources, schemaTypes)

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
    val course = MergedCourse(
      id = "1",
      name = "Test Course",
      slug = "test-course",
      instructors = List.empty,
      partner = 1,
      originalId = "",
      coursePlatform = List(CoursePlatform.NewPlatform))
    val context = SangriaGraphQlContext(data = Map("courses.v1" -> List(course.data())))
    val execution = Executor.execute(schema, queryAst, context).futureValue
    assert(
      (execution \ "data" \ "CoursesV1Resource" \ "get" \ "coursePlatform").get.as[List[String]]
        === List("NewPlatform"))
  }


}
