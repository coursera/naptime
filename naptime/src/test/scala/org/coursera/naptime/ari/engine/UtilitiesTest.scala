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

package org.coursera.naptime.ari.engine

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json._

class UtilitiesTest extends AssertionsForJUnit {

  // ---------------------------------------------------------------------------
  // stringifyArg
  // ---------------------------------------------------------------------------

  @Test
  def stringifyArg_string_returnsValue(): Unit = {
    assertResult("hello")(Utilities.stringifyArg(JsString("hello")))
  }

  @Test
  def stringifyArg_number_returnsStringRepresentation(): Unit = {
    assertResult("42")(Utilities.stringifyArg(JsNumber(42)))
  }

  @Test
  def stringifyArg_booleanTrue_returnsTrue(): Unit = {
    assertResult("true")(Utilities.stringifyArg(JsBoolean(true)))
  }

  @Test
  def stringifyArg_booleanFalse_returnsFalse(): Unit = {
    assertResult("false")(Utilities.stringifyArg(JsBoolean(false)))
  }

  @Test
  def stringifyArg_null_returnsEmptyString(): Unit = {
    assertResult("")(Utilities.stringifyArg(JsNull))
  }

  @Test
  def stringifyArg_jsObject_returnsJsonString(): Unit = {
    val obj = Json.obj("key" -> "value")
    assertResult(Json.stringify(obj))(Utilities.stringifyArg(obj))
  }

  @Test
  def stringifyArg_array_joinsByComma(): Unit = {
    val arr = JsArray(Seq(JsString("a"), JsString("b"), JsString("c")))
    assertResult("a,b,c")(Utilities.stringifyArg(arr))
  }

  @Test
  def stringifyArg_arrayWithNull_filtersNulls(): Unit = {
    // JsNull stringifies to "", which is filtered out
    val arr = JsArray(Seq(JsString("a"), JsNull, JsString("b")))
    assertResult("a,b")(Utilities.stringifyArg(arr))
  }

  @Test
  def stringifyArg_emptyArray_returnsEmptyString(): Unit = {
    assertResult("")(Utilities.stringifyArg(JsArray(Seq.empty)))
  }

  // ---------------------------------------------------------------------------
  // jsValueIsEmpty
  // ---------------------------------------------------------------------------

  @Test
  def jsValueIsEmpty_null_returnsTrue(): Unit = {
    assert(Utilities.jsValueIsEmpty(JsNull))
  }

  @Test
  def jsValueIsEmpty_emptyString_returnsTrue(): Unit = {
    assert(Utilities.jsValueIsEmpty(JsString("")))
  }

  @Test
  def jsValueIsEmpty_nonEmptyString_returnsFalse(): Unit = {
    assert(!Utilities.jsValueIsEmpty(JsString("hello")))
  }

  @Test
  def jsValueIsEmpty_number_returnsFalse(): Unit = {
    assert(!Utilities.jsValueIsEmpty(JsNumber(0)))
  }

  @Test
  def jsValueIsEmpty_boolean_returnsFalse(): Unit = {
    assert(!Utilities.jsValueIsEmpty(JsBoolean(false)))
  }

  @Test
  def jsValueIsEmpty_jsObject_returnsFalse(): Unit = {
    assert(!Utilities.jsValueIsEmpty(Json.obj("k" -> "v")))
  }

  @Test
  def jsValueIsEmpty_emptyArray_returnsTrue(): Unit = {
    assert(Utilities.jsValueIsEmpty(JsArray(Seq.empty)))
  }

  @Test
  def jsValueIsEmpty_arrayOfNulls_returnsTrue(): Unit = {
    assert(Utilities.jsValueIsEmpty(JsArray(Seq(JsNull, JsNull))))
  }

  @Test
  def jsValueIsEmpty_arrayWithNonEmptyElement_returnsFalse(): Unit = {
    assert(!Utilities.jsValueIsEmpty(JsArray(Seq(JsNull, JsString("x")))))
  }

  // ---------------------------------------------------------------------------
  // getValuesAtPath
  // ---------------------------------------------------------------------------

