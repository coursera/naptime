package org.coursera.naptime.courier

import com.linkedin.data.DataMap
import com.linkedin.data.schema.EnumDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import com.linkedin.data.template.DataTemplateUtil
import org.coursera.courier.templates.ScalaEnumTemplate
import org.coursera.courier.templates.ScalaEnumTemplateSymbol
import org.coursera.naptime.courier.Exceptions.WriteException
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString

/**
 * Additional coverage for CourierFormats paths not covered by existing tests:
 * - enumerationFormat reads/writes
 * - enumerationStringKeyFormat
 * - recordToJsObject: passthrough annotation
 * - unionToJsObject: entry count != 1 → WriteException
 * - jsObjectToRecord: passthrough annotation / unknown fields
 * - recordTemplateFormats: non-object reads → error
 * - typerefUnionToJsObject public overload
 * - jsObjectToTyperefUnion public overload
 * - recordTemplateStringKeyFormat
 */
class CourierFormatsMoreTest extends AssertionsForJUnit {

  import CourierTestFixtures._

  // ─── Schemas ─────────────────────────────────────────────────────────────────

  private val simpleRecordSchema = DataTemplateUtil
    .parseSchema(
      """{"name":"SR","type":"record","fields":[{"name":"val","type":"string"}]}"""
    )
    .asInstanceOf[RecordDataSchema]

  private val passthroughRecordSchema = DataTemplateUtil
    .parseSchema(
      """
      |{
      |  "name": "Passthrough",
      |  "type": "record",
      |  "fields": [{"name": "known", "type": "string"}],
      |  "passthroughExempt": true
      |}
      |""".stripMargin
    )
    .asInstanceOf[RecordDataSchema]

  // ─── enumerationFormat ────────────────────────────────────────────────────────

  @Test
  def enumerationFormat_reads_knownValue_returnsSuccess(): Unit = {
    val fmt = CourierFormats.enumerationFormat(TestCourierEnum)
    val result = fmt.reads(JsString("ALPHA"))
    assert(result.isSuccess)
    assert(result.get == TestCourierEnum.ALPHA)
  }

  @Test
  def enumerationFormat_reads_unknownValue_returnsError(): Unit = {
    val fmt = CourierFormats.enumerationFormat(TestCourierEnum)
    val result = fmt.reads(JsString("UNKNOWN_XYZ"))
    assert(result.isError)
  }

  @Test
  def enumerationFormat_reads_nonString_returnsError(): Unit = {
    val fmt = CourierFormats.enumerationFormat(TestCourierEnum)
    val result = fmt.reads(JsNumber(42))
    assert(result.isError)
  }

  @Test
  def enumerationFormat_writes_returnsJsString(): Unit = {
    val fmt = CourierFormats.enumerationFormat(TestCourierEnum)
    val result = fmt.writes(TestCourierEnum.BETA)
    assertResult(JsString("BETA"))(result)
  }

  @Test
  def enumerationFormat_writes_alpha_returnsAlphaString(): Unit = {
    val fmt = CourierFormats.enumerationFormat(TestCourierEnum)
    val result = fmt.writes(TestCourierEnum.ALPHA)
    assertResult(JsString("ALPHA"))(result)
  }

  // ─── enumerationStringKeyFormat ──────────────────────────────────────────────

  @Test
  def enumerationStringKeyFormat_reads_knownValue_returnsSome(): Unit = {
    import org.coursera.common.stringkey.StringKey
    val fmt = CourierFormats.enumerationStringKeyFormat(TestCourierEnum)
    val result = fmt.reads(StringKey("ALPHA"))
    assert(result.isDefined)
    assert(result.get == TestCourierEnum.ALPHA)
  }

  @Test
  def enumerationStringKeyFormat_reads_unknownValue_returnsNone(): Unit = {
    import org.coursera.common.stringkey.StringKey
    // The implementation uses Try(...).toOption so exceptions are caught → returns None
    val fmt = CourierFormats.enumerationStringKeyFormat(TestCourierEnum)
    val result = fmt.reads(StringKey("DOES_NOT_EXIST_XYZ"))
    assert(result.isEmpty)
  }

