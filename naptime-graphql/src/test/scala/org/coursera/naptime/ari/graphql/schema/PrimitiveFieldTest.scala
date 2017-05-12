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

package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.schema.LongDataSchema
import com.linkedin.data.schema.StringDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.ari.TopLevelResponse
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.helpers.ArgumentBuilder
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import sangria.execution.DeprecationTracker
import sangria.execution.ExecutionPath
import sangria.marshalling.ResultMarshaller
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.LongType
import sangria.schema.ObjectType
import sangria.schema.Schema
import sangria.schema.StringType
import sangria.schema.Value

import scala.collection.JavaConverters._

class PrimitiveFieldTest extends AssertionsForJUnit with MockitoSugar {

  def createContext[Ctx, Val](
      ctx: Ctx,
      value: Val,
      args: Map[String, Any] = Map("limit" -> 100))
      (implicit ctxManifest: Manifest[Ctx], valManifest: Manifest[Val]) = {
    Context[Ctx, Val](
      value = value,
      ctx = ctx,
      args = ArgumentBuilder.buildArgs(NaptimePaginationField.paginationArguments, args),
      schema = mock[Schema[Ctx, Val]],
      field = mock[Field[Ctx, Val]],
      parentType = mock[ObjectType[Ctx, Any]],
      marshaller = mock[ResultMarshaller],
      sourceMapper = None,
      deprecationTracker = DeprecationTracker.empty,
      astFields = Vector.empty,
      path = ExecutionPath.empty,
      deferredResolverState = None)
  }

  val fieldName = "relatedIds"
  val resourceName = "courses.v1"
  val resourceContext = SangriaGraphQlContext(Response(
    Map(TopLevelRequest(
      ResourceName.parse(resourceName).get,
      RequestField("", None, Set.empty, List.empty)) ->
    TopLevelResponse(
      ids = new DataList(List("1").asJava),
      pagination = ResponsePagination(None))),
    Map(ResourceName.parse(resourceName).get -> Map("1" -> new DataMap()))))

  @Test
  def basicParse(): Unit = {
    val stringDataSchema = new StringDataSchema()
    val field = FieldBuilder.buildPrimitiveField[String](fieldName, stringDataSchema, StringType)
    val graphqlContext = SangriaGraphQlContext(Response.empty)
    val context = createContext(graphqlContext, new DataMap(Map(fieldName -> "testString").asJava))
    val result = field.resolve(context).asInstanceOf[Value[SangriaGraphQlContext, String]]
    assert(result.value === "testString")
  }

  @Test
  def numberFixUp(): Unit = {
    val longDataSchema = new LongDataSchema()
    val field = FieldBuilder.buildPrimitiveField[Long](fieldName, longDataSchema, LongType)
    val graphqlContext = SangriaGraphQlContext(Response.empty)
    val context = createContext(graphqlContext, new DataMap(Map(fieldName -> new Integer(1234)).asJava))
    val result = field.resolve(context).asInstanceOf[Value[SangriaGraphQlContext, Long]]
    assert(result.value === 1234L)
  }

  @Test
  def invalidTypes(): Unit = {
    val longDataSchema = new LongDataSchema()
    val field = FieldBuilder.buildPrimitiveField[Long](fieldName, longDataSchema, LongType)
    val graphqlContext = SangriaGraphQlContext(Response.empty)
    val context = createContext(graphqlContext, new DataMap(Map(fieldName -> "badType").asJava))
    val caughtException = intercept[ResponseFormatException] {
      field.resolve(context)
    }
    assert(caughtException.msg === "relatedIds could not be fixed-up or parsed")
  }

  @Test
  def optionalField(): Unit = {
    val longDataSchema = new LongDataSchema()
    val field = FieldBuilder.buildPrimitiveField[Long](fieldName, longDataSchema, LongType)
    val graphqlContext = SangriaGraphQlContext(Response.empty)
    val context = createContext(graphqlContext, new DataMap(Map.empty.asJava))
    val result = field.resolve(context).asInstanceOf[Value[SangriaGraphQlContext, Any]]
    assert(result.value === null)
  }

}
