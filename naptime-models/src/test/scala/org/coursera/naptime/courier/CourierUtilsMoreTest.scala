package org.coursera.naptime.courier

import com.linkedin.data.DataMap
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import com.linkedin.data.template.DataTemplateUtil
import org.coursera.naptime.courier.CourierUtils._
import org.coursera.naptime.courier.Exceptions.ReadException
import org.coursera.naptime.courier.Exceptions.SerializationException
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

/**
 * Additional tests for CourierUtils to cover remaining uncovered branches:
 * - getUnionMemberTypeName: all branches (no typeref, None typeref, TypedDef, FlatTypedDef)
 * - getTypedDefinition: both annotations present → SerializationException
 * - typeNameToMemberSchema: simple name not in union schema → throw ReadException
 *   (the qualified-name lookup fails)
 */
class CourierUtilsMoreTest extends AssertionsForJUnit {

  private val typedDefSchemaJson =
    """
      |{
      |  "name": "TestTyperef2",
      |  "namespace": "org.example",
      |  "type": "typeref",
      |  "ref": [
      |    {
      |      "name": "MemberX",
      |      "namespace": "org.example",
      |      "type": "record",
      |      "fields": []
      |    },
      |    {
      |      "name": "MemberY",
      |      "namespace": "org.example",
      |      "type": "record",
      |      "fields": []
      |    }
      |  ],
      |  "typedDefinition": {
      |    "org.example.MemberX": "memberX",
      |    "org.example.MemberY": "memberY"
      |  }
      |}
      |""".stripMargin

  private val flatTypedDefSchemaJson =
    """
      |{
      |  "name": "FlatTestTyperef2",
      |  "namespace": "org.example",
      |  "type": "typeref",
      |  "ref": [
      |    {
      |      "name": "MemberP",
      |      "namespace": "org.example",
      |      "type": "record",
      |      "fields": []
      |    }
      |  ],
      |  "flatTypedDefinition": {
      |    "org.example.MemberP": "memberP"
      |  }
      |}
      |""".stripMargin

  private val typedDefSchema =
    DataTemplateUtil.parseSchema(typedDefSchemaJson).asInstanceOf[TyperefDataSchema]

  private val flatTypedDefSchema =
    DataTemplateUtil.parseSchema(flatTypedDefSchemaJson).asInstanceOf[TyperefDataSchema]

  // ─── getUnionMemberTypeName: no typeref (returns memberKey directly) ──────────

  @Test
  def getUnionMemberTypeName_noDeclaringTyperef_returnsMemberKey(): Unit = {
    val rawUnionSchema = DataTemplateUtil
      .parseSchema(
        """["string","int"]"""
      )
      .asInstanceOf[UnionDataSchema]

    val dataMap = new DataMap()
    dataMap.put("string", "hello")
    val member = new RawUnionMember(dataMap, rawUnionSchema)

    // No declaring typeref → memberKey returned directly
    val result = CourierUtils.getUnionMemberTypeName(member)
    assertResult("string")(result)
  }

  // ─── getUnionMemberTypeName: typeref present but no typedDefinition → memberKey ─

  @Test
  def getUnionMemberTypeName_typerefWithoutAnnotation_returnsMemberKey(): Unit = {
    val plainTyperefSchema = DataTemplateUtil
      .parseSchema(
        """
        |{
        |  "name": "PlainTyperef2",
        |  "namespace": "org.example",
        |  "type": "typeref",
        |  "ref": [
        |    {
        |      "name": "MemberA2",
        |      "namespace": "org.example",
        |      "type": "record",
        |      "fields": []
        |    }
        |  ]
        |}
        |""".stripMargin
      )
      .asInstanceOf[TyperefDataSchema]

    val unionSchema = plainTyperefSchema.getDereferencedDataSchema.asInstanceOf[UnionDataSchema]
    val dataMap = new DataMap()
    dataMap.put("org.example.MemberA2", new DataMap())
    val member = new MissingMappingUnionMember(dataMap, plainTyperefSchema)

    val result = CourierUtils.getUnionMemberTypeName(member)
    assertResult("org.example.MemberA2")(result)
  }

  // ─── getUnionMemberTypeName: TypedDef present → returns typeName ─────────────

  @Test
  def getUnionMemberTypeName_typedDef_returnsTypeName(): Unit = {
    val unionSchema = typedDefSchema.getDereferencedDataSchema.asInstanceOf[UnionDataSchema]
    val dataMap = new DataMap()
    dataMap.put("org.example.MemberX", new DataMap())
    val member = new MissingMappingUnionMember(dataMap, typedDefSchema)

    val result = CourierUtils.getUnionMemberTypeName(member)
    assertResult("memberX")(result)
  }

  // ─── getUnionMemberTypeName: FlatTypedDef present → returns typeName ──────────

  @Test
  def getUnionMemberTypeName_flatTypedDef_returnsTypeName(): Unit = {
    val unionSchema = flatTypedDefSchema.getDereferencedDataSchema.asInstanceOf[UnionDataSchema]
    val dataMap = new DataMap()
    dataMap.put("org.example.MemberP", new DataMap())
    val member = new MissingMappingUnionMember(dataMap, flatTypedDefSchema)

    val result = CourierUtils.getUnionMemberTypeName(member)
    assertResult("memberP")(result)
  }

  // ─── getTypedDefinition: both annotations present → SerializationException ────

  @Test
  def getTypedDefinition_bothAnnotations_throwsSerializationException(): Unit = {
    // Build a schema JSON with BOTH typedDefinition and flatTypedDefinition
    val bothAnnotationsSchemaJson =
      """
        |{
        |  "name": "BothAnnotations",
        |  "namespace": "org.example",
        |  "type": "typeref",
        |  "ref": [
        |    { "name": "SomeMember", "namespace": "org.example", "type": "record", "fields": [] }
        |  ],
        |  "typedDefinition": { "org.example.SomeMember": "someMember" },
        |  "flatTypedDefinition": { "org.example.SomeMember": "someMember" }
        |}
        |""".stripMargin
    val schema =
      DataTemplateUtil.parseSchema(bothAnnotationsSchemaJson).asInstanceOf[TyperefDataSchema]

    intercept[SerializationException] {
      CourierUtils.getTypedDefinition(schema)
    }
  }

  // ─── typeNameToMemberSchema: simple name lookup → qualified name not in union → throw ─

  @Test
  def typeNameToMemberSchema_simpleNameNotInUnion_throwsReadException(): Unit = {
    // nameMap uses simple name, but the union schema doesn't have any member for
    // either the simple name or the qualified name.
    val nameMap = new DataMap()
    nameMap.put("NonExistentMember", "memberX")

    val unionSchema = typedDefSchema.getDereferencedDataSchema.asInstanceOf[UnionDataSchema]

    intercept[ReadException] {
      CourierUtils.typeNameToMemberSchema(typedDefSchema, unionSchema, nameMap, "memberX")
    }
  }

  // ─── typeNameToMemberSchema: fully qualified name not found → throw ────────────

  @Test
  def typeNameToMemberSchema_fullyQualifiedNameNotInUnion_throwsReadException(): Unit = {
    val nameMap = new DataMap()
    // Use a fully qualified name (contains ".") that's not in the union
    nameMap.put("com.totally.different.Member", "memberX")

    val unionSchema = typedDefSchema.getDereferencedDataSchema.asInstanceOf[UnionDataSchema]

    intercept[ReadException] {
      CourierUtils.typeNameToMemberSchema(typedDefSchema, unionSchema, nameMap, "memberX")
    }
  }
}
