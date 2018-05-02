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

package org.coursera.naptime.actions.util

import org.scalatest.junit.AssertionsForJUnit
import play.api.http.HeaderNames
import play.api.http.Status
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.JsNull
import play.api.mvc.Result
import play.api.test.Helpers.contentAsBytes
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.http.MimeTypes

import scala.concurrent.Future

/**
 * A collection of functions to validate that a response is appropriately naptime-y.
 *
 * TODO(saeta): consider using a proper functional-based validation API.
 */
object Validators extends AssertionsForJUnit {

  /**
   * Verify that the response is valid according to the naptime wire protocol.
   * @param result The result to verify.
   * @param strictMode Whether the validator should be extra nit-picky about things that won't
   *                   necessarily break clients, but could indicate a problem with the response.
   */
  def assertValidResponse(result: Result, strictMode: Boolean = true): Unit = {
    result.header.status match {
      case Status.OK | Status.CREATED | Status.NO_CONTENT =>
        assertValidSuccessResponse(result, strictMode)
      case Status.NOT_MODIFIED =>
        assert(result.body.contentType.isEmpty)
        assert(result.header.headers.get(HeaderNames.ETAG).isDefined)
        assert(0 === contentAsBytes(Future.successful(result)).length)
      case code if code >= 400 && code < 500 =>
        assertClientErrorResponse(result)
      case code if code >= 500 =>
        assertServerErrorResponse(result)
    }
  }

  /**
   * Verify that the response is a valid successful (2XX-class) response according to the naptime
   * wire protocol.
   * @param result The result to verify.
   * @param strictMode Whether the validator should be extra nit-picky about things that won't
   *                   necessarily break clients, but could indicate a problem with the response.
   */
  def assertValidSuccessResponse(result: Result, strictMode: Boolean = true): Unit = {
    result.header.status match {
      case Status.OK =>
        assert(hasBody(result), "Response should have a body!")
        val body = assertBodyIsJson(result)
        assertValidSuccessResponseBody(body, strictMode)
      case Status.CREATED =>
        assertCreatedHeaders(result)
        if (hasBody(result)) {
          val body = assertBodyIsJson(result)
          assertValidSuccessResponseBody(body, strictMode)
        }
      case Status.NO_CONTENT =>
      // Any assertions to be made here?
      case unknown =>
        fail(s"Unknown response code $unknown")
    }
  }

  /**
   * Determines if the Result has a body (according to the headers)
   * @param result
   * @return
   */
  def hasBody(result: Result): Boolean = {
    if (result.body.isKnownEmpty) {
      false
    } else {
      result.body.contentLength.exists(_ > 0) || result.body.contentType.nonEmpty
    }
  }

  /**
   * Verifies that the body is json, and then returns the body's json.
   * @param result to verify
   * @return body as a JsValue
   */
  def assertBodyIsJson(result: Result): JsValue = {
    val contentType = result.body.contentType
    assert(contentType.isDefined)
    assert(
      contentType.get.startsWith(MimeTypes.JSON),
      s"Content-Type must be json. Found ${contentType.get}")
    contentAsJson(Future.successful(result))
  }

  def assertBodyIsJsObject(body: JsValue): JsObject = {
    assert(body.validate[JsObject].isSuccess, "Body should have been a JSObject.")
    body.as[JsObject]
  }

  def assertValidSuccessResponseBody(body: JsValue, strictMode: Boolean = true): Unit = {
    val bodyObj = assertBodyIsJsObject(body)
    if (strictMode) {
      assertSuccessHasOnlyAllowedFields(bodyObj)
    }
    assertAppropriateElementsFormat(bodyObj, strictMode)
  }

  def assertSuccessHasOnlyAllowedFields(body: JsObject): Unit = {
    val allowedFields = Set("elements", "paging", "linked", "meta")
    assert(
      body.keys.diff(allowedFields).size === 0,
      s"Body had extra keys that it shouldn't have: ${body.keys.diff(allowedFields)}")
  }

  def assertAppropriateElementsFormat(body: JsObject, strictMode: Boolean = true): Unit = {
    assert(body.keys.contains("elements"), "Body must contain the 'elements' field.")
    val elements = body \ "elements"
    assert(elements.validate[JsArray].isSuccess, "Elements should be an array.")
    val arr = elements.as[JsArray]
    arr.value.foreach { entry =>
      assert(
        entry.validate[JsObject].isSuccess,
        s"Entries inside elements must be objects. Found: $entry")
    }
  }

  def assertCreatedHeaders(result: Result): Unit = {
    assert(result.header.headers.contains(HeaderNames.LOCATION))
    assert(result.header.headers.contains("X-Coursera-Id"))
  }

  def assertClientErrorResponse(result: Result): Unit = {

    val json = assertBodyIsJson(result)
    assertErrorResponseBody(json)
  }

  def assertServerErrorResponse(result: Result): Unit = {
    val json = assertBodyIsJson(result)
    assertErrorResponseBody(json)
  }

  def assertErrorResponseBody(body: JsValue): Unit = {
    val bodyObj = assertBodyIsJsObject(body)
    assertObjectHasStringField(bodyObj, "errorCode")
    assertObjectHasStringField(bodyObj, "message", allowNull = true)
  }

  def assertObjectHasStringField(
      body: JsObject,
      fieldName: String,
      allowNull: Boolean = false): String = {
    assert(body.keys.contains(fieldName), s"Body should have field: $fieldName: $body")
    val obj = body.value.get(fieldName).get
    if (!allowNull) {
      assert(obj.validate[JsString].isSuccess, s"Field $fieldName should be string. Found: $obj")
      val str = obj.as[JsString]
      str.value
    } else {
      assert(
        obj.validate[JsString].isSuccess || obj == JsNull,
        s"Field $fieldName should be string or null. Found: $obj")
      if (obj.validate[JsString].isSuccess) {
        obj.as[JsString].value
      } else {
        ""
      }
    }
  }
}
