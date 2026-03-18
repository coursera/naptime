package org.coursera.naptime.courier

import com.linkedin.data.DataMap
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import com.linkedin.data.template.DataTemplateUtil
import org.coursera.courier.companions.UnionCompanion
import org.coursera.courier.companions.UnionMemberCompanion
import org.coursera.courier.companions.UnionWithTyperefCompanion
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.courier.templates.ScalaUnionTemplate
import org.coursera.naptime.courier.TestTypedDefinition.TestTypedDefinitionAlphaMember
import org.coursera.naptime.courier.TestTypedDefinition.TestTypedDefinitionBetaMember
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

/**
 * Extended tests for TypedDefinitions to cover error paths.
 * Exercises: no-typeref branch, missing-mapping, flatTypedDefinition, no-annotation.
 */
class TypedDefinitionsExtendedTest extends AssertionsForJUnit {

  // ─── typeName(instance) happy paths ─────────────────────────────────────────

  @Test
  def typeName_instance_alpha_resolvesTypeName(): Unit = {
    val member = TestTypedDefinition.TestTypedDefinitionAlphaMember(TestTypedDefinitionAlpha())
    assertResult("alpha")(TypedDefinitions.typeName(member))
  }

  @Test
  def typeName_instance_beta_resolvesTypeName(): Unit = {
    val member = TestTypedDefinition.TestTypedDefinitionBetaMember(TestTypedDefinitionBeta())
    assertResult("beta")(TypedDefinitions.typeName(member))
  }

  // ─── typeName(instance): no declaringTyperefSchema → throw ──────────────────

  @Test
  def typeName_instance_noDeclaringTyperef_throwsIllegalArgumentException(): Unit = {
    // Build a raw union schema (no typeref wrapper)
    val rawUnionSchema = DataTemplateUtil
      .parseSchema(
        """["string","int"]"""
      )
      .asInstanceOf[UnionDataSchema]

    val dataMap = new DataMap()
    dataMap.put("string", "hello")
    val rawMember = new RawUnionMember(dataMap, rawUnionSchema)

    intercept[IllegalArgumentException] {
      TypedDefinitions.typeName(rawMember)
    }
  }

  // ─── typeName(instance): no annotation on typeref → throw ───────────────────

  @Test
  def typeName_instance_noAnnotation_throwsIllegalArgumentException(): Unit = {
    val schemaWithNoAnnotation = DataTemplateUtil
      .parseSchema(
        """
        |{
        |  "name": "NoAnnotationTyperef",
        |  "namespace": "org.example",
        |  "type": "typeref",
        |  "ref": [
        |    {
        |      "name": "TestTypedDefinitionAlpha",
        |      "namespace": "org.coursera.naptime.courier",
        |      "type": "record",
        |      "fields": []
        |    }
        |  ]
        |}
        |""".stripMargin
      )
      .asInstanceOf[TyperefDataSchema]

    val dataMap = new DataMap()
    dataMap.put("org.coursera.naptime.courier.TestTypedDefinitionAlpha", new DataMap())
    val member = new MissingMappingUnionMember(dataMap, schemaWithNoAnnotation)

    intercept[IllegalArgumentException] {
      TypedDefinitions.typeName(member)
    }
  }

  // ─── typeName(instance): typedDefinition with empty mapping → throw ──────────

  @Test
  def typeName_instance_emptyTypedDefinitionMapping_throwsIllegalArgumentException(): Unit = {
    val schemaWithEmptyMapping = DataTemplateUtil
      .parseSchema(
        """
        |{
        |  "name": "TyperefEmptyMapping",
        |  "namespace": "org.example",
        |  "type": "typeref",
        |  "ref": [
        |    {
        |      "name": "TestTypedDefinitionAlpha",
        |      "namespace": "org.coursera.naptime.courier",
        |      "type": "record",
        |      "fields": []
        |    }
        |  ],
        |  "typedDefinition": {}
        |}
        |""".stripMargin
      )
      .asInstanceOf[TyperefDataSchema]

    val dataMap = new DataMap()
    dataMap.put("org.coursera.naptime.courier.TestTypedDefinitionAlpha", new DataMap())
    val member = new MissingMappingUnionMember(dataMap, schemaWithEmptyMapping)

    intercept[IllegalArgumentException] {
      TypedDefinitions.typeName(member)
    }
  }

