package org.coursera.naptime.courier

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.Null
import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.template.DataTemplateUtil
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Extended tests for StringKeyCodec to cover uncovered branches:
 * - Parser: boolean/long/float/double/null type parsing
 * - Parser: bytes type throws IOException
 * - Parser: unknown schema type throws IOException
 * - Parser: prefix mismatch throws IOException
 * - Generator: missing required field throws IOException
 * - Generator: ByteString throws IOException
 * - Generator: unknown schema type throws IOException
 * - requireSchemaType: incompatible schema type throws IllegalArgumentException
 */
class StringKeyCodecExtendedTest extends AssertionsForJUnit {

  // ─── Schemas ─────────────────────────────────────────────────────────────────

  private val booleanRecordSchema = DataTemplateUtil.parseSchema(
    """{"name":"BoolRec","type":"record","fields":[{"name":"flag","type":"boolean"}]}"""
  ).asInstanceOf[RecordDataSchema]

  private val intRecordSchema = DataTemplateUtil.parseSchema(
    """{"name":"IntRec","type":"record","fields":[{"name":"num","type":"int"}]}"""
  ).asInstanceOf[RecordDataSchema]

  private val longRecordSchema = DataTemplateUtil.parseSchema(
    """{"name":"LongRec","type":"record","fields":[{"name":"num","type":"long"}]}"""
  ).asInstanceOf[RecordDataSchema]

  private val floatRecordSchema = DataTemplateUtil.parseSchema(
    """{"name":"FloatRec","type":"record","fields":[{"name":"val","type":"float"}]}"""
  ).asInstanceOf[RecordDataSchema]

  private val doubleRecordSchema = DataTemplateUtil.parseSchema(
    """{"name":"DoubleRec","type":"record","fields":[{"name":"val","type":"double"}]}"""
  ).asInstanceOf[RecordDataSchema]

  private val nullRecordSchema = DataTemplateUtil.parseSchema(
    """{"name":"NullRec","type":"record","fields":[{"name":"nothing","type":"null"}]}"""
  ).asInstanceOf[RecordDataSchema]

  private val enumRecordSchema = DataTemplateUtil.parseSchema(
    """
      |{
      |  "name":"EnumRec","type":"record",
      |  "fields":[{
      |    "name":"color",
      |    "type":{"name":"Color","type":"enum","symbols":["RED","GREEN"]}
      |  }]
      |}
      |""".stripMargin
  ).asInstanceOf[RecordDataSchema]

  private val stringArraySchema = DataTemplateUtil.parseSchema(
    """{"name":"StrArr","type":"typeref","ref":{"type":"array","items":"string"}}"""
  ).asInstanceOf[TyperefDataSchema]

  private val intArraySchema = DataTemplateUtil.parseSchema(
    """{"name":"IntArr","type":"typeref","ref":{"type":"array","items":"int"}}"""
  ).asInstanceOf[TyperefDataSchema]

  private val longArraySchema = DataTemplateUtil.parseSchema(
    """{"name":"LongArr","type":"typeref","ref":{"type":"array","items":"long"}}"""
  ).asInstanceOf[TyperefDataSchema]

  private val floatArraySchema = DataTemplateUtil.parseSchema(
    """{"name":"FloatArr","type":"typeref","ref":{"type":"array","items":"float"}}"""
  ).asInstanceOf[TyperefDataSchema]

  private val doubleArraySchema = DataTemplateUtil.parseSchema(
    """{"name":"DoubleArr","type":"typeref","ref":{"type":"array","items":"double"}}"""
  ).asInstanceOf[TyperefDataSchema]

  private val booleanArraySchema = DataTemplateUtil.parseSchema(
    """{"name":"BoolArr","type":"typeref","ref":{"type":"array","items":"boolean"}}"""
  ).asInstanceOf[TyperefDataSchema]

  private val nullArraySchema = DataTemplateUtil.parseSchema(
    """{"name":"NullArr","type":"typeref","ref":{"type":"array","items":"null"}}"""
  ).asInstanceOf[TyperefDataSchema]

  // ─── Parser: various primitive types ────────────────────────────────────────

  @Test
  def parser_parsesBoolean_trueValue(): Unit = {
    val codec = new StringKeyCodec(booleanRecordSchema)
    val result = codec.bytesToMap("true".getBytes(StringKeyCodec.charset))
    assertResult(java.lang.Boolean.TRUE)(result.get("flag"))
  }

  @Test
  def parser_parsesBoolean_falseValue(): Unit = {
    val codec = new StringKeyCodec(booleanRecordSchema)
    val result = codec.bytesToMap("false".getBytes(StringKeyCodec.charset))
    assertResult(java.lang.Boolean.FALSE)(result.get("flag"))
  }

  @Test
  def parser_parsesInt_value(): Unit = {
    val codec = new StringKeyCodec(intRecordSchema)
    val result = codec.bytesToMap("42".getBytes(StringKeyCodec.charset))
    assertResult(Integer.valueOf(42))(result.get("num"))
  }