  @Test
  def enumerationStringKeyFormat_writes_returnsStringKey(): Unit = {
    import org.coursera.common.stringkey.StringKey
    val fmt = CourierFormats.enumerationStringKeyFormat(TestCourierEnum)
    val result = fmt.writes(TestCourierEnum.BETA)
    assertResult(StringKey("BETA"))(result)
  }

  // ─── recordToJsObject: passthrough annotation ────────────────────────────────

  @Test
  def recordToJsObject_passthroughAnnotation_includesUnknownFields(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("known", "value")
    dataMap.put("extraField", "extraValue")
    val result = CourierFormats.recordToJsObject(dataMap, passthroughRecordSchema)
    assert((result \ "extraField").isDefined)
    assertResult(JsString("extraValue"))((result \ "extraField").get)
  }

  @Test
  def recordToJsObject_noPassthrough_omitsUnknownFields(): Unit = {
    val dataMap = new DataMap()
    dataMap.put("val", "hello")
    dataMap.put("extra", "ignored")
    val result = CourierFormats.recordToJsObject(dataMap, simpleRecordSchema)
    assert(!(result \ "extra").isDefined)
  }

  // ─── unionToJsObject public overload: entry count != 1 → WriteException ──────

  @Test
  def unionToJsObject_multipleEntries_throwsWriteException(): Unit = {
    val unionSchema = DataTemplateUtil
      .parseSchema(
        """["string","int"]"""
      )
      .asInstanceOf[UnionDataSchema]

    val dataMap = new DataMap()
    dataMap.put("string", "hello")
    dataMap.put("int", Integer.valueOf(42))

    intercept[WriteException] {
      CourierFormats.unionToJsObject(dataMap, unionSchema)
    }
  }

  @Test
  def unionToJsObject_zeroEntries_throwsWriteException(): Unit = {
    val unionSchema = DataTemplateUtil
      .parseSchema(
        """["string","int"]"""
      )
      .asInstanceOf[UnionDataSchema]

    val dataMap = new DataMap()
    intercept[WriteException] {
      CourierFormats.unionToJsObject(dataMap, unionSchema)
    }
  }

  // ─── recordTemplateFormats: non-object reads → error ────────────────────────

  @Test
  def recordTemplateFormats_reads_nonObject_returnsJsError(): Unit = {
    val fmt = CourierFormats.recordTemplateFormats[TypedDefinitionRecord]
    val result = fmt.reads(JsString("not an object"))
    assert(result.isError)
  }

  @Test
  def recordTemplateFormats_writes_returnsJsObject(): Unit = {
    val record = CourierSerializer.read[TypedDefinitionRecord](typedDefinitionJson)
    val fmt = CourierFormats.recordTemplateFormats[TypedDefinitionRecord]
    val written = fmt.writes(record)
    assert(written.isInstanceOf[JsObject])
  }

  // ─── unionTemplateFormats: reads error path ──────────────────────────────────

  @Test
  def unionTemplateFormats_reads_nonObject_returnsJsError(): Unit = {
    val fmt = CourierFormats.unionTemplateFormats[MockTyperefUnion]
    val result = fmt.reads(JsString("not an object"))
    assert(result.isError)
  }

  // ─── jsObjectToRecord: passthrough annotation ─────────────────────────────────

  @Test
  def jsObjectToRecord_passthroughAnnotation_preservesUnknownFields(): Unit = {
    val jsObj = JsObject(Seq("known" -> JsString("value"), "extra" -> JsString("extraVal")))
    val result = CourierFormats.jsObjectToRecord(jsObj, passthroughRecordSchema)
    assertResult("value")(result.getString("known"))
    assertResult("extraVal")(result.get("extra").asInstanceOf[String])
  }