  // ─── typeName(instance): flatTypedDefinition with empty mapping → throw ──────

  @Test
  def typeName_instance_emptyFlatTypedDefinitionMapping_throwsIllegalArgumentException(): Unit = {
    val schemaWithEmptyFlatMapping = DataTemplateUtil
      .parseSchema(
        """
        |{
        |  "name": "TyperefEmptyFlatMapping",
        |  "namespace": "org.example",
        |  "type": "typeref",
        |  "ref": [
        |    {
        |      "name": "TestTypedDefinitionAlpha",
        |      "namespace": "org.coursera.naptime.courier",
        |      "type": "record",
        |      "fields": []
        |    }
        |  ],
        |  "flatTypedDefinition": {}
        |}
        |""".stripMargin
      )
      .asInstanceOf[TyperefDataSchema]

    val dataMap = new DataMap()
    dataMap.put("org.coursera.naptime.courier.TestTypedDefinitionAlpha", new DataMap())
    val member = new MissingMappingUnionMember(dataMap, schemaWithEmptyFlatMapping)

    intercept[IllegalArgumentException] {
      TypedDefinitions.typeName(member)
    }
  }

  // ─── typeName(companion) happy paths ────────────────────────────────────────

  @Test
  def typeName_companion_alpha_resolvesTypeName(): Unit = {
    assertResult("alpha")(TypedDefinitions.typeName(TestTypedDefinitionAlphaMember))
  }

  @Test
  def typeName_companion_beta_resolvesTypeName(): Unit = {
    assertResult("beta")(TypedDefinitions.typeName(TestTypedDefinitionBetaMember))
  }

  // ─── typeName(companion): plain UnionCompanion (not WithTyperef) → throw ─────

  @Test
  def typeName_companion_noTyperefCompanion_throwsIllegalArgumentException(): Unit = {
    val plainCompanion = new PlainUnionMemberCompanion()
    intercept[IllegalArgumentException] {
      TypedDefinitions.typeName(plainCompanion)
    }
  }
}

// ─── Helper classes ───────────────────────────────────────────────────────────

/** ScalaUnionTemplate with no declaring typeref schema. */
private class RawUnionMember(dataMap: DataMap, schema: UnionDataSchema)
    extends ScalaUnionTemplate(dataMap, schema) {
  override def declaringTyperefSchema: Option[TyperefDataSchema] = None
}

/** ScalaUnionTemplate that declares a given typeref schema. */
private class MissingMappingUnionMember(dataMap: DataMap, typeref: TyperefDataSchema)
    extends ScalaUnionTemplate(
      dataMap,
      typeref.getDereferencedDataSchema.asInstanceOf[UnionDataSchema]) {
  override def declaringTyperefSchema: Option[TyperefDataSchema] = Some(typeref)
}

/** UnionMemberCompanion whose unionCompanion is a plain UnionCompanion (not WithTyperef). */
private class PlainUnionMemberCompanion extends UnionMemberCompanion[TestTypedDefinition] {
  override val memberKey: String = "org.coursera.naptime.courier.TestTypedDefinitionAlpha"
  override def unionCompanion: UnionCompanion[TestTypedDefinition] =
    new PlainUnionCompanion()
}

private class PlainUnionCompanion extends UnionCompanion[TestTypedDefinition] {
  override def SCHEMA: UnionDataSchema = TestTypedDefinition.SCHEMA
  override def build(union: DataMap, conversion: DataConversion): TestTypedDefinition =
    TestTypedDefinition.build(union, conversion)
}
