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

import com.linkedin.data.DataMap
import com.linkedin.data.schema.LongDataSchema
import com.linkedin.data.schema.StringDataSchema
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.helpers.ArgumentBuilder
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar
import sangria.ast.Document
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
import scala.concurrent.ExecutionContext

class PrimitiveFieldTest extends AssertionsForJUnit with MockitoSugar {

  private[this] def createContext[Val](value: Val, args: Map[String, Any] = Map("limit" -> 100))(
      implicit ctxManifest: Manifest[SangriaGraphQlContext],
      valManifest: Manifest[Val]) = {
    Context[SangriaGraphQlContext, Val](
      value = value,
      ctx = SangriaGraphQlContext(null, null, ExecutionContext.global, debugMode = false),
      args = ArgumentBuilder.buildArgs(NaptimePaginationField.paginationArguments, args),
      schema = mock[Schema[SangriaGraphQlContext, Val]],
      field = mock[Field[SangriaGraphQlContext, Val]],
      parentType = mock[ObjectType[SangriaGraphQlContext, Any]],
      marshaller = mock[ResultMarshaller],
      query = Document.emptyStub,
      sourceMapper = None,
      deprecationTracker = DeprecationTracker.empty,
      astFields = Vector.empty,
      path = ExecutionPath.empty,
      deferredResolverState = None)
  }

  val fieldName = "relatedIds"
  val invalidFieldName = "__invalidFieldName"
  val validDoubleUnderscoreName = "valid__fieldName"
  val resourceName = "courses.v1"

  @Test
  def basicParse(): Unit = {
    val stringDataSchema = new StringDataSchema()
    val field = FieldBuilder.buildPrimitiveField[String](fieldName, stringDataSchema, StringType)

    val context = createContext(
      DataMapWithParent(new DataMap(Map(fieldName -> "testString").asJava), null))
    val result = field.resolve(context).asInstanceOf[Value[SangriaGraphQlContext, String]]
    assert(result.value === "testString")
  }

  @Test
  def numberFixUp(): Unit = {
    val longDataSchema = new LongDataSchema()
    val field = FieldBuilder.buildPrimitiveField[Long](fieldName, longDataSchema, LongType)
    val context = createContext(
      DataMapWithParent(new DataMap(Map(fieldName -> new Integer(1234)).asJava), null))
    val result = field.resolve(context).asInstanceOf[Value[SangriaGraphQlContext, Long]]
    assert(result.value === 1234L)
  }

  @Test
  def invalidTypes(): Unit = {
    val longDataSchema = new LongDataSchema()
    val field = FieldBuilder.buildPrimitiveField[Long](fieldName, longDataSchema, LongType)
    val context = createContext(
      DataMapWithParent(new DataMap(Map(fieldName -> "badType").asJava), null))
    val caughtException = intercept[ResponseFormatException] {
      field.resolve(context)
    }
    assert(caughtException.msg === "relatedIds could not be fixed-up or parsed")
  }

  @Test
  def optionalField(): Unit = {
    val longDataSchema = new LongDataSchema()
    val field = FieldBuilder.buildPrimitiveField[Long](fieldName, longDataSchema, LongType)
    val context = createContext(DataMapWithParent(new DataMap(Map.empty.asJava), null))
    val result = field.resolve(context).asInstanceOf[Value[SangriaGraphQlContext, Any]]
    assert(result.value === null)
  }

  @Test
  def fieldBeginningWithDoubleUnderscore(): Unit = {
    val longDataSchema = new LongDataSchema()
    val field = FieldBuilder.buildPrimitiveField[Long](invalidFieldName, longDataSchema, LongType)
    assert(field.name === "_invalidFieldName")
  }

  @Test
  def fieldWithInnerDoubleUnderscore(): Unit = {
    val longDataSchema = new LongDataSchema()
    val field =
      FieldBuilder.buildPrimitiveField[Long](validDoubleUnderscoreName, longDataSchema, LongType)
    assert(field.name === validDoubleUnderscoreName)
  }

}
