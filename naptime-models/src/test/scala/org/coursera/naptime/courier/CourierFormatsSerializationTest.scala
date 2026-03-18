package org.coursera.naptime.courier

import com.linkedin.data.ByteString
import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.Null
import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import com.linkedin.data.template.DataTemplateUtil
import org.coursera.naptime.courier.Exceptions.ReadException
import org.coursera.naptime.courier.Exceptions.WriteException
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString

/**
 * Tests for CourierFormats serialization / deserialization paths still not covered:
 * - dataToJsValue: array, map, union, string, null, boolean, float, bytes, unknown
 * - jsValueToData: null, bytes, float, unknown
 * - recordToJsValue: stringKey codec path
 * - jsValueToRecord: stringKey codec path
 * - schemalessDataToJsValue: bytes
 * - arrayTemplateStringKeyFormat: writes / reads round-trip
 */
class CourierFormatsSerializationTest extends AssertionsForJUnit {

  // ─── Schemas for serialization ────────────────────────────────────────────────

  private val arrayRecordSchema = DataTemplateUtil.parseSchema(
    """
      |{
      |  "name": "ArrRec",
      |  "type": "record",
      |  "fields": [{
      |    "name": "items",
      |    "type": {"type": "array", "items": "string"},
      |    "optional": true
      |  }]
      |}
      |""".stripMargin
  ).asInstanceOf[RecordDataSchema]

  private val mapRecordSchema = DataTemplateUtil.parseSchema(
    """
      |{
      |  "name": "MapRec",
      |  "type": "record",
      |  "fields": [{
      |    "name": "data",
      |    "type": {"type": "map", "values": "string"},
      |    "optional": true
      |  }]
      |}
      |""".stripMargin
  ).asInstanceOf[RecordDataSchema]

  private val unionRecordSchema = DataTemplateUtil.parseSchema(
    """
      |{
      |  "name": "UnionRec",
      |  "type": "record",
      |  "fields": [{
      |    "name": "val",
      |    "type": ["string", "int"],
      |    "optional": true
      |  }]
      |}
      |""".stripMargin
  ).asInstanceOf[RecordDataSchema]

  private val boolRecordSchema = DataTemplateUtil.parseSchema(
    """{"name":"BR","type":"record","fields":[{"name":"flag","type":"boolean"}]}"""
  ).asInstanceOf[RecordDataSchema]

  private val floatRecordSchema = DataTemplateUtil.parseSchema(
    """{"name":"FR","type":"record","fields":[{"name":"f","type":"float"}]}"""
  ).asInstanceOf[RecordDataSchema]

  private val bytesRecordSchema = DataTemplateUtil.parseSchema(
    """{"name":"BytR","type":"record","fields":[{"name":"b","type":"bytes"}]}"""
  ).asInstanceOf[RecordDataSchema]

  private val nullRecordSchema = DataTemplateUtil.parseSchema(
    """{"name":"NR","type":"record","fields":[{"name":"n","type":"null","optional":true}]}"""
  ).asInstanceOf[RecordDataSchema]

  private val stringKeyRecordSchema = DataTemplateUtil.parseSchema(
    """
      |{
      |  "name": "StrKeyRec",
      |  "type": "record",
      |  "fields": [
      |    {"name": "key1", "type": "string"},
      |    {"name": "key2", "type": "string"}
      |  ],
      |  "codec": "StringKey"
      |}
      |""".stripMargin
  ).asInstanceOf[RecordDataSchema]

  private val outerStringKeySchema = DataTemplateUtil.parseSchema(
    """
      |{
      |  "name": "Outer",
      |  "type": "record",
      |  "fields": [{
      |    "name": "inner",
      |    "type": {
      |      "name": "StrKeyRec2",
      |      "type": "record",
      |      "fields": [
      |        {"name": "key1", "type": "string"},
      |        {"name": "key2", "type": "string"}
      |      ],
      |      "codec": "StringKey"
      |    }
      |  }]
      |}
      |""".stripMargin
  ).asInstanceOf[RecordDataSchema]

  // ─── recordToJsObject: array field ────────────────────────────────────────────

  @Test
  def recordToJsObject_withArrayField_serializes(): Unit = {
    val items = new DataList()
    items.add("a")
    items.add("b")
    val dataMap = new DataMap()
    dataMap.put("items", items)

    val result = CourierFormats.recordToJsObject(dataMap, arrayRecordSchema)
    assertResult(JsArray(Seq(JsString("a"), JsString("b"))))((result \ "items").get)
  }

