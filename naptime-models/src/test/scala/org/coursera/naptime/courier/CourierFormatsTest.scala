package org.coursera.naptime.courier

import com.linkedin.data.DataComplex
import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import com.linkedin.data.template.DataTemplateUtil
import com.linkedin.data.template.RecordTemplate
import com.linkedin.data.template.UnionTemplate
import org.coursera.common.stringkey.StringKey
import org.coursera.courier.data.IntArray
import org.coursera.courier.templates.DataTemplates
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.courier.Exceptions.ReadException
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.libs.json.OFormat

class CourierFormatsTest extends AssertionsForJUnit {
  import CourierFormatsTest._

  @Test
  def testRecordTemplateFormats(): Unit = {
    implicit val converter = CourierFormats.recordTemplateFormats[MockRecord]

    val json = """ {"string": "value", "int": 1 } """
    val parsed = Json.parse(json)
    val mock = Json.fromJson[MockRecord](parsed).get
    assert(mock.data().size() === 2)
    assert(mock.data().getString("string") === "value")
    assert(mock.data().getInteger("int") === 1)

    val roundTripped = Json.toJson(mock)
    assert(parsed === roundTripped)
  }

  @Test
  def testFlatTypedDefinition(): Unit = {
    implicit val converter = CourierFormats.recordTemplateFormats[MockWithFlatTypedDefinition]

    val unionJson =
      """{
        |  "flatTypedDefinition": { "org.example.Example": { "field1": "value" } }
        |}
        |""".stripMargin
    val unionDataMap = DataTemplates.readDataMap(unionJson)
    val ftdJson =
      """
        |{
        |  "flatTypedDefinition": { "typeName": "ex", "field1": "value" }
        |}
        |""".stripMargin
    val parsed = Json.parse(ftdJson)
    val mock = Json.fromJson[MockWithFlatTypedDefinition](parsed).get

    val roundTripped = Json.toJson(mock)
    assert(parsed === roundTripped)

    val written = Json.toJson(new MockWithFlatTypedDefinition(unionDataMap))
    assert(parsed === written)
  }

  @Test
  def testFlatTypedDefinitionDoubleReferenced(): Unit = {
    implicit val converter = CourierFormats.recordTemplateFormats[MockWithFlatTypedDefinition]

    val unionMemberJson =
      """{ "org.example.Example": { "field1": "value" } }
        |""".stripMargin
    val unionMemberDataMap = DataTemplates.readDataMap(unionMemberJson)
    val ftdJson =
      """
        |{
        |  "flatTypedDefinition": { "typeName": "ex", "field1": "value" },
        |  "flatTypedDefinition2": { "typeName": "ex", "field1": "value" }
        |}
        |""".stripMargin
    val parsed = Json.parse(ftdJson)

    // Create two fields that reference the same data map
    val unionDataMap = new DataMap()
    unionDataMap.put("flatTypedDefinition", unionMemberDataMap)
    unionDataMap.put("flatTypedDefinition2", unionMemberDataMap)
    val written = Json.toJson(new MockWithFlatTypedDefinition(unionDataMap))
    assert(parsed === written)
  }

  @Test
  def testTypedDefinition(): Unit = {
    implicit val converter = CourierFormats.recordTemplateFormats[MockWithTypedDefinition]

    val unionJson = """ {"typedDefinition": { "org.example.Example": { "field1": "value" } } } """
    val unionDataMap = DataTemplates.readDataMap(unionJson)
    val tdJson =
      """ {"typedDefinition": { "typeName": "ex", "definition": { "field1": "value" } } } """
    val parsed = Json.parse(tdJson)
    val mock = Json.fromJson[MockWithTypedDefinition](parsed).get

    val roundTripped = Json.toJson(mock)
    assert(parsed === roundTripped)

    val written = Json.toJson(new MockWithTypedDefinition(unionDataMap))
    assert(parsed === written)
  }

