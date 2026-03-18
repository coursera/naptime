package org.coursera.naptime.courier

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json

/**
 * Extended tests for SchemaInference to cover branches missed by the existing test suite:
 * - inferSchemaFromWeakTypeTag
 * - JsValue / JsObject predefined type mapping
 * - Unrecognized type → deprecated fallback record schema
 * - Recursion guard (visitNamedSchema second call returns reference)
 * - UUID and other coerced predef types
 * - inferSchema where inner JsValue branch fires (non-JsObject schema result)
 */
class SchemaInferenceExtendedTest extends AssertionsForJUnit {

  // ─── inferSchemaFromWeakTypeTag ──────────────────────────────────────────────

  @Test
  def inferSchemaFromWeakTypeTag_returnsSchema(): Unit = {
    // This exercises the inferSchemaFromWeakTypeTag entry point (L51 coverage)
    val result = SchemaInference.inferSchemaFromWeakTypeTag[WithPrimitives]
    assert(result.isInstanceOf[JsObject])
    val resultStr = result.toString
    assert(resultStr.contains("WithPrimitives"))
  }

  // ─── JsValue predefined type mapping ────────────────────────────────────────

  @Test
  def inferSchema_jsValue_returnsAnyDataRecord(): Unit = {
    // JsValue maps to org.coursera.common.AnyData schema (a special record)
    val result = SchemaInference.inferSchema[play.api.libs.json.JsValue]
    assert(result.isInstanceOf[JsObject])
    val resultStr = result.toString
    assert(resultStr.contains("AnyData") || resultStr.contains("org.coursera.common"))
  }

  @Test
  def inferSchema_jsObject_returnsAnyDataRecord(): Unit = {
    // JsObject is a subtype of JsValue so it should also map to AnyData
    val result = SchemaInference.inferSchema[play.api.libs.json.JsObject]
    assert(result.isInstanceOf[JsObject])
    val resultStr = result.toString
    assert(resultStr.contains("AnyData") || resultStr.contains("org.coursera.common"))
  }

  // ─── UUID coerced predef type ────────────────────────────────────────────────

  @Test
  def inferSchema_uuid_returnsCoercedTyperef(): Unit = {
    val result = SchemaInference.inferSchema[java.util.UUID]
    assert(result.isInstanceOf[JsObject])
    val resultStr = result.toString
    // UUID maps to a typeref with coercer
    assert(
      resultStr.contains("UUID") || resultStr.contains("typeref") || resultStr.contains("string"))
  }

  // ─── String predef type ──────────────────────────────────────────────────────

  @Test
  def inferSchema_string_returnsStringPrimitive(): Unit = {
    // String is a predef without a name, so no visitNamedSchema call
    // But when used inside a record field it returns JsString("string")
    // Testing it at top-level triggers the non-JsObject branch in inferSchema (L65)
    val result = SchemaInference.inferSchema[String]
    // inferSchema wraps non-JsObject in {"type": value}
    assertResult(Json.obj("type" -> "string"))(result)
  }

  @Test
  def inferSchema_int_returnsIntPrimitive(): Unit = {
    val result = SchemaInference.inferSchema[Int]
    assertResult(Json.obj("type" -> "int"))(result)
  }

  @Test
  def inferSchema_long_returnsLongPrimitive(): Unit = {
    val result = SchemaInference.inferSchema[Long]
    assertResult(Json.obj("type" -> "long"))(result)
  }

  @Test
  def inferSchema_boolean_returnsBooleanPrimitive(): Unit = {
    val result = SchemaInference.inferSchema[Boolean]
    assertResult(Json.obj("type" -> "boolean"))(result)
  }

  @Test
  def inferSchema_float_returnsFloatPrimitive(): Unit = {
    val result = SchemaInference.inferSchema[Float]
    assertResult(Json.obj("type" -> "float"))(result)
  }

  @Test
  def inferSchema_double_returnsDoublePrimitive(): Unit = {
    val result = SchemaInference.inferSchema[Double]
    assertResult(Json.obj("type" -> "double"))(result)
  }

  // ─── Unrecognized type → deprecated fallback record ──────────────────────────

  @Test
  def inferSchema_unknownType_returnsFallbackRecordWithDeprecatedMessage(): Unit = {
    // An abstract class with no subclasses is not a union, not a Product,
    // and not a predef → hits the "We don't know how to infer" branch
    val result = SchemaInference.inferSchema[UnrecognizedAbstractClass]
    assert(result.isInstanceOf[JsObject])
    val resultStr = result.toString
    assert(resultStr.contains("deprecated"))
    assert(resultStr.contains("UnrecognizedAbstractClass"))
  }

  // ─── Recursive record (visitNamedSchema second call returns reference) ────────

  @Test
  def inferSchema_recursiveRecord_returnsSchemaWithReference(): Unit = {
    // SelfReferential has a field of its own type; the second traversal returns the name string
    val result = SchemaInference.inferSchema[SelfReferential]
    assert(result.isInstanceOf[JsObject])
    val resultStr = result.toString
    assert(resultStr.contains("SelfReferential"))
  }

  // ─── None / Some types ──────────────────────────────────────────────────────

  @Test
  def inferSchema_noneType_returnsNullSchema(): Unit = {
    // None.type → JsString("null"), which gets wrapped by top-level inferSchema
    val result = SchemaInference.inferSchema[None.type]
    assertResult(Json.obj("type" -> "null"))(result)
  }

  // ─── Map with non-string keys ────────────────────────────────────────────────

  @Test
  def inferSchema_mapWithIntKeys_includesKeysField(): Unit = {
    val result = SchemaInference.inferSchema[WithTypedKeyMaps]
    val resultStr = result.toString
    assert(resultStr.contains("keys"))
    assert(resultStr.contains("int"))
  }

  // ─── ScalaClassTraverser public extractPegasusSchemaIfPresent ────────────────

  @Test
  def scalaClassTraverser_extractPegasusSchemaIfPresent_nonCourierClass_returnsNone(): Unit = {
    import scala.reflect.runtime.{universe => ru}
    val traverser = new ScalaClassTraverser(ru.typeOf[WithPrimitives])
    val result = traverser.extractPegasusSchemaIfPresent()
    // WithPrimitives is a plain case class, not a DataTemplate
    assert(result.isEmpty)
  }

  @Test
  def scalaClassTraverser_inferSchema_withPrimitives_producesExpectedFields(): Unit = {
    import scala.reflect.runtime.{universe => ru}
    val traverser = new ScalaClassTraverser(ru.typeOf[WithPrimitives])
    val inferred = traverser.inferSchema()
    val resultStr = inferred.schema.toString
    assert(resultStr.contains("int"))
  }
}

// ─── Fixtures ────────────────────────────────────────────────────────────────

/** An abstract class with no known subclasses – hits the "unknown type" fallback. */
abstract class UnrecognizedAbstractClass

/** A case class that references itself in a field – tests recursion guard. */
case class SelfReferential(name: String, child: Option[SelfReferential] = None)
