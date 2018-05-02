/*
 * Copyright 2016 Coursera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.coursera.naptime.model

import java.util.UUID

import org.coursera.common.jsonformat.JsonFormats
import org.coursera.common.stringkey.StringKey
import org.coursera.common.stringkey.StringKeyFormat
import play.api.libs.json.Format
import play.api.libs.json.JsResult
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import play.api.libs.json.Reads
import play.api.libs.json.__

import scala.annotation.implicitNotFound

/**
 * Marshaller for resource id types.
 */
@implicitNotFound(
  msg =
    "Implicit `KeyFormat` not found for {K}. Create one in ${K}'s companion object with a method in `KeyFormat`.")
sealed trait KeyFormat[K] extends Format[K] {

  /** Marshals resource id into a string that can be used in URLs. */
  implicit def stringKeyFormat: StringKeyFormat[K]

  /** Marshals resource id into a JSON object that can be added to a Naptime response. */
  implicit def format: OFormat[K]

}

object KeyFormat extends PrimitiveFormats {

  val ID_FIELD = "id"

  /**
   * Defines a `KeyFormat` that only adds the `"id"` field to response objects. The `"id"` field
   * will contain a string representation of the id, as specified by `stringKeyFormatT`.
   *
   * Note: If `K` contains a single, primitive type field (like `case class A(i: Int)`), you may
   * want to use [[idAsPrimitive]] instead. With [[idAsStringOnly]], the output object will
   * be `{"id": "3"}`, whereas with [[idAsPrimitive]], it'll be `{"id": 3}`.
   *
   * Note: If you want to add additional fields to JSON response objects (for example, if `K` is a
   * composite key), use [[idAsStringWithFields]] instead.
   */
  def idAsStringOnly[K](implicit stringKeyFormatT: StringKeyFormat[K]): KeyFormat[K] = {
    new CompositeKeyFormat[K]()(stringKeyFormatT, OWrites(_ => Json.obj()))
  }

  /**
   * Defines a `KeyFormat` that adds the `"id"` field to response objects as well as others
   * returned by `idWrites`. Use this for composite id types where clients should have access
   * to parts of the id in addition to the whole opaque string.
   *
   * See [[idAsStringOnly]].
   */
  def idAsStringWithFields[K](
      idWrites: OWrites[K]) // Intentionally not implicit, to avoid accidental format use.
  (implicit stringKeyFormat: StringKeyFormat[K]): KeyFormat[K] = {
    new CompositeKeyFormat[K]()(stringKeyFormat, idWrites)
  }

  /**
   * Defines a format that only adds the `"id"` field to response objects. The `"id"` field
   * will contain the natural JSON representation for primitive type `P`.
   * (JSON number for `Int`, etc.)
   */
  def idAsPrimitive[K, P](apply: P => K, unapply: K => Option[P])(
      implicit primitiveFormat: KeyFormat[P]): KeyFormat[K] =
    caseClassFormat(apply, unapply)

  /**
   * Conveniently construct a format for a `case class` type.
   *
   * For example:
   * {{{
   *   case class StorageKey(distributionKey: String, sortKey: String)
   *
   *   object StorageKey {
   *     implicit val keyFormat: KeyFormat[StorageKey] =
   *       KeyFormat.caseClass((apply _).tupled, unapply)
   *   }
   * }}}
   */
  def caseClassFormat[K, L](apply: L => K, unapply: K => Option[L])(
      implicit delegateFormat: KeyFormat[L]): KeyFormat[K] = {

    new KeyFormat[K] {
      override implicit val stringKeyFormat: StringKeyFormat[K] =
        StringKeyFormat.caseClassFormat(apply, unapply)(delegateFormat.stringKeyFormat)
      override implicit val format: OFormat[K] =
        JsonFormats.caseClassOFormat(apply, unapply)(delegateFormat.format)

      override def reads(json: JsValue): JsResult[K] =
        delegateFormat.reads(json).map(apply)
      override def writes(o: K): JsValue =
        delegateFormat.writes(Function.unlift(unapply).apply(o))
    }
  }

