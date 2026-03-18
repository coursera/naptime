package org.coursera.naptime.courier

import com.linkedin.data.DataMap
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import com.linkedin.data.template.DataTemplateUtil
import org.coursera.naptime.courier.CourierUtils._
import org.coursera.naptime.courier.Exceptions.ReadException
import org.coursera.naptime.courier.Exceptions.WriteException
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

class CourierUtilsExtendedTest extends AssertionsForJUnit {

  // Shared schema setup with a typeref union and typedDefinition mapping
  private val schemaJson =
    """
      |{
      |  "name": "TestTyperef",
      |  "namespace": "org.example",
      |  "type": "typeref",
      |  "ref": [
      |    {
      |      "name": "MemberA",
      |      "namespace": "org.example",
      |      "type": "record",
      |      "fields": [
      |        { "name": "value", "type": "string" }
      |      ]
      |    },
      |    {
      |      "name": "MemberB",
      |      "namespace": "org.example",
      |      "type": "record",
      |      "fields": [
      |        { "name": "count", "type": "int" }
      |      ]
      |    }
      |  ],
      |  "typedDefinition": {
      |    "org.example.MemberA": "memberA",
      |    "org.example.MemberB": "memberB"
      |  }
      |}
      |""".stripMargin

  private val typerefSchema =
    DataTemplateUtil.parseSchema(schemaJson).asInstanceOf[TyperefDataSchema]
  private val unionSchema =
    typerefSchema.getDereferencedDataSchema.asInstanceOf[UnionDataSchema]

  // ---------------------------------------------------------------------------------------
  // getTypedDefinition
  // ---------------------------------------------------------------------------------------

