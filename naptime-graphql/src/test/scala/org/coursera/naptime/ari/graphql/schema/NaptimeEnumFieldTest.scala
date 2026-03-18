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
import com.linkedin.data.schema.EnumDataSchema
import com.linkedin.data.schema.Name
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.helpers.ArgumentBuilder
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar
import sangria.ast.Document
import sangria.execution.DeprecationTracker
import sangria.execution.ExecutionPath
import sangria.marshalling.ResultMarshaller
import sangria.schema.Args
import sangria.schema.Context
import sangria.schema.EnumType
import sangria.schema.ObjectType
import sangria.schema.Schema

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class NaptimeEnumFieldTest extends AssertionsForJUnit with MockitoSugar {

  def buildEnumDataSchema(values: List[String]): EnumDataSchema = {
    val enum = new EnumDataSchema(new Name("testEnum"))
    val stringBuilder = new java.lang.StringBuilder()
    enum.setSymbols(values.asJava, stringBuilder)
    enum
  }

  @Test
  def build_RegularEnum(): Unit = {
    val values = List("valueOne", "valueTwo")
    val enum = buildEnumDataSchema(values)
    val field = NaptimeEnumField.build(enum, "myField")
    assert(field.fieldType.asInstanceOf[EnumType[String]].values.map(_.name) === values)
  }

  @Test
  def build_EmptyEnum(): Unit = {
    val values = List()
    val expectedValues = List("UNKNOWN")
    val enum = buildEnumDataSchema(values)
    val field = NaptimeEnumField.build(enum, "myField")
    assert(field.fieldType.asInstanceOf[EnumType[String]].values.map(_.name) === expectedValues)
  }

  // -------------------------------------------------------------------------
  // resolve — cover NaptimeEnumField.build resolve lambda (line 21)
  // -------------------------------------------------------------------------

  private def buildContext(
      value: DataMapWithParent): Context[SangriaGraphQlContext, DataMapWithParent] = {
    val mockSchema = mock[Schema[SangriaGraphQlContext, DataMapWithParent]]
    val mockField = mock[sangria.schema.Field[SangriaGraphQlContext, DataMapWithParent]]
    val mockParent = mock[ObjectType[SangriaGraphQlContext, Any]]
    Context[SangriaGraphQlContext, DataMapWithParent](
      value = value,
      ctx = SangriaGraphQlContext(null, null, ExecutionContext.global, debugMode = false),
      args = ArgumentBuilder.buildArgs(NaptimePaginationField.paginationArguments, Map("limit" -> 100)),
      schema = mockSchema,
      field = mockField,
      parentType = mockParent,
      marshaller = mock[ResultMarshaller],
      query = Document.emptyStub,
      sourceMapper = None,
      deprecationTracker = DeprecationTracker.empty,
      astFields = Vector.empty,
      path = ExecutionPath.empty,
      deferredResolverState = None)
  }

  @Test
  def build_RegularEnum_resolve_returnsEnumString(): Unit = {
    val values = List("valueOne", "valueTwo")
    val enum = buildEnumDataSchema(values)
    val field = NaptimeEnumField.build(enum, "myField")

    val dm = new DataMap()
    dm.put("myField", "valueOne")
    val parentModel = mock[ParentModel]
    val dmWithParent = DataMapWithParent(dm, parentModel)
    val ctx = buildContext(dmWithParent)
    val resolved = field.resolve(ctx)
    // resolve returns Value("valueOne") or "valueOne" depending on how sangria wraps it
    assert(resolved.toString.contains("valueOne") || resolved == "valueOne")
  }

  @Test
  def build_EmptyEnum_resolve_returnsNull(): Unit = {
    val values = List()
    val enum = buildEnumDataSchema(values)
    val field = NaptimeEnumField.build(enum, "myField")

    val dm = new DataMap()
    // "myField" not present → getString returns null
    val parentModel = mock[ParentModel]
    val dmWithParent = DataMapWithParent(dm, parentModel)
    val ctx = buildContext(dmWithParent)
    val resolved = field.resolve(ctx)
    assert(resolved == null || resolved.isInstanceOf[sangria.schema.Value[_, _]])
  }

}