  /**
   * Format for composite keys that adds key parts to js objects (in addition to the id).
   */
  private[this] class CompositeKeyFormat[K]()(
      override implicit val stringKeyFormat: StringKeyFormat[K],
      baseJsonWrites: OWrites[K])
      extends KeyFormat[K] {

    override def format: OFormat[K] = {
      val reads = (__ \ ID_FIELD).read(JsonFormats.stringKeyFormat[K])
      val writes = OWrites { key: K =>
        val extraFields = baseJsonWrites.writes(key)
        require(!extraFields.keys.contains(ID_FIELD), s"Cannot overwrite $ID_FIELD")
        Json.obj(ID_FIELD -> StringKey.toStringKey(key).key) ++ extraFields
      }
      OFormat(reads, writes)
    }

    private[this] val jsonKeyFormat: Format[K] = JsonFormats.stringKeyFormat[K]

    override def reads(js: JsValue): JsResult[K] = jsonKeyFormat.reads(js)

    override def writes(k: K): JsValue = jsonKeyFormat.writes(k)

  }

  /**
   * Augment `K`'s format with an additional fallback JsReads implementation.
   *
   * @param altReads An alternate JsValue to `K` Reads implementation.
   * @param keyFormat The standard KeyFormat to augment.
   * @tparam K The key type.
   * @return The augmented KeyFormat.
   */
  def withFallbackReads[K](altReads: Reads[K])(keyFormat: KeyFormat[K]): KeyFormat[K] = {
    new KeyFormat[K] {

      override implicit def stringKeyFormat: StringKeyFormat[K] =
        keyFormat.stringKeyFormat

      override implicit def format: OFormat[K] = keyFormat.format

      override def reads(json: JsValue): JsResult[K] = {
        keyFormat.reads(json).orElse(altReads.reads(json))
      }

      override def writes(o: K): JsValue = keyFormat.writes(o)
    }
  }
}

/**
 * Work around implicit resolution rules: if these fields were declared in [[PrimitiveFormats]],
 * the implicit `KeyFormat`s there would hide the default Play JSON implicits.
 */
private object DefaultPrimitiveFormats {

  val defaultIntFormat: Format[Int] = implicitly

  val defaultLongFormat: Format[Long] = implicitly

  val defaultStringFormat: Format[String] = implicitly

  val defaultUuidFormat: Format[UUID] = JsonFormats.stringKeyFormat

}

sealed trait PrimitiveFormats {

  import DefaultPrimitiveFormats._

  implicit val intKeyFormat: KeyFormat[Int] = new PrimitiveKeyFormat(defaultIntFormat)

  implicit val longKeyFormat: KeyFormat[Long] = new PrimitiveKeyFormat(defaultLongFormat)

  implicit val stringKeyFormat: KeyFormat[String] = new PrimitiveKeyFormat(defaultStringFormat)

  implicit val uuidKeyFormat: KeyFormat[UUID] = new PrimitiveKeyFormat(defaultUuidFormat)

  /**
   * Format for primitive key types that serializes the primitives to native JSON types
   * (ints to JSON ints, etc.) rather than JSON strings.
   *
   * It can read both primitives and strings, though.
   */
  private[this] class PrimitiveKeyFormat[P](primitiveJsonFormat: Format[P])(
      override implicit val stringKeyFormat: StringKeyFormat[P])
      extends KeyFormat[P] {

    override implicit def format: OFormat[P] = {
      val reads: Reads[P] = (__ \ KeyFormat.ID_FIELD)
        .read(primitiveJsonFormat.orElse(JsonFormats.stringKeyFormat[P]))
      val writes = OWrites { primitive: P =>
        Json.obj(KeyFormat.ID_FIELD -> primitiveJsonFormat.writes(primitive))
      }
      OFormat(reads, writes)
    }

    override def writes(o: P): JsValue = primitiveJsonFormat.writes(o)

    override def reads(json: JsValue): JsResult[P] =
      primitiveJsonFormat.reads(json)

  }

}