  // ─── recordToJsObject: map field ─────────────────────────────────────────────

  @Test
  def recordToJsObject_withMapField_serializes(): Unit = {
    val mapData = new DataMap()
    mapData.put("k1", "v1")
    mapData.put("k2", "v2")
    val dataMap = new DataMap()
    dataMap.put("data", mapData)

    val result = CourierFormats.recordToJsObject(dataMap, mapRecordSchema)
    val jsonMap = (result \ "data").get.asInstanceOf[JsObject]
    assertResult(JsString("v1"))((jsonMap \ "k1").get)
  }

  // ─── recordToJsObject: union field ────────────────────────────────────────────

  @Test
  def recordToJsObject_withUnionField_serializes(): Unit = {
    val unionDataMap = new DataMap()
    unionDataMap.put("string", "hello")
    val dataMap = new DataMap()
    dataMap.put("val", unionDataMap)

    val result = CourierFormats.recordToJsObject(dataMap, unionRecordSchema)
    val unionJs = (result \ "val").get.asInstanceOf[JsObject]
    assertResult(JsString("hello"))((unionJs \ "string").get)
  }

  // ─── recordToJsObject: boolean field ──────────────────────────────────────────

  @Test
  def recordToJsObject_withBooleanField_serializes(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("flag", java.lang.Boolean.TRUE)

    val result = CourierFormats.recordToJsObject(dataMap, boolRecordSchema)
    assertResult(JsBoolean(true))((result \ "flag").get)
  }

  // ─── recordToJsObject: float field ────────────────────────────────────────────

  @Test
  def recordToJsObject_withFloatField_serializes(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("f", java.lang.Float.valueOf(1.5f))

    val result = CourierFormats.recordToJsObject(dataMap, floatRecordSchema)
    val jsVal = (result \ "f").get
    assert(jsVal.isInstanceOf[JsNumber])
  }

  // ─── recordToJsObject: string field ──────────────────────────────────────────

  @Test
  def recordToJsObject_withStringField_serializes(): Unit = {
    val simpleSchema = DataTemplateUtil.parseSchema(
      """{"name":"S","type":"record","fields":[{"name":"msg","type":"string"}]}"""
    ).asInstanceOf[RecordDataSchema]
    val dataMap = new DataMap()
    dataMap.put("msg", "hello")

    val result = CourierFormats.recordToJsObject(dataMap, simpleSchema)
    assertResult(JsString("hello"))((result \ "msg").get)
  }

  // ─── recordToJsObject: bytes field ────────────────────────────────────────────

  @Test
  def recordToJsObject_withBytesField_serializesAsAvroString(): Unit = {
    val dataMap = new DataMap()
    val bs = ByteString.copyString("test", "UTF-8")
    dataMap.put("b", bs)

    val result = CourierFormats.recordToJsObject(dataMap, bytesRecordSchema)
    val jsVal = (result \ "b").get
    assert(jsVal.isInstanceOf[JsString])
  }

  // ─── jsObjectToRecord: bytes field ───────────────────────────────────────────

  @Test
  def jsObjectToRecord_withBytesField_deserializes(): Unit = {
    val jsObj = JsObject(Seq("b" -> JsString("dGVzdA==")))
    val result = CourierFormats.jsObjectToRecord(jsObj, bytesRecordSchema)
    assert(result.get("b") != null)
  }

  // ─── jsObjectToRecord: float field ───────────────────────────────────────────

  @Test
  def jsObjectToRecord_withFloatField_deserializes(): Unit = {
    val jsObj = JsObject(Seq("f" -> JsNumber(BigDecimal("1.5"))))
    val result = CourierFormats.jsObjectToRecord(jsObj, floatRecordSchema)
    val floatVal = result.get("f").asInstanceOf[java.lang.Float]
    assert(floatVal != null)
    assert(Math.abs(floatVal - 1.5f) < 0.01f)
  }

  // ─── jsObjectToRecord: null value → field omitted ────────────────────────────

  @Test
  def jsObjectToRecord_withNullValue_fieldOmitted(): Unit = {
    val schema = DataTemplateUtil.parseSchema(
      """{"name":"Opt","type":"record","fields":[{"name":"opt","type":"string","optional":true}]}"""
    ).asInstanceOf[RecordDataSchema]
    val jsObj = JsObject(Seq("opt" -> JsNull))
    val result = CourierFormats.jsObjectToRecord(jsObj, schema)
    assert(result.get("opt") == null)
  }

