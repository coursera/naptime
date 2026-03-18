package org.coursera.naptime.model

import java.util.UUID

import org.coursera.common.stringkey.StringKey
import org.coursera.common.stringkey.StringKeyFormat
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.libs.json.OWrites

/**
 * Extended tests for KeyFormat to push coverage from ~20% to 90%+.
 * Exercises: idAsStringOnly, idAsStringWithFields, caseClassFormat, idAsPrimitive,
 * withFallbackReads, and the primitive formats.
 */
class KeyFormatExtendedTest extends AssertionsForJUnit {

  // ─── helpers ──────────────────────────────────────────────────────────────────

  case class SimpleId(value: String)

  object SimpleId {
    implicit val stringKeyFormat: StringKeyFormat[SimpleId] =
      StringKeyFormat[SimpleId](
        k => Some(SimpleId(k.key)),
        id => StringKey(id.value)
      )
    val keyFormat: KeyFormat[SimpleId] = KeyFormat.idAsStringOnly[SimpleId]
  }

  case class CompositeId(userId: Long, courseId: String)

  object CompositeId {
    implicit val stringKeyFormat: StringKeyFormat[CompositeId] =
      StringKeyFormat.caseClassFormat((apply _).tupled, unapply)

    val keyFormat: KeyFormat[CompositeId] = KeyFormat.idAsStringWithFields(
      OWrites[CompositeId] { id =>
        Json.obj("userId" -> id.userId, "courseId" -> id.courseId)
      }
    )
  }

  // ─── idAsStringOnly ─────────────────────────────────────────────────────────

  @Test
  def idAsStringOnly_writes_producesIdField(): Unit = {
    val id = SimpleId("abc")
    val result = SimpleId.keyFormat.format.writes(id)
    assert(result.isInstanceOf[JsObject])
    assertResult(JsString("abc"))((result \ "id").get)
  }

  @Test
  def idAsStringOnly_reads_parsesIdField(): Unit = {
    val json = Json.obj("id" -> "abc")
    val result = SimpleId.keyFormat.format.reads(json)
    assert(result.isSuccess)
    assertResult(SimpleId("abc"))(result.get)
  }

  @Test
  def idAsStringOnly_reads_fromStringKey(): Unit = {
    val jsString = JsString("abc")
    val result = SimpleId.keyFormat.reads(jsString)
    assert(result.isSuccess)
    assertResult(SimpleId("abc"))(result.get)
  }

  @Test
  def idAsStringOnly_writes_toStringKey(): Unit = {
    val id = SimpleId("xyz")
    val result = SimpleId.keyFormat.writes(id)
    assertResult(JsString("xyz"))(result)
  }

  @Test
  def idAsStringOnly_stringKeyFormat_roundTrip(): Unit = {
    val id = SimpleId("test-key")
    val key = SimpleId.keyFormat.stringKeyFormat.writes(id)
    val parsed = SimpleId.keyFormat.stringKeyFormat.reads(key)
    assertResult(Some(id))(parsed)
  }

  // ─── idAsStringWithFields ──────────────────────────────────────────────────

  @Test
  def idAsStringWithFields_writes_includesAllFields(): Unit = {
    val id = CompositeId(12345L, "ml-course")
    val result = CompositeId.keyFormat.format.writes(id)
    assertResult(JsNumber(12345))((result \ "userId").get)
    assertResult(JsString("ml-course"))((result \ "courseId").get)
    assert((result \ "id").isDefined)
  }

  @Test
  def idAsStringWithFields_reads_parsesFromIdField(): Unit = {
    val id = CompositeId(42L, "course-x")
    val written = CompositeId.keyFormat.format.writes(id)
    val result = CompositeId.keyFormat.format.reads(written)
    assert(result.isSuccess)
    assertResult(id)(result.get)
  }

  @Test
  def idAsStringWithFields_reads_fromStringKey(): Unit = {
    val id = CompositeId(7L, "y")
    val stringKey = CompositeId.keyFormat.stringKeyFormat.writes(id)
    val parsed = CompositeId.keyFormat.reads(JsString(stringKey.key))
    assert(parsed.isSuccess)
    assertResult(id)(parsed.get)
  }

  @Test
  def idAsStringWithFields_writes_toStringKey(): Unit = {
    val id = CompositeId(99L, "abc")
    val result = CompositeId.keyFormat.writes(id)
    assert(result.isInstanceOf[JsString])
  }

