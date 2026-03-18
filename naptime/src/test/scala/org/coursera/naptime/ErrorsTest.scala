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

package org.coursera.naptime

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.http.Status
import play.api.libs.json.{Json, OFormat}

case class ErrorsTestDetail(info: String)

object ErrorsTestDetail {
  implicit val fmt: OFormat[ErrorsTestDetail] = Json.format[ErrorsTestDetail]
}

class ErrorsTest extends AssertionsForJUnit {

  @Test
  def badRequest_hasCorrectHttpCode(): Unit = {
    val e = Errors.BadRequest(errorCode = "bad.code", msg = "bad message")
    assert(e.httpCode === Status.BAD_REQUEST)
    assert(e.errorCode === Some("bad.code"))
    assert(e.message === Some("bad message"))
  }

  @Test
  def badRequest_defaults(): Unit = {
    val e = Errors.BadRequest()
    assert(e.httpCode === Status.BAD_REQUEST)
    assert(e.errorCode === None)
    assert(e.message === None)
  }

  @Test
  def unauthorized_hasCorrectHttpCode(): Unit = {
    val e = Errors.Unauthorized(errorCode = "auth.required", msg = "You must be logged in")
    assert(e.httpCode === Status.UNAUTHORIZED)
    assert(e.errorCode === Some("auth.required"))
  }

  @Test
  def forbidden_hasCorrectHttpCode(): Unit = {
    val e = Errors.Forbidden(errorCode = "access.denied", msg = "Forbidden")
    assert(e.httpCode === Status.FORBIDDEN)
    assert(e.errorCode === Some("access.denied"))
  }

  @Test
  def notFound_hasCorrectHttpCode(): Unit = {
    val e = Errors.NotFound(errorCode = "not.found", msg = "Resource not found")
    assert(e.httpCode === Status.NOT_FOUND)
    assert(e.errorCode === Some("not.found"))
  }

  @Test
  def notFound_withCauseThrowable(): Unit = {
    val cause = new RuntimeException("underlying cause")
    val e = Errors.NotFound(errorCode = "not.found", msg = "not found", cause = Some(cause))
    assert(e.httpCode === Status.NOT_FOUND)
    assert(e.cause === Some(cause))
  }

  @Test
  def conflict_hasCorrectHttpCode(): Unit = {
    val e = Errors.Conflict(errorCode = "conflict", msg = "Conflict")
    assert(e.httpCode === Status.CONFLICT)
  }

  @Test
  def gone_hasCorrectHttpCode(): Unit = {
    val e = Errors.Gone(errorCode = "gone", msg = "Gone")
    assert(e.httpCode === Status.GONE)
  }

  @Test
  def preconditionFailed_hasCorrectHttpCode(): Unit = {
    val e = Errors.PreconditionFailed(errorCode = "precond", msg = "Precondition failed")
    assert(e.httpCode === Status.PRECONDITION_FAILED)
  }

  @Test
  def internalServerError_hasCorrectHttpCode(): Unit = {
    val e = Errors.InternalServerError(errorCode = "ise", msg = "ISE")
    assert(e.httpCode === Status.INTERNAL_SERVER_ERROR)
  }

  @Test
  def badGateway_hasCorrectHttpCode(): Unit = {
    val e = Errors.BadGateway(errorCode = "bad.gateway", msg = "Bad gateway")
    assert(e.httpCode === Status.BAD_GATEWAY)
  }

  @Test
  def serviceUnavailable_hasCorrectHttpCode(): Unit = {
    val e = Errors.ServiceUnavailable(errorCode = "unavailable", msg = "Service unavailable")
    assert(e.httpCode === Status.SERVICE_UNAVAILABLE)
  }

  @Test
  def gatewayTimeout_hasCorrectHttpCode(): Unit = {
    val e = Errors.GatewayTimeout(errorCode = "timeout", msg = "Gateway timeout")
    assert(e.httpCode === Status.GATEWAY_TIMEOUT)
  }

  @Test
  def error_customHttpCode(): Unit = {
    val e = Errors.error(418, errorCode = "teapot", msg = "I'm a teapot")
    assert(e.httpCode === 418)
    assert(e.errorCode === Some("teapot"))
  }

  @Test
  def badRequestT_withDetails(): Unit = {
    implicit val fmt = ErrorsTestDetail.fmt
    val e = Errors.BadRequestT[ErrorsTestDetail](
      errorCode = "bad",
      msg = "bad message",
      details = Some(ErrorsTestDetail("some info")))
    assert(e.httpCode === Status.BAD_REQUEST)
    assert(e.details.isDefined)
  }

  @Test
  def unauthorizedT_withDetails(): Unit = {
    implicit val fmt = ErrorsTestDetail.fmt
    val e = Errors.UnauthorizedT[ErrorsTestDetail](
      errorCode = "auth",
      msg = "auth message",
      details = Some(ErrorsTestDetail("some info")))
    assert(e.httpCode === Status.UNAUTHORIZED)
    assert(e.details.isDefined)
  }

  @Test
  def notFoundT_withDetails(): Unit = {
    implicit val fmt = ErrorsTestDetail.fmt
    val e = Errors.NotFoundT[ErrorsTestDetail](
      errorCode = "missing",
      msg = "not found",
      details = Some(ErrorsTestDetail("some info")))
    assert(e.httpCode === Status.NOT_FOUND)
    assert(e.details.isDefined)
  }

  @Test
  def naptimeActionException_result_hasCorrectStatusCode(): Unit = {
    val e = Errors.BadRequest(errorCode = "bad.code", msg = "bad message")
    val result = e.result
    assert(result.header.status === Status.BAD_REQUEST)
  }

  @Test
  def naptimeActionException_withExceptionDetails(): Unit = {
    implicit val fmt = ErrorsTestDetail.fmt
    val e = Errors.BadRequest(errorCode = "bad.code", msg = "bad message")
    val withDetails = e.withExceptionDetails(Some(ErrorsTestDetail("context info")))
    assert(withDetails.details.isDefined)
  }

  @Test
  def naptimeActionException_withExceptionDetails_noneDetails(): Unit = {
    implicit val fmt = ErrorsTestDetail.fmt
    val e = Errors.BadRequest(errorCode = "bad.code", msg = "bad message")
    val withDetails = e.withExceptionDetails(Option.empty[ErrorsTestDetail])
    assert(withDetails.details.isEmpty)
  }
}