  @Test
  def parser_parsesLong_value(): Unit = {
    val codec = new StringKeyCodec(longRecordSchema)
    val result = codec.bytesToMap("123456789012".getBytes(StringKeyCodec.charset))
    assertResult(java.lang.Long.valueOf(123456789012L))(result.get("num"))
  }

  @Test
  def parser_parsesFloat_value(): Unit = {
    val codec = new StringKeyCodec(floatRecordSchema)
    val result = codec.bytesToMap("1.5".getBytes(StringKeyCodec.charset))
    assertResult(java.lang.Float.valueOf(1.5f))(result.get("val"))
  }

  @Test
  def parser_parsesDouble_value(): Unit = {
    val codec = new StringKeyCodec(doubleRecordSchema)
    val result = codec.bytesToMap("3.14".getBytes(StringKeyCodec.charset))
    assertResult(java.lang.Double.valueOf(3.14))(result.get("val"))
  }

  @Test
  def parser_parsesNull_value(): Unit = {
    val codec = new StringKeyCodec(nullRecordSchema)
    val result = codec.bytesToMap("null".getBytes(StringKeyCodec.charset))
    assertResult(Null.getInstance())(result.get("nothing"))
  }

  @Test
  def parser_parsesEnum_value(): Unit = {
    val codec = new StringKeyCodec(enumRecordSchema)
    val result = codec.bytesToMap("RED".getBytes(StringKeyCodec.charset))
    assertResult("RED")(result.get("color"))
  }

  // ─── Parser: array of various primitive types ────────────────────────────────

  @Test
  def parser_parsesIntArray_values(): Unit = {
    val codec = new StringKeyCodec(intArraySchema)
    val result = codec.bytesToList("1,2,3".getBytes(StringKeyCodec.charset))
    assertResult(3)(result.size())
    assertResult(Integer.valueOf(1))(result.get(0))
    assertResult(Integer.valueOf(2))(result.get(1))
    assertResult(Integer.valueOf(3))(result.get(2))
  }

  @Test
  def parser_parsesLongArray_values(): Unit = {
    val codec = new StringKeyCodec(longArraySchema)
    val result = codec.bytesToList("100,200".getBytes(StringKeyCodec.charset))
    assertResult(2)(result.size())
    assertResult(java.lang.Long.valueOf(100L))(result.get(0))
  }

  @Test
  def parser_parsesFloatArray_values(): Unit = {
    val codec = new StringKeyCodec(floatArraySchema)
    val result = codec.bytesToList("1.0,2.0".getBytes(StringKeyCodec.charset))
    assertResult(2)(result.size())
    assertResult(java.lang.Float.valueOf(1.0f))(result.get(0))
  }

  @Test
  def parser_parsesDoubleArray_values(): Unit = {
    val codec = new StringKeyCodec(doubleArraySchema)
    val result = codec.bytesToList("3.14,2.71".getBytes(StringKeyCodec.charset))
    assertResult(2)(result.size())
    assertResult(java.lang.Double.valueOf(3.14))(result.get(0))
  }

  @Test
  def parser_parsesBooleanArray_values(): Unit = {
    val codec = new StringKeyCodec(booleanArraySchema)
    val result = codec.bytesToList("true,false".getBytes(StringKeyCodec.charset))
    assertResult(2)(result.size())
    assertResult(java.lang.Boolean.TRUE)(result.get(0))
    assertResult(java.lang.Boolean.FALSE)(result.get(1))
  }

  @Test
  def parser_parsesNullArray_values(): Unit = {
    val codec = new StringKeyCodec(nullArraySchema)
    val result = codec.bytesToList("null".getBytes(StringKeyCodec.charset))
    assertResult(1)(result.size())
    assertResult(Null.getInstance())(result.get(0))
  }

  // ─── Parser: bytes type throws IOException ───────────────────────────────────

  @Test
  def parser_bytesType_throwsIOException(): Unit = {
    val bytesRecordSchema = DataTemplateUtil.parseSchema(
      """{"name":"BytesRec","type":"record","fields":[{"name":"data","type":"bytes"}]}"""
    ).asInstanceOf[RecordDataSchema]

    val codec = new StringKeyCodec(bytesRecordSchema)
    intercept[IOException] {
      codec.bytesToMap("somedata".getBytes(StringKeyCodec.charset))
    }
  }

  // ─── Parser: prefix mismatch throws IOException ──────────────────────────────

  @Test
  def parser_prefixMismatch_throwsIOException(): Unit = {
    val stringRecordSchema = DataTemplateUtil.parseSchema(
      """{"name":"StrRec","type":"record","fields":[{"name":"val","type":"string"}]}"""
    ).asInstanceOf[RecordDataSchema]

    val codec = new StringKeyCodec(stringRecordSchema, Some("expectedPrefix"))
    intercept[IOException] {
      codec.bytesToMap("wrongPrefix~value".getBytes(StringKeyCodec.charset))
    }
  }