  // ─── caseClassFormat ──────────────────────────────────────────────────────

  case class PrimitiveWrapped(value: Int)

  @Test
  def caseClassFormat_reads_wrapsValue(): Unit = {
    val kf = KeyFormat.caseClassFormat[PrimitiveWrapped, Int](PrimitiveWrapped.apply, PrimitiveWrapped.unapply)
    val json = JsNumber(5)
    val result = kf.reads(json)
    assert(result.isSuccess)
    assertResult(PrimitiveWrapped(5))(result.get)
  }

  @Test
  def caseClassFormat_writes_unwrapsValue(): Unit = {
    val kf = KeyFormat.caseClassFormat[PrimitiveWrapped, Int](PrimitiveWrapped.apply, PrimitiveWrapped.unapply)
    val result = kf.writes(PrimitiveWrapped(5))
    assertResult(JsNumber(5))(result)
  }

  @Test
  def caseClassFormat_format_reads_fromObject(): Unit = {
    val kf = KeyFormat.caseClassFormat[PrimitiveWrapped, Int](PrimitiveWrapped.apply, PrimitiveWrapped.unapply)
    val json = Json.obj("id" -> 5)
    val result = kf.format.reads(json)
    assert(result.isSuccess)
    assertResult(PrimitiveWrapped(5))(result.get)
  }

  @Test
  def caseClassFormat_format_writes_toObject(): Unit = {
    val kf = KeyFormat.caseClassFormat[PrimitiveWrapped, Int](PrimitiveWrapped.apply, PrimitiveWrapped.unapply)
    val result = kf.format.writes(PrimitiveWrapped(5))
    assertResult(JsNumber(5))((result \ "id").get)
  }

  @Test
  def caseClassFormat_stringKeyFormat_roundTrip(): Unit = {
    val kf = KeyFormat.caseClassFormat[PrimitiveWrapped, Int](PrimitiveWrapped.apply, PrimitiveWrapped.unapply)
    val key = kf.stringKeyFormat.writes(PrimitiveWrapped(42))
    val parsed = kf.stringKeyFormat.reads(key)
    assertResult(Some(PrimitiveWrapped(42)))(parsed)
  }

  // ─── idAsPrimitive ─────────────────────────────────────────────────────────

  case class WrappedInt(n: Int)

  @Test
  def idAsPrimitive_reads_wrapsInt(): Unit = {
    val kf = KeyFormat.idAsPrimitive[WrappedInt, Int](WrappedInt.apply, WrappedInt.unapply)
    val result = kf.reads(JsNumber(7))
    assert(result.isSuccess)
    assertResult(WrappedInt(7))(result.get)
  }

  @Test
  def idAsPrimitive_writes_unwrapsInt(): Unit = {
    val kf = KeyFormat.idAsPrimitive[WrappedInt, Int](WrappedInt.apply, WrappedInt.unapply)
    val result = kf.writes(WrappedInt(7))
    assertResult(JsNumber(7))(result)
  }

  // ─── withFallbackReads ────────────────────────────────────────────────────

  @Test
  def withFallbackReads_primarySucceeds_usesPrimary(): Unit = {
    val base = KeyFormat.idAsStringOnly[SimpleId]
    val fallback = KeyFormat.withFallbackReads[SimpleId](
      play.api.libs.json.Reads.pure(SimpleId("fallback"))
    )(base)

    val json = JsString("primary")
    val result = fallback.reads(json)
    assert(result.isSuccess)
    assertResult(SimpleId("primary"))(result.get)
  }

  @Test
  def withFallbackReads_primaryFails_usesFallback(): Unit = {
    val base = KeyFormat.idAsStringOnly[SimpleId]
    val fallback = KeyFormat.withFallbackReads[SimpleId](
      play.api.libs.json.Reads.pure(SimpleId("fallback"))
    )(base)

    // Pass something that the primary can't read (missing "id" field)
    val json = Json.obj("not_id" -> "something")
    val result = fallback.reads(json)
    assert(result.isSuccess)
    assertResult(SimpleId("fallback"))(result.get)
  }

  @Test
  def withFallbackReads_writes_delegatesToBase(): Unit = {
    val base = KeyFormat.idAsStringOnly[SimpleId]
    val fallback = KeyFormat.withFallbackReads[SimpleId](
      play.api.libs.json.Reads.pure(SimpleId("fallback"))
    )(base)

    val id = SimpleId("my-id")
    assertResult(base.writes(id))(fallback.writes(id))
  }