  @Test
  def testTypedDefinitionDoubleReferenced(): Unit = {
    implicit val converter = CourierFormats.recordTemplateFormats[MockWithTypedDefinition]

    val unionMemberJson =
      """{ "org.example.Example": { "field1": "value" } }
        |""".stripMargin
    val unionMemberDataMap = DataTemplates.readDataMap(unionMemberJson)
    val ftdJson =
      """
        |{
        |  "typedDefinition": { "typeName": "ex", "definition": { "field1": "value" } },
        |  "typedDefinition2": { "typeName": "ex", "definition": { "field1": "value" } }
        |}
        |""".stripMargin
    val parsed = Json.parse(ftdJson)

    // Create two fields that reference the same data map
    val unionDataMap = new DataMap()
    unionDataMap.put("typedDefinition", unionMemberDataMap)
    unionDataMap.put("typedDefinition2", unionMemberDataMap)
    val written = Json.toJson(new MockWithTypedDefinition(unionDataMap))
    assert(parsed === written)
  }

  @Test
  def testUnionTemplateFormats(): Unit = {
    implicit val converter = CourierFormats.unionTemplateFormats[MockUnion]

    val json = """ { "org.example.Example": {} } """
    val parsed = Json.parse(json)
    Json.fromJson[MockUnion](parsed) match {
      case success: JsSuccess[MockUnion] =>
        val mock = success.get
        val dataMap = mock.data().asInstanceOf[DataMap]
        assert(dataMap.size() === 1)
        assert(dataMap.getDataMap("org.example.Example").size() == 0)

        val roundTripped = Json.toJson(mock)
        assert(parsed === roundTripped)
      case error: JsError => fail(JsError.toJson(error).toString())
    }
  }

  @Test
  def testTyperefUnionTemplateFormats(): Unit = {
    implicit val converter = CourierFormats.unionTemplateFormats[MockTypedDefinition]

    val json = """ { "typeName": "ex", "definition": { "field1": 123 } } """
    val parsed = Json.parse(json)

    val mock = Json.fromJson[MockTypedDefinition](parsed).get
    val unionDataMap = mock.data().asInstanceOf[DataMap]
    assert(unionDataMap.size() === 1)
    val memberDataMap = unionDataMap.getDataMap("org.example.Example")
    assert(memberDataMap.getLong("field1") === 123L)

    val roundTripped = Json.toJson(mock)
    assert(parsed === roundTripped)
  }

  @Test
  def testDataTemplateNestedInOFormat(): Unit = {

    val json =
      """
        |{
        |  "mockRecord": {
        |    "string": "value", "int": 1
        |  }
        |}
        |""".stripMargin
    val parsed = Json.parse(json)
    val wrapper = Json.fromJson[Wrapper](parsed).get
    val mock = wrapper.mockRecord

    assert(mock.data().size() === 2)
    assert(mock.data().getString("string") === "value")
    assert(mock.data().getInteger("int") === 1)

    val roundTripped = Json.toJson(wrapper)
    assert(parsed === roundTripped)
  }

  @Test
  def testRecordTemplateStringKeyFormat(): Unit = {
    implicit val converter =
      CourierFormats.recordTemplateStringKeyFormat[MockRecord]

    val string = "value~1"
    val stringKey = StringKey(string)
    val mock = stringKey.asOpt[MockRecord].get
    assert(mock.data().size() === 2)
    assert(mock.data().getString("string") === "value")
    assert(mock.data().getInteger("int") === 1)

    val roundTripped = StringKey.unapply(stringKey).get
    assert(roundTripped === string)

    val string2 = "nottwo"
    val stringKey2 = StringKey(string2)
    val mock2 = stringKey2.asOpt[MockRecord]

    val string3 = "value~notnumeric"
    val stringKey3 = StringKey(string3)
    val mock3 = stringKey3.asOpt[MockRecord]

    assert(mock2 === None) // IOException
    assert(mock3 === None) // DataValidationException
  }

