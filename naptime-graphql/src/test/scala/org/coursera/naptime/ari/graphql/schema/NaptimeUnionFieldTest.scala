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
import org.scalatest.mockito.MockitoSugar
import com.linkedin.data.schema.UnionDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.Models
import org.mockito.Mockito.when
import sangria.schema.IntType
import sangria.schema.ObjectType
import sangria.schema.OutputType
import sangria.schema.UnionType

import scala.collection.JavaConverters._

class NaptimeUnionFieldTest extends AssertionsForJUnit with MockitoSugar {

  private[this] val resourceName = ResourceName("courses", 1)
  private[this] val schemaMetadata = mock[SchemaMetadata]
  private[this] val resource = Models.courseResource
  when(schemaMetadata.getResourceOpt(resourceName)).thenReturn(Some(resource))
  when(schemaMetadata.getSchema(resource)).thenReturn(Some(null))

  def buildUnionDataSchema(
      types: List[DataSchema],
      typedDefinitions: Map[String, String] = Map.empty,
      properties: Map[String, AnyRef] = Map.empty): UnionDataSchema = {
    val union = new UnionDataSchema()
    val stringBuilder = new java.lang.StringBuilder()
    union.setTypes(types.asJava, stringBuilder)
    val unionProperties =
      Map("typedDefinition" -> typedDefinitions.asJava.asInstanceOf[AnyRef]) ++ properties
    union.setProperties(unionProperties.asJava)
    union
  }

  private[this] def buildRecordField(
      name: String,
      fields: List[Field],
      namespace: String = "org.coursera.naptime") = {
    val fullName = new Name(name, namespace, new java.lang.StringBuilder())
    val recordDataSchema = new RecordDataSchema(fullName, RecordType.RECORD)
    fields.foreach(_.setRecord(recordDataSchema))
    val stringBuilder = new java.lang.StringBuilder()
    recordDataSchema.setFields(fields.asJava, stringBuilder)
    recordDataSchema
  }

  private def assertComputedIsExpectedUnionTypeUsingNames(
      computed: OutputType[_],
      expected: UnionType[_]): Unit = {

    /**
     * It looks like it is very hard to assert equality of Sangria `Types`, so for the case we care
     * about [union of object members], we will check that the computed type contains the same
     * object fields, and each object field contains the same names.
     *
     * Cannot check for object equality because `ObjectType` constructor takes a list and creates a function.
     * We previously checked .toString equality, which is incorrect. It also didn't check field names at all...
     *
     * To wit:
     * scala> case class A(f: () => Int)
     * defined class A
     *
     * scala> A(() =>2) == A(() => 2)
     * res1: Boolean = false
     *
     * scala> def randomInt(): Int = {new scala.util.Random().nextInt()}
     * randomInt: ()Int
     *
     * scala> A(randomInt).toString == A(randomInt).toString
     * res2: Boolean = true
     *
     */
    // 1. Assert computed `OutputType` is a union type, sort the union members by name for stability
    val computedObjectTypes = computed match {
      case (computedUnion: UnionType[_]) => computedUnion.types.sortBy(_.name)
      case _ =>
        fail(
          s"assertUnionType should have gotten a computed `UnionType`, but got $computed instead")
    }
    val expectedObjectTypes = expected.types.sortBy(_.name)

    assert(
      computedObjectTypes.map(_.name) == expectedObjectTypes.map(_.name),
      s"Different union members between computed and expected!")

    // 2. For each member, check that all the fields' names match up.
    computedObjectTypes.zip(expectedObjectTypes).foreach {
      case (computedObject, expectedObject) =>
        assert(
          computedObject.ownFields.map(_.name) === expectedObject.ownFields.map(_.name),
          s"Found a field mismatch for union member: ${computedObject.name}")
    }
  }

  @Test
  def build_SingleElementUnion(): Unit = {
    val fieldName = "intOnlyUnion"
    val integerField = new IntegerDataSchema()

    val values = List(integerField)
    val union = buildUnionDataSchema(values)
    val field =
      NaptimeUnionField.build(schemaMetadata, union, fieldName, None, resourceName, List.empty)

    val expectedUnionTypes = List(
      ObjectType(
        "CoursesV1_intMember",
        List(FieldBuilder
          .buildPrimitiveField(integerField.getUnionMemberKey, new IntegerDataSchema(), IntType))))
    val expectedField = UnionType("CoursesV1_intOnlyUnion", None, expectedUnionTypes)

    assertComputedIsExpectedUnionTypeUsingNames(field.fieldType, expectedField)
  }

