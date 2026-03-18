package org.coursera.naptime.courier

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.Null
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.template.DataTemplateUtil
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString

/**
 * Extended tests for CourierFormats to cover uncovered branches:
 * - dataMapToObj / objToDataMap (schemaless paths)
 * - schemalessDataToJsValue and its various type branches
 * - arrayToJsValue / mapToJsValue via write paths
 * - getUnionMemberTypeName, typeNameToMemberSchema paths
 */
class CourierFormatsExtendedTest extends AssertionsForJUnit {

  // ─── dataMapToObj / objToDataMap (schemaless serialization) ─────────────────────

  @Test
  def dataMapToObj_stringValue_serializedCorrectly(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("key", "value")
    val result = CourierFormats.dataMapToObj(dataMap)
    assertResult(JsObject(Seq("key" -> JsString("value"))))(result)
  }

  @Test
  def dataMapToObj_integerValue_serializedCorrectly(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("count", Integer.valueOf(42))
    val result = CourierFormats.dataMapToObj(dataMap)
    assertResult(JsObject(Seq("count" -> JsNumber(BigDecimal(42)))))(result)
  }

  @Test
  def dataMapToObj_longValue_serializedCorrectly(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("big", java.lang.Long.valueOf(Long.MaxValue))
    val result = CourierFormats.dataMapToObj(dataMap)
    assertResult(JsObject(Seq("big" -> JsNumber(BigDecimal(Long.MaxValue)))))(result)
  }

  @Test
  def dataMapToObj_floatValue_serializedCorrectly(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("ratio", java.lang.Float.valueOf(1.5f))
    val result = CourierFormats.dataMapToObj(dataMap)
    // Float is serialized as BigDecimal
    assert(result.value.contains("ratio"))
  }

  @Test
  def dataMapToObj_doubleValue_serializedCorrectly(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("pi", java.lang.Double.valueOf(3.14))
    val result = CourierFormats.dataMapToObj(dataMap)
    assert(result.value.contains("pi"))
  }

  @Test
  def dataMapToObj_booleanValue_serializedCorrectly(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("flag", java.lang.Boolean.TRUE)
    val result = CourierFormats.dataMapToObj(dataMap)
    assertResult(JsObject(Seq("flag" -> JsBoolean(true))))(result)
  }

  @Test
  def dataMapToObj_nullValue_serializedAsJsNull(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("missing", Null.getInstance)
    val result = CourierFormats.dataMapToObj(dataMap)
    assertResult(JsObject(Seq("missing" -> JsNull)))(result)
  }

  @Test
  def dataMapToObj_nestedDataMap_serializedRecursively(): Unit = {
    val inner = new DataMap()
    inner.put("x", Integer.valueOf(1))
    val outer = new DataMap()
    outer.put("nested", inner)
    val result = CourierFormats.dataMapToObj(outer)
    assertResult(JsObject(Seq("nested" -> JsObject(Seq("x" -> JsNumber(1))))))(result)
  }

  @Test
  def dataMapToObj_dataList_serializedAsJsArray(): Unit = {
    val list = new DataList()
    list.add("item1")
    list.add("item2")
    val dataMap = new DataMap()
    dataMap.put("items", list)
    val result = CourierFormats.dataMapToObj(dataMap)
    assertResult(JsObject(Seq("items" -> JsArray(Seq(JsString("item1"), JsString("item2"))))))(
      result)
  }

  @Test
  def objToDataMap_stringValue_deserializedCorrectly(): Unit = {
    val jsObj = JsObject(Seq("key" -> JsString("value")))
    val result = CourierFormats.objToDataMap(jsObj)
    assertResult("value")(result.getString("key"))
  }

  @Test
  def objToDataMap_numberValue_deserializedCorrectly(): Unit = {
    val jsObj = JsObject(Seq("count" -> JsNumber(BigDecimal(5))))
    val result = CourierFormats.objToDataMap(jsObj)
    assertResult(Integer.valueOf(5))(result.get("count"))
  }

  @Test
  def objToDataMap_booleanValue_deserializedCorrectly(): Unit = {
    val jsObj = JsObject(Seq("flag" -> JsBoolean(true)))
    val result = CourierFormats.objToDataMap(jsObj)
    assertResult(java.lang.Boolean.TRUE)(result.get("flag"))
  }

  @Test
  def objToDataMap_nullValue_deserializedCorrectly(): Unit = {
    val jsObj = JsObject(Seq("empty" -> JsNull))
    val result = CourierFormats.objToDataMap(jsObj)
    assertResult(Null.getInstance)(result.get("empty"))
  }