  @Test
  def testArrayTemplateStringKeyFormat(): Unit = {
    implicit val converter =
      CourierFormats.arrayTemplateStringKeyFormat[IntArray]

    val string = "1,2,3"
    val stringKey = StringKey(string)
    val array = stringKey.asOpt[IntArray].get
    assert(array.size === 3)
    assert(array(0) === 1)
    assert(array(1) === 2)
    assert(array(2) === 3)

    val roundTripped = StringKey.unapply(stringKey).get
    assert(roundTripped === string)

    val string2 = "1,2,"
    val stringKey2 = StringKey(string2)
    val mock2 = stringKey2.asOpt[IntArray]

    val string3 = "!~"
    val stringKey3 = StringKey(string3)
    val mock3 = stringKey3.asOpt[IntArray]

    assert(mock2 === None) // DataValidationException
    assert(mock3 === None) // IOException
  }

  @Test
  def testRecordTemplateStringKeyFormatMaterialized(): Unit = {
    implicit val converter =
      CourierFormats.recordTemplateStringKeyFormat[TestPositiveIntComplex]

    val string = "1!~2~3!~4,5!~6"
    val stringKey = StringKey(string)
    val recordOption = stringKey.asOpt[TestPositiveIntComplex]
    assert(
      recordOption.contains(
        TestPositiveIntComplex(
          TestPositiveIntPair(TestPositiveInt(1), TestPositiveInt(2)),
          TestPositiveIntPairArray(
            List(
              TestPositiveIntPair(TestPositiveInt(3), TestPositiveInt(4)),
              TestPositiveIntPair(TestPositiveInt(5), TestPositiveInt(6)))))))

    val string2 = "-1!~2~3!~4,5!~6"
    val stringKey2 = StringKey(string2)
    val recordOption2 = stringKey2.asOpt[TestPositiveIntComplex]
    assert(recordOption2 === None) // TemplateOutputCastException

    val string3 = "1!~2~3!~4,5!~-6"
    val stringKey3 = StringKey(string3)
    val recordOption3 = stringKey3.asOpt[TestPositiveIntComplex]
    assert(recordOption3 === None) // TemplateOutputCastException
  }

  @Test
  def testArrayStringKeyFormatMaterialized(): Unit = {
    // TestPositiveIntPairArray is generated by the
    // array[TestPositiveIntPair] field in TestPositiveIntComplex
    implicit val converter =
      CourierFormats.arrayTemplateStringKeyFormat[TestPositiveIntPairArray]

    val string = "3~4,5~6"
    val stringKey = StringKey(string)
    val recordOption = stringKey.asOpt[TestPositiveIntPairArray]
    assert(
      recordOption.contains(
        TestPositiveIntPairArray(
          List(
            TestPositiveIntPair(TestPositiveInt(3), TestPositiveInt(4)),
            TestPositiveIntPair(TestPositiveInt(5), TestPositiveInt(6))))))

    val string2 = "3~4,5~-6"
    val stringKey2 = StringKey(string2)
    val recordOption2 = stringKey2.asOpt[TestPositiveIntPairArray]
    assert(recordOption2 === None) // TemplateOutputCastException
  }

  @Test
  def testValidatingRecordTemplateFormats(): Unit = {
    implicit val converter = CourierFormats.recordTemplateFormats[MockRecord]

    val json = """ { "int": false } """
    val parsed = Json.parse(json)
    val jsResult = Json.fromJson[MockRecord](parsed)
    assert(jsResult.isError === true)
    val error = jsResult.asInstanceOf[JsError]
    assert(error.errors.size == 2)

    error.errors(0) match {
      case (path, Seq(vError)) =>
        assert(path.toString() === "/int")
        assert(vError.message === "ERROR :: /int :: false cannot be coerced to Integer")
    }

    error.errors(1) match {
      case (path, Seq(vError)) =>
        assert(path.toString() === "/string")
        assert(
          vError.message ===
            "ERROR :: /string :: field is required but not found and has no default value")
    }
  }