  @Test
  def getTypedDefinition_typedDefinition_returnsSomeTypedDef(): Unit = {
    val result = CourierUtils.getTypedDefinition(typerefSchema)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[TypedDef])
  }

  @Test
  def getTypedDefinition_flatTypedDefinition_returnsSomeFlatTypedDef(): Unit = {
    val flatSchemaJson =
      """
        |{
        |  "name": "FlatTyperef",
        |  "namespace": "org.example",
        |  "type": "typeref",
        |  "ref": [
        |    {
        |      "name": "FlatMemberA",
        |      "namespace": "org.example",
        |      "type": "record",
        |      "fields": [
        |        { "name": "val", "type": "string" }
        |      ]
        |    }
        |  ],
        |  "flatTypedDefinition": {
        |    "org.example.FlatMemberA": "flatMemberA"
        |  }
        |}
        |""".stripMargin
    val flatTyperef = DataTemplateUtil.parseSchema(flatSchemaJson).asInstanceOf[TyperefDataSchema]
    val result = CourierUtils.getTypedDefinition(flatTyperef)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[FlatTypedDef])
  }

  @Test
  def getTypedDefinition_noAnnotation_returnsNone(): Unit = {
    val noAnnotationSchemaJson =
      """
        |{
        |  "name": "PlainTyperef",
        |  "type": "typeref",
        |  "ref": "string"
        |}
        |""".stripMargin
    val plainTyperef =
      DataTemplateUtil.parseSchema(noAnnotationSchemaJson).asInstanceOf[TyperefDataSchema]
    val result = CourierUtils.getTypedDefinition(plainTyperef)
    assert(result.isEmpty)
  }

  // ---------------------------------------------------------------------------------------
  // memberKeyToTypeName
  // ---------------------------------------------------------------------------------------

  @Test
  def memberKeyToTypeName_fullyQualifiedKey_returnsTypeName(): Unit = {
    val nameMap = typerefSchema.getProperties
      .get(CourierUtils.typedDefinitionField)
      .asInstanceOf[DataMap]
    val result = CourierUtils.memberKeyToTypeName(typerefSchema, nameMap, "org.example.MemberA")
    assertResult("memberA")(result)
  }

  @Test
  def memberKeyToTypeName_simpleNameInSameNamespace_resolvedViaNamespace(): Unit = {
    val nameMap = new DataMap()
    nameMap.put("MemberA", "memberA")
    // When memberKey = "org.example.MemberA" and we strip the namespace prefix,
    // it should fall back to looking up "MemberA" in the nameMap
    val result = CourierUtils.memberKeyToTypeName(typerefSchema, nameMap, "org.example.MemberA")
    assertResult("memberA")(result)
  }

  @Test
  def memberKeyToTypeName_unmappedKey_throwsWriteException(): Unit = {
    val nameMap = new DataMap()
    intercept[WriteException] {
      CourierUtils.memberKeyToTypeName(typerefSchema, nameMap, "org.example.MemberA")
    }
  }

  // ---------------------------------------------------------------------------------------
  // typeNameToMemberSchema
  // ---------------------------------------------------------------------------------------

  @Test
  def typeNameToMemberSchema_knownTypeName_returnsMemberSchema(): Unit = {
    val nameMap = typerefSchema.getProperties
      .get(CourierUtils.typedDefinitionField)
      .asInstanceOf[DataMap]
    val result =
      CourierUtils.typeNameToMemberSchema(typerefSchema, unionSchema, nameMap, "memberA")
    assert(result != null)
    assertResult("org.example.MemberA")(result.getUnionMemberKey)
  }

  @Test
  def typeNameToMemberSchema_unknownTypeName_throwsReadException(): Unit = {
    val nameMap = typerefSchema.getProperties
      .get(CourierUtils.typedDefinitionField)
      .asInstanceOf[DataMap]
    intercept[ReadException] {
      CourierUtils.typeNameToMemberSchema(typerefSchema, unionSchema, nameMap, "doesNotExist")
    }
  }

  @Test
  def typeNameToMemberSchema_simpleNameMapping_resolvesViaNamespace(): Unit = {
    // nameMap uses simple name "MemberA" → "memberA", but union has "org.example.MemberA"
    val nameMap = new DataMap()
    nameMap.put("MemberA", "memberA")
    val result =
      CourierUtils.typeNameToMemberSchema(typerefSchema, unionSchema, nameMap, "memberA")
    assert(result != null)
  }

  // ---------------------------------------------------------------------------------------
  // destructureUnionMemberDataMap
  // ---------------------------------------------------------------------------------------

  @Test
  def destructureUnionMemberDataMap_singleEntry_returnsTriple(): Unit = {
    val innerData = new DataMap()
    innerData.put("value", "hello")
    val dataMap = new DataMap()
    dataMap.put("org.example.MemberA", innerData)

    val (memberKey, memberData, memberSchema) =
      CourierUtils.destructureUnionMemberDataMap(dataMap, unionSchema)

    assertResult("org.example.MemberA")(memberKey)
    assertResult(innerData)(memberData)
    assert(memberSchema != null)
  }

  @Test
  def destructureUnionMemberDataMap_emptyDataMap_throwsWriteException(): Unit = {
    val dataMap = new DataMap()
    intercept[WriteException] {
      CourierUtils.destructureUnionMemberDataMap(dataMap, unionSchema)
    }
  }

  @Test
  def destructureUnionMemberDataMap_multipleEntries_throwsWriteException(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("org.example.MemberA", new DataMap())
    dataMap.put("org.example.MemberB", new DataMap())
    intercept[WriteException] {
      CourierUtils.destructureUnionMemberDataMap(dataMap, unionSchema)
    }
  }

  @Test
  def destructureUnionMemberDataMap_unknownMemberKey_throwsWriteException(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("org.example.UnknownMember", new DataMap())
    intercept[WriteException] {
      CourierUtils.destructureUnionMemberDataMap(dataMap, unionSchema)
    }
  }

  // ---------------------------------------------------------------------------------------
  // destructureTypedDefinitionJsObject — extra unmatched branch
  // ---------------------------------------------------------------------------------------

  @Test
  def destructureTypedDefinition_nonStringTypeName_throwsReadException(): Unit = {
    import play.api.libs.json.{JsNumber, JsObject, JsString}
    // Cover the last case branch: typeName present but not a JsString, and definition present
    val obj = JsObject(Seq(
      "typeName" -> JsNumber(42),
      "definition" -> JsObject(Seq("x" -> JsString("y")))
    ))
    intercept[ReadException] {
      CourierUtils.destructureTypedDefinitionJsObject(obj)
    }
  }
}
