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

import org.coursera.naptime.QueryFields
import org.coursera.naptime.ResourceName
import org.coursera.naptime.actions.RestActionCategoryEngine2.FlattenedFilteringJacksonDataCodec
import org.junit.Ignore
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.Json

class FlattenedFilteringJacksonDataCodecTest extends AssertionsForJUnit {

  @Test
  def testElementsFiltering(): Unit = {
    val unfiltered = Json.obj(
      "elements" -> Json.arr(
        Json.obj(
          "a" -> "1",
          "b" -> "1",
          "c" -> "1"),
        Json.obj(
          "a" -> "2",
          "b" -> "2",
          "c" -> "2"),
        Json.obj(
          "a" -> "3",
          "b" -> "3",
          "c" -> "3")))

    val dataMap = NaptimeSerializer.PlayJson.serialize(unfiltered)

    val expected = Json.obj(
      "elements" -> Json.arr(
        Json.obj(
          "a" -> "1"),
        Json.obj(
          "a" -> "2"),
        Json.obj(
          "a" -> "3")))

    val fields = QueryFields(Set("a"), Map.empty)

    val codec = new FlattenedFilteringJacksonDataCodec(fields)
    val serialized = codec.mapToString(dataMap)
    val deserialized = codec.stringToMap(serialized)

    assert(NaptimeSerializer.PlayJson.serialize(expected) === deserialized,
      s"Serialized is: $serialized")
    assert(expected === Json.parse(serialized))
  }

  @Test
  def testRelatedFiltering(): Unit = {
    val unfiltered = Json.obj(
      "elements" -> Json.arr(
        Json.obj(
          "a" -> 1,
          "b" -> 1,
          "c" -> 1),
        Json.obj(
          "a" -> 2,
          "b" -> 2,
          "c" -> 2),
        Json.obj(
          "a" -> 3,
          "b" -> 3,
          "c" -> 3)),
      "linked" -> Json.obj(
        "foo.v1" -> Json.arr(
          Json.obj(
            "x" -> 1,
            "y" -> 1,
            "z" -> 1),
          Json.obj(
            "x" -> 2,
            "y" -> 2,
            "z" -> 2)),
        "bar.v2/sub/supersub" -> Json.arr(
          Json.obj(
            "p" -> 1,
            "q" -> 1,
            "r" -> 1),
          Json.obj(
            "p" -> 2,
            "q" -> 2,
            "r" -> 2)),
        "unrelated.v3" -> Json.arr(
          Json.obj("w" -> "oops"))))

    val expected = Json.obj(
      "elements" -> Json.arr(
        Json.obj("a" -> 1),
        Json.obj("a" -> 2),
        Json.obj("a" -> 3)),
      "linked" -> Json.obj(
        "foo.v1" -> Json.arr(
          Json.obj("x" -> 1),
          Json.obj("x" -> 2)),
        "bar.v2/sub/supersub" -> Json.arr(
          Json.obj("p" -> 1),
          Json.obj("p" -> 2))))

    val fields = QueryFields(Set("a"), Map(
      ResourceName("foo", 1) -> Set("x"),
      ResourceName("bar", 2, List("sub", "supersub")) -> Set("p")))

    val dataMap = NaptimeSerializer.PlayJson.serialize(unfiltered)
    val codec = new FlattenedFilteringJacksonDataCodec(fields)
    val serialized = codec.mapToString(dataMap)
    val deserialized = codec.stringToMap(serialized)
    assert(NaptimeSerializer.PlayJson.serialize(expected) === deserialized,
      s"Serialized is: $serialized")
    assert(expected === Json.parse(serialized))
  }

  @Ignore
  @Test
  def testEmptyTopLevels(): Unit = {
    val unfiltered = Json.obj(
      "elements" -> Json.arr(
        Json.obj("a" -> 1),
        Json.obj("a" -> 2)),
      "paging" -> Json.obj(),
      "linked" -> Json.obj())

    val expected = Json.obj(
      "elements" -> Json.arr(
        Json.obj("a" -> 1),
        Json.obj("a" -> 2)))

    val fields = QueryFields(Set("a"), Map.empty)
    val dataMap = NaptimeSerializer.PlayJson.serialize(unfiltered)
    val codec = new FlattenedFilteringJacksonDataCodec(fields)
    val serialized = codec.mapToString(dataMap)
    val deserialized = codec.stringToMap(serialized)
    assert(NaptimeSerializer.PlayJson.serialize(expected) === deserialized)
    assert(expected === Json.parse(serialized))
  }
}