  @Test
  def testDecimal1p17e22Parse(): Unit = {
    val result = testRecordWithDoubleFormat.reads(Json.parse(testRecordWithDoubleJson))
    assert(result === JsSuccess(testRecordWithDouble))
  }

  @Test
  def testDecimal1p17e22RoundTrip(): Unit = {
    val serialized = Json.prettyPrint(testRecordWithDoubleFormat.writes(testRecordWithDouble))
    val result = testRecordWithDoubleFormat.reads(Json.parse(serialized))
    assert(result === JsSuccess(testRecordWithDouble))
  }

  @Test
  def testDecimal1point17e22ToNumber(): Unit = {
    // testing that no exception is thrown
    CourierFormats.bigDecimalToNumber(decimal1p17e22)
  }

  @Test
  def testDecimalOutsideDoubleRangeToNumber(): Unit = {
    assertThrows[ReadException](CourierFormats.bigDecimalToNumber(decimalTooBigForDouble))
    assertThrows[ReadException](CourierFormats.bigDecimalToNumber(decimalTooSmallForDouble))
  }
}

object CourierFormatsTest {
  private class MockRecord(private val dataMap: DataMap)
      extends RecordTemplate(dataMap, MockRecord.SCHEMA) {
    dataMap.makeReadOnly()
  }

  private object MockRecord {
    def build(dataMap: DataMap, converter: DataConversion): MockRecord = {
      new MockRecord(dataMap)
    }

    val SCHEMA = DataTemplateUtil.parseSchema("""
      |{
      |  "name": "MockRecord",
      |  "type": "record",
      |  "fields": [
      |    { "name": "string", "type": "string" },
      |    { "name": "int", "type": "int" }
      |  ]
      |}
      |""".stripMargin).asInstanceOf[RecordDataSchema]
  }

  private case class Wrapper(mockRecord: MockRecord)
  private object Wrapper {
    implicit val mockRecordFormat = CourierFormats.recordTemplateFormats[MockRecord]
    implicit val jsonFormat: OFormat[Wrapper] = Json.format[Wrapper]
  }

  private class MockUnion(private val obj: AnyRef) extends UnionTemplate(obj, MockUnion.SCHEMA) {
    obj match {
      case complex: DataComplex => complex.makeReadOnly()
      case _                    =>
    }
  }

  private object MockUnion {
    def build(dataMap: DataMap, converter: DataConversion): MockUnion = {
      new MockUnion(dataMap)
    }

    val SCHEMA = DataTemplateUtil
      .parseSchema(
        """
      |[
      |  "string",
      |  "int",
      |  { "name": "Example", "namespace": "org.example", "type": "record", "fields": [] }
      |]
      |""".stripMargin)
      .asInstanceOf[UnionDataSchema]
  }

  private class MockWithFlatTypedDefinition(val dataMap: DataMap)
      extends RecordTemplate(dataMap, MockWithFlatTypedDefinition.SCHEMA) {
    dataMap.makeReadOnly()
  }

  private object MockWithFlatTypedDefinition {
    def build(dataMap: DataMap, converter: DataConversion): MockWithFlatTypedDefinition = {
      new MockWithFlatTypedDefinition(dataMap)
    }

    val SCHEMA = DataTemplateUtil.parseSchema("""
      |{
      |  "name": "MockWithFlatTypedDefinition",
      |  "namespace": "org.example",
      |  "type": "record",
      |  "fields": [
      |    {
      |      "name": "flatTypedDefinition",
      |      "type": {
      |        "name": "FlatTypedDefinition",
      |        "type": "typeref",
      |        "ref": [
      |          "string",
      |          "int",
      |          {
      |            "name": "Example",
      |            "namespace": "org.example",
      |            "type": "record",
      |            "fields": [
      |              { "name": "field1", "type": "string" }
      |            ]
      |          }
      |        ],
      |        "flatTypedDefinition": {
      |          "string": "str",
      |          "int": "num",
      |          "org.example.Example": "ex"
      |        }
      |      }
      |    },
      |    {
      |      "name": "flatTypedDefinition2",
      |      "type": "FlatTypedDefinition",
      |      "optional": true
      |    }
      |  ]
      |}
      |""".stripMargin).asInstanceOf[RecordDataSchema]
  }