  @Test
  def withFallbackReads_format_delegatesToBase(): Unit = {
    val base = KeyFormat.idAsStringOnly[SimpleId]
    val fallback = KeyFormat.withFallbackReads[SimpleId](
      play.api.libs.json.Reads.pure(SimpleId("fallback"))
    )(base)

    val id = SimpleId("test")
    assertResult(base.format.writes(id))(fallback.format.writes(id))
  }

  @Test
  def withFallbackReads_stringKeyFormat_delegatesToBase(): Unit = {
    val base = KeyFormat.idAsStringOnly[SimpleId]
    val fallback = KeyFormat.withFallbackReads[SimpleId](
      play.api.libs.json.Reads.pure(SimpleId("fallback"))
    )(base)

    val id = SimpleId("sk-test")
    val key = fallback.stringKeyFormat.writes(id)
    assertResult(base.stringKeyFormat.writes(id))(key)
  }

  // ─── primitive key formats ─────────────────────────────────────────────────

  @Test
  def intKeyFormat_reads_fromNumber(): Unit = {
    val result = KeyFormat.intKeyFormat.reads(JsNumber(42))
    assert(result.isSuccess)
    assertResult(42)(result.get)
  }

  @Test
  def intKeyFormat_writes_toNumber(): Unit = {
    val result = KeyFormat.intKeyFormat.writes(42)
    assertResult(JsNumber(42))(result)
  }

  @Test
  def intKeyFormat_format_reads_fromObject(): Unit = {
    val result = KeyFormat.intKeyFormat.format.reads(Json.obj("id" -> 99))
    assert(result.isSuccess)
    assertResult(99)(result.get)
  }

  @Test
  def intKeyFormat_format_writes_toObject(): Unit = {
    val result = KeyFormat.intKeyFormat.format.writes(7)
    assertResult(JsNumber(7))((result \ "id").get)
  }

  @Test
  def longKeyFormat_reads_fromNumber(): Unit = {
    val result = KeyFormat.longKeyFormat.reads(JsNumber(Long.MaxValue))
    assert(result.isSuccess)
    assertResult(Long.MaxValue)(result.get)
  }

  @Test
  def longKeyFormat_writes_toNumber(): Unit = {
    val result = KeyFormat.longKeyFormat.writes(Long.MaxValue)
    assertResult(JsNumber(BigDecimal(Long.MaxValue)))(result)
  }

  @Test
  def stringKeyFormat_reads_fromString(): Unit = {
    val result = KeyFormat.stringKeyFormat.reads(JsString("hello"))
    assert(result.isSuccess)
    assertResult("hello")(result.get)
  }

  @Test
  def stringKeyFormat_writes_toString(): Unit = {
    val result = KeyFormat.stringKeyFormat.writes("world")
    assertResult(JsString("world"))(result)
  }

  @Test
  def uuidKeyFormat_roundTrip(): Unit = {
    val uuid = UUID.randomUUID()
    val written = KeyFormat.uuidKeyFormat.writes(uuid)
    val result = KeyFormat.uuidKeyFormat.reads(written)
    assert(result.isSuccess)
    assertResult(uuid)(result.get)
  }

  @Test
  def uuidKeyFormat_stringKeyFormat_roundTrip(): Unit = {
    val uuid = UUID.randomUUID()
    val key = KeyFormat.uuidKeyFormat.stringKeyFormat.writes(uuid)
    val result = KeyFormat.uuidKeyFormat.stringKeyFormat.reads(key)
    assertResult(Some(uuid))(result)
  }

  // ─── CompositeKeyFormat error path ──────────────────────────────────────────

  @Test
  def idAsStringWithFields_format_overwriteIdField_throwsException(): Unit = {
    val kf = KeyFormat.idAsStringWithFields(
      OWrites[SimpleId] { id =>
        Json.obj("id" -> "conflict")  // Collides with the auto-added "id" field
      }
    )
    val id = SimpleId("test")
    intercept[IllegalArgumentException] {
      kf.format.writes(id)
    }
  }

  // ─── intKeyFormat: reads from string representation ──────────────────────────

  @Test
  def intKeyFormat_format_reads_fromStringRepresentation(): Unit = {
    // PrimitiveKeyFormat falls back to stringKeyFormat which reads strings
    val result = KeyFormat.intKeyFormat.format.reads(Json.obj("id" -> "42"))
    assert(result.isSuccess)
    assertResult(42)(result.get)
  }
}
