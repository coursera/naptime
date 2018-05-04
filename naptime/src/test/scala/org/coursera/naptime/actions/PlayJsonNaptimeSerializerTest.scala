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

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import play.api.libs.json.Json
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class PlayJsonNaptimeSerializerTest extends AssertionsForJUnit {

  @Test
  def checkDataMapEquality(): Unit = {
    val one = new DataMap()
    one.put("a", "b")
    val two = new DataMap()
    two.put("a", "b")

    val three = new DataMap()
    three.put("a", one)
    val four = new DataMap()
    four.put("a", two)

    assert(one === two)
    assert(three === four)
    assert(one !== three)
    assert(two !== four)
  }

  @Test
  def testEmpty(): Unit = {
    val testCase = Json.obj()
    val expected = new DataMap()
    assert(expected === NaptimeSerializer.PlayJson.serialize(testCase), "Didn't match expected")
    assert(
      testCase ===
        NaptimeSerializer.PlayJson.deserialize(NaptimeSerializer.PlayJson.serialize(testCase)))
  }

  @Test
  def testSimpleStrings(): Unit = {
    val testCase = Json.obj("a" -> "b")
    val expected = new DataMap()
    expected.put("a", "b")
    assert(expected === NaptimeSerializer.PlayJson.serialize(testCase), "Didn't match expected")
    assert(
      testCase ===
        NaptimeSerializer.PlayJson.deserialize(NaptimeSerializer.PlayJson.serialize(testCase)))
  }

  @Test
  def testNumbers(): Unit = {
    val testCase = Json.obj("a" -> 1, "b" -> 1.2)
    val expected = new DataMap()
    expected.put("a", new java.lang.Integer(1))
    expected.put("b", Double.box(1.2))
//    assert(expected === NaptimeSerializer.PlayJson.serialize(testCase), "Didn't match expected")

    // Use string serialization to work around Float imprecision.
    assert(expected.toString === NaptimeSerializer.PlayJson.serialize(testCase).toString)
    assert(
      testCase ===
        NaptimeSerializer.PlayJson.deserialize(NaptimeSerializer.PlayJson.serialize(testCase)))
  }

  // TODO: Test Longs

  @Test
  def testBoolean(): Unit = {
    val testCase = Json.obj("a" -> true, "b" -> false)
    val expected = new DataMap()
    expected.put("a", new java.lang.Boolean(true))
    expected.put("b", new java.lang.Boolean(false))

    assert(expected === NaptimeSerializer.PlayJson.serialize(testCase), "Didn't match expected")
    assert(
      testCase ===
        NaptimeSerializer.PlayJson.deserialize(NaptimeSerializer.PlayJson.serialize(testCase)))
  }

  @Test
  def testNestedObjects(): Unit = {
    val testCase = Json.obj("a" -> Json.obj("b" -> "c"))
    val expected = new DataMap()
    val inner = new DataMap()
    inner.put("b", "c")
    expected.put("a", inner)

    assert(expected === NaptimeSerializer.PlayJson.serialize(testCase), "Didn't match expected")
    assert(
      testCase ===
        NaptimeSerializer.PlayJson.deserialize(NaptimeSerializer.PlayJson.serialize(testCase)))
  }

  @Test
  def testComplex(): Unit = {
    val testCase = Json.obj(
      "elements" -> Json.arr(Json.obj("a" -> "b"), Json.obj("a" -> "b")),
      "paging" -> Json.obj("offset" -> 1, "next" -> "nextTokenBase64Encoded")
    )

    val expected = new DataMap()
    val elements = new DataList()
    val elemA = new DataMap()
    val elemB = new DataMap()
    elemA.put("a", "b")
    elemB.put("a", "b")
    elements.add(elemA)
    elements.add(elemB)
    val paging = new DataMap()
    paging.put("offset", new java.lang.Integer(1))
    paging.put("next", "nextTokenBase64Encoded")
    expected.put("elements", elements)
    expected.put("paging", paging)

    val serialized = NaptimeSerializer.PlayJson.serialize(testCase)
    assert(elements === serialized.get("elements"))
    assert(paging === serialized.get("paging"))

    assert(expected === serialized, "Didn't match expected")
    assert(
      testCase ===
        NaptimeSerializer.PlayJson.deserialize(NaptimeSerializer.PlayJson.serialize(testCase)))
  }
}