  // ─── Parser: wrong tuple length throws IOException ──────────────────────────

  @Test
  def parser_tupleLengthMismatch_throwsIOException(): Unit = {
    val tuple2Schema = DataTemplateUtil.parseSchema(
      """{"name":"T2","type":"record","fields":[{"name":"a","type":"string"},{"name":"b","type":"string"}]}"""
    ).asInstanceOf[RecordDataSchema]

    val codec = new StringKeyCodec(tuple2Schema)
    // Provide only one value instead of two
    intercept[IOException] {
      codec.bytesToMap("onlyone".getBytes(StringKeyCodec.charset))
    }
  }

  // ─── Generator: missing required field throws IOException ────────────────────

  @Test
  def generator_missingField_throwsIOException(): Unit = {
    val schema = DataTemplateUtil.parseSchema(
      """{"name":"Req","type":"record","fields":[{"name":"required","type":"string"}]}"""
    ).asInstanceOf[RecordDataSchema]

    val codec = new StringKeyCodec(schema)
    val emptyDataMap = new DataMap()
    intercept[IOException] {
      codec.mapToBytes(emptyDataMap)
    }
  }

  // ─── Generator: bytes type in list throws IOException ────────────────────────

  @Test
  def generator_bytesInList_throwsIOException(): Unit = {
    val bytesArraySchema = DataTemplateUtil.parseSchema(
      """{"name":"BytesArr","type":"typeref","ref":{"type":"array","items":"bytes"}}"""
    ).asInstanceOf[TyperefDataSchema]

    val codec = new StringKeyCodec(bytesArraySchema)
    val dataList = new DataList()
    dataList.add(com.linkedin.data.ByteString.copyString("test", "UTF-8"))
    intercept[IOException] {
      codec.listToBytes(dataList)
    }
  }

  // ─── requireSchemaType: incompatible type throws IllegalArgumentException ─────

  @Test
  def requireSchemaType_incompatible_throwsIllegalArgumentException(): Unit = {
    // StringKeyCodec wraps a record schema but we call writeList (which requires ArrayDataSchema)
    val recordSchema = DataTemplateUtil.parseSchema(
      """{"name":"Rec","type":"record","fields":[{"name":"x","type":"string"}]}"""
    ).asInstanceOf[RecordDataSchema]

    val codec = new StringKeyCodec(recordSchema)
    val dataList = new DataList()
    intercept[IllegalArgumentException] {
      codec.listToBytes(dataList)
    }
  }

  @Test
  def requireSchemaType_incompatibleForMap_throwsIllegalArgumentException(): Unit = {
    // StringKeyCodec wraps an array schema but we call writeMap (which requires RecordDataSchema)
    val arraySchema = DataTemplateUtil.parseSchema(
      """{"name":"Arr","type":"typeref","ref":{"type":"array","items":"string"}}"""
    ).asInstanceOf[TyperefDataSchema]

    val codec = new StringKeyCodec(arraySchema)
    val dataMap = new DataMap()
    intercept[IllegalArgumentException] {
      codec.mapToBytes(dataMap)
    }
  }

  // ─── Round-trip: with prefix ─────────────────────────────────────────────────

  @Test
  def roundTrip_withPrefix_succeeds(): Unit = {
    val schema = DataTemplateUtil.parseSchema(
      """{"name":"Prefixed","type":"record","fields":[{"name":"val","type":"string"}]}"""
    ).asInstanceOf[RecordDataSchema]

    val codec = new StringKeyCodec(schema, Some("myPrefix"))
    val dataMap = new DataMap()
    dataMap.put("val", "hello")

    val bytes = codec.mapToBytes(dataMap)
    val result = codec.bytesToMap(bytes)
    assertResult("hello")(result.get("val"))
  }

  // ─── readList / readMap using InputStream variants ────────────────────────────

  @Test
  def readList_fromInputStream_parsesCorrectly(): Unit = {
    val codec = new StringKeyCodec(stringArraySchema)
    val input = new ByteArrayInputStream("a,b".getBytes(StringKeyCodec.charset))
    val result = codec.readList(input)
    assertResult(2)(result.size())
    assertResult("a")(result.get(0))
    assertResult("b")(result.get(1))
  }

  @Test
  def readMap_fromInputStream_parsesCorrectly(): Unit = {
    val schema = DataTemplateUtil.parseSchema(
      """{"name":"StrRec2","type":"record","fields":[{"name":"val","type":"string"}]}"""
    ).asInstanceOf[RecordDataSchema]

    val codec = new StringKeyCodec(schema)
    val input = new ByteArrayInputStream("hello".getBytes(StringKeyCodec.charset))
    val result = codec.readMap(input)
    assertResult("hello")(result.get("val"))
  }
}
