/*
 * Copyright 2024 Coursera Inc.
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

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json.Json
import play.api.libs.json.JsSuccess

class KeyedTest extends AssertionsForJUnit {

  // ─── Keyed core ────────────────────────────────────────────────────────────

  @Test
  def mapValue_transformsValue(): Unit = {
    val keyed = Keyed(1, "hello")
    val result = keyed.mapValue(_.length)
    assertResult(Keyed(1, 5))(result)
  }

  @Test
  def tuple_returnsPair(): Unit = {
    val keyed = Keyed("k", 42)
    assertResult(("k", 42))(keyed.tuple)
  }

  @Test
  def tupled_constructsFromPair(): Unit = {
    val pair = (7, "seven")
    val keyed = Keyed.tupled(pair)
    assertResult(Keyed(7, "seven"))(keyed)
  }

  // ─── Keyed JSON reads/writes ────────────────────────────────────────────────

  @Test
  def reads_parsesKeyedFromJson(): Unit = {
    implicit val keyFormat: KeyFormat[Int] = KeyFormat.intKeyFormat
    implicit val valueReads = Json.reads[KeyedTest.Item]
    val json = Json.obj("id" -> 42, "name" -> "foo")
    val result = json.validate[Keyed[Int, KeyedTest.Item]]
    assertResult(JsSuccess(Keyed(42, KeyedTest.Item("foo"))))(result)
  }

  @Test
  def writes_producesKeyedJson(): Unit = {
    implicit val keyFormat: KeyFormat[Int] = KeyFormat.intKeyFormat
    implicit val valueWrites = Json.writes[KeyedTest.Item]
    val keyed = Keyed(10, KeyedTest.Item("bar"))
    val json = Json.toJson(keyed)
    assertResult(Some(10))((json \ "id").asOpt[Int])
    assertResult(Some("bar"))((json \ "name").asOpt[String])
  }
}

object KeyedTest {
  case class Item(name: String)
}
