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

import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.IntegerDataSchema
import com.linkedin.data.schema.Name
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.RecordDataSchema.Field
import com.linkedin.data.schema.RecordDataSchema.RecordType
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import com.linkedin.data.schema.UnionDataSchema
import org.coursera.naptime.ari.graphql.Models
import org.mockito.Mockito.when
import sangria.schema.IntType
import sangria.schema.ObjectType
import sangria.schema.UnionType

import scala.collection.JavaConverters._

class NaptimeUnionFieldTest extends AssertionsForJUnit with MockitoSugar {

  private[this] val resourceName = "courses.v1"
  private[this]val schemaMetadata = mock[SchemaMetadata]
  private[this]val resource = Models.courseResource
  when(schemaMetadata.getResourceOpt(resourceName)).thenReturn(Some(resource))
  when(schemaMetadata.getSchema(resource)).thenReturn(Some(null))

  def buildUnionDataSchema(
      types: List[DataSchema],
      typedDefinitions: Map[String, String] = Map.empty,
      properties: Map[String, AnyRef] = Map.empty): UnionDataSchema = {
    val union = new UnionDataSchema()
    val stringBuilder = new java.lang.StringBuilder()
    union.setTypes(types.asJava, stringBuilder)
    val unionProperties = Map("typedDefinition" -> typedDefinitions.asJava.asInstanceOf[AnyRef]) ++ properties
    union.setProperties(unionProperties.asJava)
    union
  }

  private[this] def buildRecordField(name: String, fields: List[Field], namespace: String = "org.coursera.naptime") = {
    val fullName = new Name(name, namespace, new java.lang.StringBuilder())
    val recordDataSchema = new RecordDataSchema(fullName, RecordType.RECORD)
    fields.foreach(_.setRecord(recordDataSchema))
    val stringBuilder = new java.lang.StringBuilder()
    recordDataSchema.setFields(fields.asJava, stringBuilder)
    recordDataSchema
  }

  @Test
  def build_SingleElementUnion() = {
    val values = List(new IntegerDataSchema())
    val union = buildUnionDataSchema(values)
    val fieldName = "intOnlyUnion"
    val field = NaptimeUnionField.build(schemaMetadata, union, fieldName, None, resourceName)

    val expectedUnionTypes = List(
      ObjectType("courses_v1_intMember", List(
        FieldBuilder.buildPrimitiveField(fieldName, new IntegerDataSchema(), IntType))))
    val expectedField = UnionType("courses_v1_intOnlyUnion", None, expectedUnionTypes)
    assert(field.fieldType.toString === expectedField.toString)
  }

  @Test
  def build_TypedDefinitionUnion() = {
    val integerField = new Field(new IntegerDataSchema())
    integerField.setName("integerField", new java.lang.StringBuilder())
    val simpleFieldDataSchema = buildRecordField("simpleField", List(integerField))
    val complexFieldDataSchema = buildRecordField("complexField", List(integerField))

    val values = List(simpleFieldDataSchema, complexFieldDataSchema)
    val union = buildUnionDataSchema(values, Map(
      "org.coursera.naptime.simpleField" -> "easy",
      "org.coursera.naptime.complexField" -> "hard"))

    val fieldName = "typedDefinitionTestField"
    val field = NaptimeUnionField.build(schemaMetadata, union, fieldName, None, resourceName)

    val expectedUnionTypes = List(
      ObjectType("courses_v1_easyMember", List(
        NaptimeRecordField.build(
          schemaMetadata,
          simpleFieldDataSchema,
          "easy",
          Some("org.coursera.naptime"),
          resourceName))),
      ObjectType("courses_v1_hardMember", List(
        NaptimeRecordField.build(
          schemaMetadata,
          complexFieldDataSchema,
          "hard",
          Some("org.coursera.naptime"),
          resourceName))))
    val expectedField = UnionType("courses_v1_typedDefinitionTestField", None, expectedUnionTypes)
    assert(field.fieldType.toString === expectedField.toString)
  }

  @Test
  def build_ShorthandTypedDefinitionUnion_InSameNamespace() = {
    val integerField = new Field(new IntegerDataSchema())
    integerField.setName("integerField", new java.lang.StringBuilder())
    val simpleFieldDataSchema = buildRecordField("simpleField", List(integerField))
    val complexFieldDataSchema = buildRecordField("complexField", List(integerField))

    val values = List(simpleFieldDataSchema, complexFieldDataSchema)
    val union = buildUnionDataSchema(values, Map(
      "simpleField" -> "easy",
      "complexField" -> "hard"),
      Map("namespace" -> "org.coursera.naptime"))

    val fieldName = "typedDefinitionTestField"
    val field = NaptimeUnionField.build(schemaMetadata, union, fieldName, None, resourceName)

    val expectedUnionTypes = List(
      ObjectType("courses_v1_easyMember", List(
        NaptimeRecordField.build(
          schemaMetadata,
          simpleFieldDataSchema,
          "easy",
          Some("org.coursera.naptime"),
          resourceName))),
      ObjectType("courses_v1_hardMember", List(
        NaptimeRecordField.build(
          schemaMetadata,
          complexFieldDataSchema,
          "hard",
          Some("org.coursera.naptime"),
          resourceName))))
    val expectedField = UnionType("courses_v1_typedDefinitionTestField", None, expectedUnionTypes)
    assert(field.fieldType.toString === expectedField.toString)
  }

  @Test
  def build_ShorthandTypedDefinitionUnion_InDifferentNamespace() = {
    val integerField = new Field(new IntegerDataSchema())
    integerField.setName("integerField", new java.lang.StringBuilder())
    val simpleFieldDataSchema = buildRecordField("simpleField", List(integerField), "org.coursera.awaketime")
    val complexFieldDataSchema = buildRecordField("complexField", List(integerField))

    val values = List(simpleFieldDataSchema, complexFieldDataSchema)
    val union = buildUnionDataSchema(values, Map(
      "simpleField" -> "easy",
      "complexField" -> "hard"),
      Map("namespace" -> "org.coursera.naptime"))

    val fieldName = "typedDefinitionTestField"
    val field = NaptimeUnionField.build(schemaMetadata, union, fieldName, None, resourceName)

    val expectedUnionTypes = List(
      ObjectType("courses_v1_org_coursera_awaketime_simpleFieldMember", List(
        NaptimeRecordField.build(
          schemaMetadata,
          simpleFieldDataSchema,
          "easy",
          Some("org.coursera.awaketime"),
          resourceName))),
      ObjectType("courses_v1_hardMember", List(
        NaptimeRecordField.build(
          schemaMetadata,
          complexFieldDataSchema,
          "hard",
          Some("org.coursera.naptime"),
          resourceName))))
    val expectedField = UnionType("courses_v1_typedDefinitionTestField", None, expectedUnionTypes)

    assert(field.fieldType.toString === expectedField.toString)
  }

}