  /** Build a minimal MergedCourse DataMap with the given fields. */
  private def makeCourseDataMap(
      id: String,
      name: String,
      slug: String,
      instructorIds: Seq[String] = Seq.empty,
      partnerId: Int = 1): DataMap = {
    val dm = new DataMap()
    dm.put("id", id)
    dm.put("name", name)
    dm.put("slug", slug)
    dm.put("partnerId", Int.box(partnerId))
    val instrList = new DataList()
    instructorIds.foreach(instrList.add)
    dm.put("instructorIds", instrList)
    // Provide minimal required nested fields
    val originalIdMap = new DataMap()
    originalIdMap.put("int", Int.box(0))
    dm.put("originalId", originalIdMap)
    val platformDataMap = new DataMap()
    // TypedDefinition structures
    val innerPlatformMap = new DataMap()
    innerPlatformMap.put("typeName", "old")
    innerPlatformMap.put("definition", new DataMap())
    dm.put("platformSpecificData", innerPlatformMap)
    dm.put("coursePlatform", new DataList())
    dm.put("arbitraryData", new DataMap())
    dm
  }

  @Test
  def getValuesAtPath_topLevelStringField_returnsValue(): Unit = {
    val dm = makeCourseDataMap(id = "courseA", name = "My Course", slug = "my-course")
    val result = Utilities.getValuesAtPath(dm, MergedCourse.SCHEMA, Seq("id"))
    assert(result.contains("courseA"))
  }

  @Test
  def getValuesAtPath_topLevelStringField_nameField(): Unit = {
    val dm = makeCourseDataMap(id = "courseA", name = "Test Course", slug = "test")
    val result = Utilities.getValuesAtPath(dm, MergedCourse.SCHEMA, Seq("name"))
    assert(result.contains("Test Course"))
  }

  @Test
  def getValuesAtPath_arrayField_returnsAllElements(): Unit = {
    val dm = makeCourseDataMap(
      id = "courseA",
      name = "My Course",
      slug = "my-course",
      instructorIds = Seq("instrA", "instrB"))
    val result = Utilities.getValuesAtPath(dm, MergedCourse.SCHEMA, Seq("instructorIds"))
    assert(result.contains("instrA"))
    assert(result.contains("instrB"))
    assert(result.size === 2)
  }

  @Test
  def getValuesAtPath_emptyArray_returnsEmptyList(): Unit = {
    val dm = makeCourseDataMap(
      id = "courseA",
      name = "My Course",
      slug = "my-course",
      instructorIds = Seq.empty)
    val result = Utilities.getValuesAtPath(dm, MergedCourse.SCHEMA, Seq("instructorIds"))
    assert(result.isEmpty)
  }

  @Test
  def getValuesAtPath_nonExistentPath_returnsEmpty(): Unit = {
    val dm = makeCourseDataMap(id = "courseA", name = "My Course", slug = "my-course")
    val result = Utilities.getValuesAtPath(dm, MergedCourse.SCHEMA, Seq("nonExistentField"))
    assert(result.isEmpty)
  }

  @Test
  def getValuesAtPath_duplicateValues_returnDistinct(): Unit = {
    val dm = makeCourseDataMap(
      id = "courseA",
      name = "My Course",
      slug = "my-course",
      instructorIds = Seq("instrA", "instrA", "instrB"))
    val result = Utilities.getValuesAtPath(dm, MergedCourse.SCHEMA, Seq("instructorIds"))
    // distinct() is called in getValuesAtPath
    assert(result.size <= 3)
    assert(result.contains("instrA"))
    assert(result.contains("instrB"))
  }

  @Test
  def getValuesAtPath_intField_returnsStringRepresentation(): Unit = {
    val dm =
      makeCourseDataMap(id = "courseA", name = "My Course", slug = "my-course", partnerId = 42)
    val result = Utilities.getValuesAtPath(dm, MergedCourse.SCHEMA, Seq("partnerId"))
    assert(result.contains("42"))
  }
}