  // ─── jsObjectToRecord: boolean field ──────────────────────────────────────────

  @Test
  def jsObjectToRecord_withBooleanField_deserializes(): Unit = {
    val jsObj = JsObject(Seq("flag" -> JsBoolean(false)))
    val result = CourierFormats.jsObjectToRecord(jsObj, boolRecordSchema)
    assertResult(java.lang.Boolean.FALSE)(result.get("flag"))
  }

  // ─── recordToJsValue: StringKey codec path ───────────────────────────────────

  @Test
  def recordToJsObject_outerWithStringKeyNestedRecord_serializes(): Unit = {
    // When a record has "codec": "StringKey", it should be serialized as a JsString
    val innerData = new DataMap()
    innerData.put("key1", "hello")
    innerData.put("key2", "world")
    val outerData = new DataMap()
    outerData.put("inner", innerData)

    val result = CourierFormats.recordToJsObject(outerData, outerStringKeySchema)
    val innerJs = (result \ "inner").get
    // The inner record has "codec":"StringKey" so it should be encoded as a string
    assert(innerJs.isInstanceOf[JsString])
  }

  // ─── jsObjectToRecord: StringKey codec path ──────────────────────────────────

  @Test
  def jsObjectToRecord_outerWithStringKeyNestedRecord_deserializes(): Unit = {
    val innerData = new DataMap()
    innerData.put("key1", "hello")
    innerData.put("key2", "world")
    val outerData = new DataMap()
    outerData.put("inner", innerData)

    // First serialize to get the StringKey form
    val serialized = CourierFormats.recordToJsObject(outerData, outerStringKeySchema)
    // Then deserialize back
    val result = CourierFormats.jsObjectToRecord(serialized, outerStringKeySchema)
    val innerResult = result.get("inner").asInstanceOf[DataMap]
    assertResult("hello")(innerResult.getString("key1"))
    assertResult("world")(innerResult.getString("key2"))
  }

  // ─── schemalessDataToJsValue: bytes ──────────────────────────────────────────

  @Test
  def dataMapToObj_bytesValue_serializedAsAvroString(): Unit = {
    val dataMap = new DataMap()
    val bs = ByteString.copyString("test", "UTF-8")
    dataMap.put("bytes", bs)
    val result = CourierFormats.dataMapToObj(dataMap)
    val jsVal = (result \ "bytes").get
    assert(jsVal.isInstanceOf[JsString])
  }

  // ─── arrayTemplateStringKeyFormat round-trip ──────────────────────────────────

  @Test
  def arrayTemplateStringKeyFormat_writesAndReads_roundTrip(): Unit = {
    import org.coursera.courier.data.IntArray
    val fmt = CourierFormats.arrayTemplateStringKeyFormat[IntArray]
    val dataList = new com.linkedin.data.DataList()
    dataList.add(Integer.valueOf(1))
    dataList.add(Integer.valueOf(2))
    dataList.add(Integer.valueOf(3))
    val arr = IntArray.build(dataList, org.coursera.courier.templates.DataTemplates.DataConversion.SetReadOnly)
    val key = fmt.writes(arr)
    val parsed = fmt.reads(key)
    assert(parsed.isDefined)
    val result = parsed.get
    assert(result.size == 3)
  }

  // ─── recordTemplateFormats: ReadException path ────────────────────────────────

  @Test
  def recordTemplateFormats_reads_missingRequiredField_returnsJsError(): Unit = {
    import CourierTestFixtures._
    val fmt = CourierFormats.recordTemplateFormats[MockRecord]
    // MockRecord requires "string" and "int" fields - provide neither
    val result = fmt.reads(JsObject(Seq.empty))
    assert(result.isError)
  }

  // ─── unionTemplateFormats: write path ────────────────────────────────────────

  @Test
  def unionTemplateFormats_writes_returnsJsObject(): Unit = {
    import CourierTestFixtures._
    val fmt = CourierFormats.unionTemplateFormats[MockTyperefUnion]
    val union = CourierSerializer.readUnion[MockTyperefUnion](mockTyperefUnionJson)
    val written = fmt.writes(union)
    assert(written.isInstanceOf[JsObject])
  }
}
