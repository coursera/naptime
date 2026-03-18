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

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json._

/**
 * Tests targeting uncovered branches in NaptimeSerializer.PlayJson:
 *  - nested objects inside arrays
 *  - nested arrays inside arrays
 *  - null values inside arrays
 *  - boolean inside array
 *  - number inside array
 *  - nested object inside object
 *  - null value in object
 *  - boolean in object
 *  - AnyWrites path
 */
class NaptimeSerializerTest extends AssertionsForJUnit {

  // ─── PlayJson.serialize ──────────────────────────────────────────────────

  @Test
  def playJson_simpleString(): Unit = {
    val js = Json.obj("name" -> "Alice")
    val dm = NaptimeSerializer.PlayJson.serialize(js)
    assert(dm.get("name") === "Alice")
  }

  @Test
  def playJson_numberField(): Unit = {
    val js = Json.obj("count" -> 42)
    val dm = NaptimeSerializer.PlayJson.serialize(js)
    assert(dm.get("count") !== null)
  }

  @Test
  def playJson_booleanField(): Unit = {
    val js = Json.obj("active" -> true)
    val dm = NaptimeSerializer.PlayJson.serialize(js)
    assert(dm.get("active") === Boolean.box(true))
  }

  @Test
  def playJson_nullField(): Unit = {
    val js = Json.obj("nullable" -> JsNull)
    val dm = NaptimeSerializer.PlayJson.serialize(js)
    assert(dm.get("nullable") !== null) // stored as Null.getInstance()
  }

  @Test
  def playJson_arrayOfStrings(): Unit = {
    val js = Json.obj("tags" -> Json.arr("a", "b", "c"))
    val dm = NaptimeSerializer.PlayJson.serialize(js)
    val list = dm.get("tags")
    assert(list !== null)
  }

  @Test
  def playJson_arrayOfNumbers(): Unit = {
    val js = Json.obj("ids" -> Json.arr(1, 2, 3))
    val dm = NaptimeSerializer.PlayJson.serialize(js)
    val list = dm.get("ids")
    assert(list !== null)
  }

  @Test
  def playJson_arrayOfBooleans(): Unit = {
    val js = Json.obj("flags" -> Json.arr(true, false, true))
    val dm = NaptimeSerializer.PlayJson.serialize(js)
    val list = dm.get("flags")
    assert(list !== null)
  }

  @Test
  def playJson_arrayWithNull(): Unit = {
    val js = Json.obj("mixed" -> Json.arr(JsNull, "hello"))
    val dm = NaptimeSerializer.PlayJson.serialize(js)
    val list = dm.get("mixed")
    assert(list !== null)
  }

  @Test
  def playJson_nestedObject(): Unit = {
    val js = Json.obj("address" -> Json.obj("street" -> "123 Main St", "city" -> "SF"))
    val dm = NaptimeSerializer.PlayJson.serialize(js)
    val nested = dm.get("address")
    assert(nested !== null)
  }

  @Test
  def playJson_arrayOfObjects(): Unit = {
    val js = Json.obj("items" -> Json.arr(Json.obj("id" -> 1), Json.obj("id" -> 2)))
    val dm = NaptimeSerializer.PlayJson.serialize(js)
    val list = dm.get("items")
    assert(list !== null)
  }

  @Test
  def playJson_nestedArrayInArray(): Unit = {
    val js = Json.obj("matrix" -> Json.arr(Json.arr(1, 2), Json.arr(3, 4)))
    val dm = NaptimeSerializer.PlayJson.serialize(js)
    val list = dm.get("matrix")
    assert(list !== null)
  }

  @Test
  def playJson_deserialize_roundTrips(): Unit = {
    val original = Json.obj("x" -> 1, "y" -> "hello")
    val dm = NaptimeSerializer.PlayJson.serialize(original)
    val back = NaptimeSerializer.PlayJson.deserialize(dm)
    assert((back \ "x").as[Int] === 1)
    assert((back \ "y").as[String] === "hello")
  }

  @Test
  def playJson_schema_isNone(): Unit = {
    assert(NaptimeSerializer.PlayJson.schema(Json.obj()) === None)
  }

  // ─── AnyWrites ────────────────────────────────────────────────────────────

  @Test
  def anyWrites_serialize_putsStringRep(): Unit = {
    import NaptimeSerializer.AnyWrites._
    val dm = anyWrites.serialize(42)
    assert(dm.get("id") === "42")
  }

  @Test
  def anyWrites_schema_isNone(): Unit = {
    import NaptimeSerializer.AnyWrites._
    assert(anyWrites.schema(42) === None)
  }

  // ─── playJsonFormats ──────────────────────────────────────────────────────

  case class Point(x: Int, y: Int)
  object Point {
    implicit val fmt: OFormat[Point] = Json.format[Point]
  }

  @Test
  def playJsonFormats_serialize_producesDataMap(): Unit = {
    val serializer = NaptimeSerializer.playJsonFormats[Point]
    val dm = serializer.serialize(Point(3, 4))
    assert(dm.get("x") !== null)
    assert(dm.get("y") !== null)
  }

  @Test
  def playJsonFormats_schema_isNone(): Unit = {
    val serializer = NaptimeSerializer.playJsonFormats[Point]
    assert(serializer.schema(Point(1, 2)) === None)
  }
}