  @Test
  def build_TypedDefinitionUnion(): Unit = {

    /**
     * The goal of this test is to show typed definition renaming in Courier works with our type
     * computations.
     * The typed definition format looks like https://github.com/coursera/courier/blob/9e739f6d99b11e3cf18ca3cd774c6c506aa53597/reference-suite/src/main/courier/org/coursera/typerefs/TypedDefinition.courier#L6
     */
    val integerField = new Field(new IntegerDataSchema())
    integerField.setName("integerField", new java.lang.StringBuilder())
    val simpleFieldDataSchema = buildRecordField("simpleField", List(integerField))
    val complexFieldDataSchema = buildRecordField("complexField", List(integerField))

    val values = List(simpleFieldDataSchema, complexFieldDataSchema)
    val union = buildUnionDataSchema(
      values,
      typedDefinitions = Map(
        "org.coursera.naptime.simpleField" -> "easy",
        "org.coursera.naptime.complexField" -> "hard"))

    val fieldName = "typedDefinitionTestField"
    val field =
      NaptimeUnionField.build(schemaMetadata, union, fieldName, None, resourceName, List.empty)

    val expectedUnionTypes = List(
      ObjectType(
        "CoursesV1_easyMember",
        List(
          NaptimeRecordField.build(
            schemaMetadata,
            simpleFieldDataSchema,
            "easy",
            Some("org.coursera.naptime"),
            resourceName,
            List.empty))),
      ObjectType(
        "CoursesV1_hardMember",
        List(
          NaptimeRecordField.build(
            schemaMetadata,
            complexFieldDataSchema,
            "hard",
            Some("org.coursera.naptime"),
            resourceName,
            List.empty))))
    val expectedField = UnionType("CoursesV1_typedDefinitionTestField", None, expectedUnionTypes)

    assertComputedIsExpectedUnionTypeUsingNames(field.fieldType, expectedField)
  }

  @Test
  def build_ShorthandTypedDefinitionUnion_InSameNamespace(): Unit = {

    /**
     * This test looks similar to build_TypedDefinitionUnion but is ensuring the `namespace`
     * feature behaves as a prefix to the typedDefinitions field.
     * For example, given a union `union[simpleField, complexField]`, where both records are
     * in the org.coursera.naptime namespace, we'd expect we can refer to these fields without
     * qualifying the namespace.
     */
    val integerField = new Field(new IntegerDataSchema())
    integerField.setName("integerField", new java.lang.StringBuilder())
    val simpleFieldDataSchema = buildRecordField("simpleField", List(integerField))
    val complexFieldDataSchema = buildRecordField("complexField", List(integerField))

    val values = List(simpleFieldDataSchema, complexFieldDataSchema)
    val union = buildUnionDataSchema(
      values,
      typedDefinitions = Map("simpleField" -> "easy", "complexField" -> "hard"),
      Map("namespace" -> "org.coursera.naptime"))

    val fieldName = "typedDefinitionTestField"
    val field =
      NaptimeUnionField.build(schemaMetadata, union, fieldName, None, resourceName, List.empty)

    val expectedUnionTypes = List(
      ObjectType(
        "CoursesV1_easyMember",
        List(
          NaptimeRecordField.build(
            schemaMetadata,
            simpleFieldDataSchema,
            "easy",
            Some("org.coursera.naptime"),
            resourceName,
            List.empty))),
      ObjectType(
        "CoursesV1_hardMember",
        List(
          NaptimeRecordField.build(
            schemaMetadata,
            complexFieldDataSchema,
            "hard",
            Some("org.coursera.naptime"),
            resourceName,
            List.empty))))
    val expectedField = UnionType("CoursesV1_typedDefinitionTestField", None, expectedUnionTypes)

    assertComputedIsExpectedUnionTypeUsingNames(field.fieldType, expectedField)
  }

  @Test
  def build_ShorthandTypedDefinitionUnion_InDifferentNamespace(): Unit = {

    /**
     * The goal of this test is similar to build_ShorthandTypedDefinitionUnion_InSameNamespace,
     * but to show that for fields outside of the namespace, they are converted to
     * `NaptimeRecordField` with the namespace prefixed.
     */
    val integerField = new Field(new IntegerDataSchema())
    integerField.setName("integerField", new java.lang.StringBuilder())
    val simpleFieldDataSchema =
      buildRecordField("simpleField", List(integerField), "org.coursera.awaketime")
    val complexFieldDataSchema = buildRecordField("complexField", List(integerField))

    val values = List(simpleFieldDataSchema, complexFieldDataSchema)
    val union = buildUnionDataSchema(
      values,
      Map("complexField" -> "hard"),
      Map("namespace" -> "org.coursera.naptime"))

    val fieldName = "typedDefinitionTestField"
    val field =
      NaptimeUnionField.build(schemaMetadata, union, fieldName, None, resourceName, List.empty)

    val expectedUnionTypes = List(
      ObjectType(
        "CoursesV1_org_coursera_awaketime_simpleFieldMember",
        List(
          NaptimeRecordField.build(
            schemaMetadata,
            simpleFieldDataSchema,
            "org_coursera_awaketime_simpleField",
            Some("org.coursera.awaketime"),
            resourceName,
            List.empty))),
      ObjectType(
        "CoursesV1_hardMember",
        List(
          NaptimeRecordField.build(
            schemaMetadata,
            complexFieldDataSchema,
            "hard",
            Some("org.coursera.naptime"),
            resourceName,
            List.empty))))
    val expectedField = UnionType("CoursesV1_typedDefinitionTestField", None, expectedUnionTypes)

    assertComputedIsExpectedUnionTypeUsingNames(field.fieldType, expectedField)
  }

}