  private class MockWithTypedDefinition(val dataMap: DataMap)
      extends RecordTemplate(dataMap, MockWithTypedDefinition.SCHEMA) {
    dataMap.makeReadOnly()
  }

  private object MockWithTypedDefinition {
    def build(dataMap: DataMap, converter: DataConversion): MockWithTypedDefinition = {
      new MockWithTypedDefinition(dataMap)
    }

    val SCHEMA = DataTemplateUtil.parseSchema("""
      |{
      |  "name": "MockWithTypedDefinition",
      |  "namespace": "org.example",
      |  "type": "record",
      |  "fields": [
      |    {
      |      "name": "typedDefinition",
      |      "type": {
      |        "name": "TypedDefinition",
      |        "type": "typeref",
      |        "ref": [
      |          "string",
      |          "int",
      |          {
      |            "name": "Example",
      |            "namespace": "org.example",
      |            "type": "record",
      |            "fields": [
      |              { "name": "field1", "type": "string" }
      |            ]
      |          }
      |        ],
      |        "typedDefinition": {
      |          "string": "str",
      |          "int": "num",
      |          "org.example.Example": "ex"
      |        }
      |      }
      |    },
      |    {
      |      "name": "typedDefinition2",
      |      "type": "TypedDefinition",
      |      "optional": true
      |    }
      |  ]
      |}
      |""".stripMargin).asInstanceOf[RecordDataSchema]
  }

  private class MockTypedDefinition(val dataMap: DataMap)
      extends UnionTemplate(dataMap, MockTypedDefinition.SCHEMA) {
    dataMap.makeReadOnly()
  }

  private object MockTypedDefinition {
    def build(dataMap: DataMap, converter: DataConversion): MockTypedDefinition = {
      new MockTypedDefinition(dataMap)
    }

    val SCHEMA = DataTemplateUtil.parseSchema("""
      |[
      |  "string",
      |  "int",
      |  {
      |    "name": "Example",
      |    "namespace": "org.example",
      |    "type": "record",
      |    "fields": [
      |      { "name": "field1", "type": "long" }
      |    ]
      |  }
      |]
      |""".stripMargin).asInstanceOf[UnionDataSchema]

    val TYPEREF_SCHEMA = DataTemplateUtil.parseSchema("""
      |{
      |  "name": "MockTypedDefinition",
      |  "type": "typeref",
      |  "ref": [
      |    "string",
      |    "int",
      |    {
      |      "name": "Example",
      |      "namespace": "org.example",
      |      "type": "record",
      |      "fields": [
      |        { "name": "field1", "type": "long" }
      |      ]
      |    }
      |  ],
      |  "typedDefinition": {
      |    "string": "str",
      |    "int": "num",
      |    "org.example.Example": "ex"
      |  }
      |}
      |""".stripMargin).asInstanceOf[TyperefDataSchema]
  }

  val decimal1p17e22 = BigDecimal("1.17e22")
  val decimalTooBigForDouble = 2 * BigDecimal(Double.MaxValue)
  val decimalTooSmallForDouble = 2 * BigDecimal(Double.MinValue)
  val testRecordWithDoubleFormat = CourierFormats.recordTemplateFormats[TestRecordWithDouble]
  val testRecordWithDouble = TestRecordWithDouble(doubleField = decimal1p17e22.toDouble)
  val testRecordWithDoubleJson =
    """
      |{
      |  "doubleField": 1.17e22
      |}
    """.stripMargin

}