  @Test
  def objToDataMap_nestedObject_deserializedRecursively(): Unit = {
    val jsObj = JsObject(Seq("nested" -> JsObject(Seq("x" -> JsNumber(1)))))
    val result = CourierFormats.objToDataMap(jsObj)
    val nested = result.get("nested").asInstanceOf[DataMap]
    assertResult(Integer.valueOf(1))(nested.get("x"))
  }

  @Test
  def objToDataMap_arrayValue_deserializedCorrectly(): Unit = {
    val jsObj = JsObject(Seq("items" -> JsArray(Seq(JsString("a"), JsString("b")))))
    val result = CourierFormats.objToDataMap(jsObj)
    val list = result.get("items").asInstanceOf[DataList]
    assertResult(2)(list.size())
    assertResult("a")(list.get(0))
    assertResult("b")(list.get(1))
  }

  // ─── bigDecimalToNumber edge cases ────────────────────────────────────────────

  @Test
  def bigDecimalToNumber_intValue_returnsInt(): Unit = {
    val result = CourierFormats.bigDecimalToNumber(BigDecimal(10))
    assert(result.isInstanceOf[java.lang.Integer])
    assertResult(10)(result.intValue)
  }

  @Test
  def bigDecimalToNumber_longValue_returnsLong(): Unit = {
    // Use a value larger than Int.MaxValue
    val big = BigDecimal(Int.MaxValue.toLong + 1)
    val result = CourierFormats.bigDecimalToNumber(big)
    assert(result.isInstanceOf[java.lang.Long])
  }

  @Test
  def bigDecimalToNumber_floatValue_returnsFloat(): Unit = {
    // A value that's exact as float but not int or long
    val result = CourierFormats.bigDecimalToNumber(BigDecimal("1.5"))
    assert(result.isInstanceOf[java.lang.Float] || result.isInstanceOf[java.lang.Double])
  }

  @Test
  def bigDecimalToNumber_infinityValue_throwsReadException(): Unit = {
    import org.coursera.naptime.courier.Exceptions.ReadException
    val hugeValue = BigDecimal("1e999")
    intercept[ReadException] {
      CourierFormats.bigDecimalToNumber(hugeValue)
    }
  }

  // ─── recordToJsObject (public overload) ────────────────────────────────────────

  @Test
  def recordToJsObject_simpleRecord_serializes(): Unit = {
    val schema = DataTemplateUtil
      .parseSchema(
        """{"name":"S","type":"record","fields":[{"name":"x","type":"int"}]}"""
      )
      .asInstanceOf[RecordDataSchema]
    val dataMap = new DataMap()
    dataMap.put("x", Integer.valueOf(7))
    val result = CourierFormats.recordToJsObject(dataMap, schema)
    assertResult(JsObject(Seq("x" -> JsNumber(7))))(result)
  }

  // ─── jsObjectToRecord (public overload) ──────────────────────────────────────

  @Test
  def jsObjectToRecord_simpleRecord_deserializes(): Unit = {
    val schema = DataTemplateUtil
      .parseSchema(
        """{"name":"R","type":"record","fields":[{"name":"y","type":"string"}]}"""
      )
      .asInstanceOf[RecordDataSchema]
    val jsObj = JsObject(Seq("y" -> JsString("hello")))
    val result = CourierFormats.jsObjectToRecord(jsObj, schema)
    assertResult("hello")(result.getString("y"))
  }

  // ─── jsObjectToUnion (public overload) ───────────────────────────────────────

  @Test
  def jsObjectToUnion_singleEntry_deserializes(): Unit = {
    import com.linkedin.data.schema.UnionDataSchema
    val unionSchema = DataTemplateUtil
      .parseSchema("""["string","int"]""")
      .asInstanceOf[UnionDataSchema]
    val jsObj = JsObject(Seq("string" -> JsString("hello")))
    val result = CourierFormats.jsObjectToUnion(jsObj, unionSchema)
    assertResult("hello")(result.getString("string"))
  }

  @Test
  def jsObjectToUnion_multipleEntries_throwsReadException(): Unit = {
    import com.linkedin.data.schema.UnionDataSchema
    import org.coursera.naptime.courier.Exceptions.ReadException
    val unionSchema = DataTemplateUtil
      .parseSchema("""["string","int"]""")
      .asInstanceOf[UnionDataSchema]
    val jsObj = JsObject(Seq("string" -> JsString("a"), "int" -> JsNumber(1)))
    intercept[ReadException] {
      CourierFormats.jsObjectToUnion(jsObj, unionSchema)
    }
  }
}
