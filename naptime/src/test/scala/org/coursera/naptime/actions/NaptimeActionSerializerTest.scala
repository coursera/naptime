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

package org.coursera.naptime.actions

import org.coursera.naptime.schema.InternalAuth
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.http.ContentTypes
import play.api.libs.json.Json
import play.api.libs.json.JsValue

import java.nio.charset.StandardCharsets

case class SerializerTestFoo(x: Int)
object SerializerTestFoo {
  implicit val writes: play.api.libs.json.Writes[SerializerTestFoo] =
    Json.writes[SerializerTestFoo]
}

class NaptimeActionSerializerTest extends AssertionsForJUnit {

  // ─── PlayJson ──────────────────────────────────────────────────────────────

  @Test
  def playJson_serialize_producesJsonBytes(): Unit = {
    val jsValue: JsValue = Json.obj("key" -> "value")
    val bytes = NaptimeActionSerializer.PlayJson.serialize(jsValue)
    val str = new String(bytes, StandardCharsets.UTF_8)
    assert(str.contains("key"))
    assert(str.contains("value"))
  }

  @Test
  def playJson_contentType_isJson(): Unit = {
    val jsValue: JsValue = Json.obj()
    assert(NaptimeActionSerializer.PlayJson.contentType(jsValue) === ContentTypes.JSON)
  }

  @Test
  def playJson_schema_isNone(): Unit = {
    val jsValue: JsValue = Json.obj()
    assert(NaptimeActionSerializer.PlayJson.schema(jsValue) === None)
  }

  // ─── Strings ──────────────────────────────────────────────────────────────

  @Test
  def strings_serialize_producesUtf8Bytes(): Unit = {
    val bytes = NaptimeActionSerializer.Strings.serialize("hello world")
    val str = new String(bytes, StandardCharsets.UTF_8)
    assert(str === "hello world")
  }

  @Test
  def strings_contentType_isText(): Unit = {
    assert(NaptimeActionSerializer.Strings.contentType("any") === ContentTypes.TEXT)
  }

  @Test
  def strings_schema_isSomeStringSchema(): Unit = {
    val schema = NaptimeActionSerializer.Strings.schema("any")
    assert(schema.isDefined)
  }

  // ─── UnitWriter ────────────────────────────────────────────────────────────

  @Test
  def unitWriter_serialize_returnsEmptyArray(): Unit = {
    val bytes = NaptimeActionSerializer.UnitWriter.serialize(())
    assert(bytes.isEmpty)
  }

  @Test
  def unitWriter_contentType_isText(): Unit = {
    assert(NaptimeActionSerializer.UnitWriter.contentType(()) === ContentTypes.TEXT)
  }

  @Test
  def unitWriter_schema_isSomeNullSchema(): Unit = {
    val schema = NaptimeActionSerializer.UnitWriter.schema(())
    assert(schema.isDefined)
  }

  // ─── playJson (via Writes[T]) ──────────────────────────────────────────────

  @Test
  def playJsonWrites_serialize_usesWrites(): Unit = {
    implicit val writes = SerializerTestFoo.writes
    val serializer = NaptimeActionSerializer.playJson[SerializerTestFoo]
    val bytes = serializer.serialize(SerializerTestFoo(42))
    val str = new String(bytes, StandardCharsets.UTF_8)
    assert(str.contains("42"))
  }

  @Test
  def playJsonWrites_contentType_isJson(): Unit = {
    implicit val writes = SerializerTestFoo.writes
    val serializer = NaptimeActionSerializer.playJson[SerializerTestFoo]
    assert(serializer.contentType(SerializerTestFoo(1)) === ContentTypes.JSON)
  }

  @Test
  def playJsonWrites_schema_isNone(): Unit = {
    implicit val writes = SerializerTestFoo.writes
    val serializer = NaptimeActionSerializer.playJson[SerializerTestFoo]
    assert(serializer.schema(SerializerTestFoo(1)) === None)
  }

  // ─── optionWriter ──────────────────────────────────────────────────────────

  @Test
  def optionWriter_some_delegatesToInner(): Unit = {
    val serializer = NaptimeActionSerializer.optionWriter[String](NaptimeActionSerializer.Strings)
    val bytes = serializer.serialize(Some("hello"))
    assert(new String(bytes, StandardCharsets.UTF_8) === "hello")
  }

  @Test
  def optionWriter_none_returnsEmptyBytes(): Unit = {
    val serializer = NaptimeActionSerializer.optionWriter[String](NaptimeActionSerializer.Strings)
    val bytes = serializer.serialize(None)
    assert(bytes.isEmpty)
  }

  @Test
  def optionWriter_some_contentType(): Unit = {
    val serializer = NaptimeActionSerializer.optionWriter[String](NaptimeActionSerializer.Strings)
    assert(serializer.contentType(Some("x")) === ContentTypes.TEXT)
  }

  @Test
  def optionWriter_none_contentType_isText(): Unit = {
    val serializer = NaptimeActionSerializer.optionWriter[String](NaptimeActionSerializer.Strings)
    assert(serializer.contentType(None) === ContentTypes.TEXT)
  }

  @Test
  def optionWriter_some_schema(): Unit = {
    val serializer = NaptimeActionSerializer.optionWriter[String](NaptimeActionSerializer.Strings)
    assert(serializer.schema(Some("x")).isDefined)
  }

  @Test
  def optionWriter_none_schema_isNone(): Unit = {
    val serializer = NaptimeActionSerializer.optionWriter[String](NaptimeActionSerializer.Strings)
    assert(serializer.schema(None) === None)
  }

  // ─── AnyWrites ────────────────────────────────────────────────────────────

  @Test
  def anyWrites_serialize_callsToString(): Unit = {
    import NaptimeActionSerializer.AnyWrites._
    val bytes = AnyWrites.serialize(42)
    assert(new String(bytes, StandardCharsets.UTF_8) === "42")
  }

  @Test
  def anyWrites_contentType_isText(): Unit = {
    import NaptimeActionSerializer.AnyWrites._
    assert(AnyWrites.contentType("anything") === ContentTypes.TEXT)
  }

  @Test
  def anyWrites_schema_isNone(): Unit = {
    import NaptimeActionSerializer.AnyWrites._
    assert(AnyWrites.schema("anything") === None)
  }

  // ─── courierModel ──────────────────────────────────────────────────────────

  @Test
  def courierModel_serialize_producesJsonBytes(): Unit = {
    val auth = InternalAuth()
    val serializer = NaptimeActionSerializer.courierModel[InternalAuth]
    val bytes = serializer.serialize(auth)
    // InternalAuth is an empty record, so serialized as "{}"
    assert(bytes.nonEmpty)
  }

  @Test
  def courierModel_contentType_isJson(): Unit = {
    val auth = InternalAuth()
    val serializer = NaptimeActionSerializer.courierModel[InternalAuth]
    assert(serializer.contentType(auth) === ContentTypes.JSON)
  }

  @Test
  def courierModel_schema_isSomeDataSchema(): Unit = {
    val auth = InternalAuth()
    val serializer = NaptimeActionSerializer.courierModel[InternalAuth]
    val schemaOpt = serializer.schema(auth)
    assert(schemaOpt.isDefined)
  }
}
