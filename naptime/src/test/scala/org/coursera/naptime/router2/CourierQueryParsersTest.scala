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

package org.coursera.naptime.router2

import org.coursera.courier.codecs.InlineStringCodec
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.courier.StringKeyCodec
import org.coursera.naptime.actions.SortOrder
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.test.FakeRequest

class CourierQueryParsersTest extends AssertionsForJUnit {

  @Test
  def checkStrictParseSimple(): Unit = {
    val sortOrder = SortOrder(field = "startDate", descending = false)
    val sortOrderStr = InlineStringCodec.dataToString(sortOrder.data())
    val fakeRequest = FakeRequest("GET", s"/foo?sort=$sortOrderStr")
    val parsedDataMap =
      CourierQueryParsers.strictParse("sort", SortOrder.SCHEMA, getClass, fakeRequest)
    assert(parsedDataMap.isRight)
    val parsedSortOrder = SortOrder(parsedDataMap.right.get, DataConversion.SetReadOnly)
    assert(sortOrder === parsedSortOrder)
  }

  @Test
  def checkStrictParseMalformed(): Unit = {
    val fakeRequest = FakeRequest("GET", s"/foo?sort=baz")
    val parsedDataMap =
      CourierQueryParsers.strictParse("sort", SortOrder.SCHEMA, getClass, fakeRequest)
    assert(parsedDataMap.isLeft)
  }

  @Test
  def checkStrictParseMalformed2(): Unit = {
    val fakeRequest = FakeRequest("GET", s"/foo?sort=(a~b)")
    val parsedDataMap =
      CourierQueryParsers.strictParse("sort", SortOrder.SCHEMA, getClass, fakeRequest)
    assert(parsedDataMap.isLeft)
  }

  @Test
  def checkStrictParseMissingField(): Unit = {
    val sortOrder = SortOrder(field = "startDate") // Field descending should be default
    val fakeRequest = FakeRequest("GET", s"/foo?sort=(field~startDate)")
    val parsedDataMap =
      CourierQueryParsers.strictParse("sort", SortOrder.SCHEMA, getClass, fakeRequest)
    assert(parsedDataMap.isRight)
    val parsedSortOrder = SortOrder(parsedDataMap.right.get, DataConversion.SetReadOnly)
    assert(sortOrder === parsedSortOrder)
  }

  @Test
  def checkStrictParseOldFormat(): Unit = {
    val sortOrder = SortOrder(field = "startDate", descending = false)
    val sortOrderStr = new String(new StringKeyCodec(SortOrder.SCHEMA).mapToBytes(sortOrder.data()))
    assert("startDate~false" === sortOrderStr)
    val fakeRequest = FakeRequest("GET", s"/foo?sort=$sortOrderStr")
    val parsedDataMap =
      CourierQueryParsers.strictParse("sort", SortOrder.SCHEMA, getClass, fakeRequest)
    assert(parsedDataMap.isRight)
    val parsedSortOrder = SortOrder(parsedDataMap.right.get, DataConversion.SetReadOnly)
    assert(sortOrder === parsedSortOrder)
  }

  @Test
  def checkOptParsePresent(): Unit = {
    val sortOrder = SortOrder(field = "startDate", descending = false)
    val sortOrderStr = InlineStringCodec.dataToString(sortOrder.data())
    val fakeRequest = FakeRequest("GET", s"/foo?sort=$sortOrderStr")
    val parsedDataMap =
      CourierQueryParsers.optParse("sort", SortOrder.SCHEMA, getClass, fakeRequest)
    assert(parsedDataMap.isRight)
    assert(parsedDataMap.right.get.isDefined)
    val parsedSortOrder = SortOrder(parsedDataMap.right.get.get, DataConversion.SetReadOnly)
    assert(sortOrder === parsedSortOrder)
  }

  @Test
  def checkOptParseAbsent(): Unit = {
    val fakeRequest = FakeRequest("GET", s"/foo?bar=baz")
    val parsedDataMap =
      CourierQueryParsers.optParse("sort", SortOrder.SCHEMA, getClass, fakeRequest)
    assert(parsedDataMap.isRight)
    assert(parsedDataMap.right.get.isEmpty)
  }

  @Test
  def checkOptParseMalformed(): Unit = {
    val fakeRequest = FakeRequest("GET", s"/foo?sort=baz")
    val parsedDataMap =
      CourierQueryParsers.optParse("sort", SortOrder.SCHEMA, getClass, fakeRequest)
    assert(parsedDataMap.isLeft)
  }

  @Test
  def checkOptParseMalformed2(): Unit = {
    val fakeRequest = FakeRequest("GET", s"/foo?sort=(a~b)")
    val parsedDataMap =
      CourierQueryParsers.optParse("sort", SortOrder.SCHEMA, getClass, fakeRequest)
    assert(parsedDataMap.isLeft)
  }

  @Test
  def checkOptParseMissingField(): Unit = {
    val sortOrder = SortOrder(field = "startDate") // Field descending should be default
    val fakeRequest = FakeRequest("GET", s"/foo?sort=(field~startDate)")
    val parsedDataMap =
      CourierQueryParsers.optParse("sort", SortOrder.SCHEMA, getClass, fakeRequest)
    assert(parsedDataMap.isRight)
    assert(parsedDataMap.right.get.isDefined)
    val parsedSortOrder = SortOrder(parsedDataMap.right.get.get, DataConversion.SetReadOnly)
    assert(sortOrder === parsedSortOrder)
  }

  @Test
  def checkOptParseOldFormat(): Unit = {
    val sortOrder = SortOrder(field = "startDate", descending = false)
    val sortOrderStr = new String(new StringKeyCodec(SortOrder.SCHEMA).mapToBytes(sortOrder.data()))
    assert("startDate~false" === sortOrderStr)
    val fakeRequest = FakeRequest("GET", s"/foo?sort=$sortOrderStr")
    val parsedDataMap =
      CourierQueryParsers.optParse("sort", SortOrder.SCHEMA, getClass, fakeRequest)
    assert(parsedDataMap.isRight)
    assert(parsedDataMap.right.get.isDefined)
    val parsedSortOrder = SortOrder(parsedDataMap.right.get.get, DataConversion.SetReadOnly)
    assert(sortOrder === parsedSortOrder)
  }
}