  @Test
  def jsObjectToRecord_noPassthrough_omitsUnknownFields(): Unit = {
    val jsObj = JsObject(Seq("val" -> JsString("hello"), "extra" -> JsString("ignored")))
    val result = CourierFormats.jsObjectToRecord(jsObj, simpleRecordSchema)
    assertResult("hello")(result.getString("val"))
    assert(result.get("extra") == null)
  }

  // ─── typerefUnionToJsObject public overload ───────────────────────────────────

  @Test
  def typerefUnionToJsObject_withTypedDef_writes(): Unit = {
    val typerefField = typedDefinitionSchema.getField("typedDefinition")
    val typerefSchema = typerefField.getType.asInstanceOf[TyperefDataSchema]
    val unionSchema = typerefSchema.getDereferencedDataSchema.asInstanceOf[UnionDataSchema]

    val inner = new DataMap()
    inner.put("x", Integer.valueOf(1))
    val dataMap = new DataMap()
    dataMap.put("UnionMember1", inner)

    val result = CourierFormats.typerefUnionToJsObject(typerefSchema, dataMap, unionSchema)
    assert(result.isInstanceOf[JsObject])
    // should produce a typedDefinition style response
    assert((result \ "typeName").isDefined)
  }

  // ─── jsObjectToTyperefUnion public overload ──────────────────────────────────

  @Test
  def jsObjectToTyperefUnion_withTypedDef_reads(): Unit = {
    val typerefField = typedDefinitionSchema.getField("typedDefinition")
    val typerefSchema = typerefField.getType.asInstanceOf[TyperefDataSchema]
    val unionSchema = typerefSchema.getDereferencedDataSchema.asInstanceOf[UnionDataSchema]

    val jsObj = JsObject(
      Seq(
        "typeName" -> JsString("memberOne"),
        "definition" -> JsObject(Seq("x" -> JsNumber(1)))
      ))

    val result = CourierFormats.jsObjectToTyperefUnion(typerefSchema, jsObj, unionSchema)
    assert(result.isInstanceOf[DataMap])
    assert(result.containsKey("UnionMember1"))
  }

  // ─── recordTemplateStringKeyFormat ────────────────────────────────────────────

  @Test
  def recordTemplateStringKeyFormat_writesAndReads_roundTrip(): Unit = {
    val fmt = CourierFormats.recordTemplateStringKeyFormat[MockRecord]
    val record = CourierSerializer.read[MockRecord](
      """{"string": "hello", "int": 42}"""
    )
    val key = fmt.writes(record)
    val parsed = fmt.reads(key)
    assert(parsed.isDefined)
    assertResult("hello")(parsed.get.data().getString("string"))
    assertResult(Integer.valueOf(42))(parsed.get.data().getInteger("int"))
  }

  @Test
  def recordTemplateStringKeyFormat_invalidInput_returnsNone(): Unit = {
    import org.coursera.common.stringkey.StringKey
    val fmt = CourierFormats.recordTemplateStringKeyFormat[MockRecord]
    // MockRecord has two fields (string and int), providing wrong tuple length
    val result = fmt.reads(StringKey("tooFewFields"))
    assert(result.isEmpty)
  }
}

// ─── TestCourierEnum fixture ──────────────────────────────────────────────────

sealed abstract class TestCourierEnum(name: String, properties: Option[DataMap])
    extends ScalaEnumTemplateSymbol(name, properties)

object TestCourierEnum extends ScalaEnumTemplate[TestCourierEnum] {
  case object ALPHA extends TestCourierEnum("ALPHA", None)
  case object BETA extends TestCourierEnum("BETA", None)
  case object $UNKNOWN extends TestCourierEnum("$UNKNOWN", None)

  override def withName(s: String): TestCourierEnum =
    symbols.find(_.toString == s).getOrElse {
      throw new NoSuchElementException(s"No value found for '$s'")
    }

  val SCHEMA: EnumDataSchema = DataTemplateUtil
    .parseSchema(
      """{"type":"enum","name":"TestCourierEnum","namespace":"org.coursera.naptime.courier","symbols":["ALPHA","BETA"]}""")
    .asInstanceOf[EnumDataSchema]
}
