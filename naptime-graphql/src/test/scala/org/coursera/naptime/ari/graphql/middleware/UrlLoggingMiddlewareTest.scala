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

package org.coursera.naptime.ari.graphql.middleware

import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.controllers.middleware.ResponseMetadataMiddleware
import org.coursera.naptime.ari.graphql.marshaller.NaptimeMarshaller._
import org.coursera.naptime.ari.graphql.resolvers.NaptimeResponse
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.Headers
import play.api.test.FakeRequest
import sangria.ast.Document
import sangria.execution.DeprecationTracker
import sangria.execution.ExecutionPath
import sangria.execution.MiddlewareQueryContext
import sangria.execution.ResultResolver
import sangria.execution.TimeMeasurement
import sangria.macros._
import sangria.marshalling.ResultMarshaller
import sangria.schema.Args
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.Schema

import scala.concurrent.ExecutionContext

class UrlLoggingMiddlewareTest extends AssertionsForJUnit with MockitoSugar {

  private[this] val mockAst = graphql"""
    schema {
      query: Root
    }

    type Root {
      course: Course
    }

    type Course {
      slug: String
      instructor: Instructor
    }

    type Instructor {
      name: String
    }
  """

  private[this] val mockSchema: Schema[SangriaGraphQlContext, Any] =
    Schema.buildFromAst(mockAst).asInstanceOf[Schema[SangriaGraphQlContext, Any]]

  private[this] val rootType = mockSchema.query

  private[this] val courseField = Field[SangriaGraphQlContext, Any, Any, Any](
    name = "course",
    fieldType = mockSchema.outputTypes("Course"),
    description = None,
    arguments = List.empty,
    resolve = _ => null)

  def buildContext(
      ctx: SangriaGraphQlContext,
      executionPath: ExecutionPath): Context[SangriaGraphQlContext, _] = {
    Context[SangriaGraphQlContext, Any](
      value = null,
      ctx = ctx,
      args = Args.empty,
      schema = mockSchema,
      field = courseField,
      parentType = mock[ObjectType[SangriaGraphQlContext, Any]],
      marshaller = mock[ResultMarshaller],
      query = Document.emptyStub,
      sourceMapper = None,
      deprecationTracker = DeprecationTracker.empty,
      astFields = Vector.empty,
      path = executionPath,
      deferredResolverState = None)

  }

  def buildMiddlewareQueryContext(
      ctx: SangriaGraphQlContext): MiddlewareQueryContext[SangriaGraphQlContext, _, _] = {
    MiddlewareQueryContext[SangriaGraphQlContext, Any, Unit](
      ctx = ctx,
      executor = null,
      queryAst = Document(Vector.empty),
      operationName = None,
      variables = (),
      inputUnmarshaller = null,
      validationTiming = TimeMeasurement.empty,
      queryReducerTiming = TimeMeasurement.empty)
  }

  @Test
  def urlsNotRecordWhenNotDebugging(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val request = FakeRequest(method = "GET", uri = "/graphql", headers = Headers(), body = "")
    val ctx = SangriaGraphQlContext(null, request, ExecutionContext.global, debugMode = false)
    val middlewareQueryContext = buildMiddlewareQueryContext(ctx)
    val astField = sangria.ast.Field(None, "course", Vector.empty, Vector.empty, Vector.empty)
    val path = ExecutionPath.empty.add(astField, rootType)
    val context = buildContext(ctx, path)
    middleware.afterField(
      queryVal = (),
      fieldVal = (),
      value = NaptimeResponse(List.empty, None, "test url"),
      mctx = middlewareQueryContext,
      ctx = context)
    val extensions = middleware.afterQueryExtensions((), middlewareQueryContext)
    assert(extensions.isEmpty)
  }

  @Test
  def urlsRecordedAfterEachField(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val request = FakeRequest(method = "GET", uri = "/graphql", headers = Headers(), body = "")
    val ctx = SangriaGraphQlContext(null, request, ExecutionContext.global, debugMode = true)
    val middlewareQueryContext = buildMiddlewareQueryContext(ctx)
    val astField = sangria.ast.Field(None, "course", Vector.empty, Vector.empty, Vector.empty)
    val path = ExecutionPath.empty.add(astField, rootType)
    val context = buildContext(ctx, path)
    middleware.afterField(
      queryVal = (),
      fieldVal = (),
      value = NaptimeResponse(List.empty, None, "test url"),
      mctx = middlewareQueryContext,
      ctx = context)
    val extensions = middleware.afterQueryExtensions((), middlewareQueryContext)
    val marshalled =
      ResultResolver.marshalExtensions(PlayJsonMarshallerForType.marshaller, extensions).get
    val expected =
      Json.obj(
        "responseMetadata" -> Json.obj(
          "course" -> Json.obj("sourceUrl" -> "test url", "statusCode" -> 200)))
    assert(marshalled === expected)

  }
}
