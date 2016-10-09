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

import java.nio.charset.StandardCharsets

import akka.util.ByteString
import org.coursera.common.jsonformat.JsonFormats
import org.coursera.common.jsonformat.JsonFormats.Implicits.optionalReads
import play.api.http.HeaderNames
import play.api.http.HttpEntity
import play.api.http.MimeTypes
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.__
import play.api.mvc.ResponseHeader
import play.api.mvc.Result

/**
 * Throw to break out of a Naptime action body on error.
 */
case class NaptimeActionException(
    httpCode: Int,
    errorCode: Option[String],
    message: Option[String],
    details: Option[JsValue] = None)
  extends RuntimeException(s"Naptime error $httpCode [$errorCode]: $message") {

  def result: Result = {
    val bodyJson = Json.toJson(NaptimeActionException.Body(errorCode, message, details))
    val bodyString = Json.stringify(bodyJson)
    Result(
      ResponseHeader(httpCode, Map(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)),
      HttpEntity.Strict(ByteString(bodyString), Some(MimeTypes.JSON)))
  }

}

object NaptimeActionException {

  case class Body(
      errorCode: Option[String],
      message: Option[String],
      details: Option[JsValue])

  object Body {

    /**
     * Note: We use a custom format instead of the case class format macro so that empty options
     * become null values in the JSON object instead of being omitted.
     */
    implicit val format: OFormat[Body] = {
      import play.api.libs.functional.syntax.{unapply => _, _}
      val builder =
        (__ \ "errorCode").format[Option[String]] and
        (__ \ "message").format[Option[String]] and
        (__ \ "details").format[Option[JsValue]]
      builder(apply, unlift(unapply))
    }

    object Extract extends JsonFormats.Extract[Body]

  }

}

/**
 * Error responses. Note: these error responses work by throwing special exceptions that are
 * caught by the framework. They are not designed to work outside the framework.
 */
trait Errors {

  import play.api.http.Status._

  /**
   * Error out with a BadRequest (400) response.
   *
   * Note: Only use this within a Rest Action, and not a general action.
   */
  def BadRequest(errorCode: String = null, msg: String = null, details: Option[JsValue] = None) =
    throw new NaptimeActionException(BAD_REQUEST,
      Option(errorCode),
      Option(msg),
      details)

  /**
   * Error out with an Unauthorized (401) response.
   *
   * Note: Only use this within a Rest Action, and not a general action.
   */
  def Unauthorized(errorCode: String = null, msg: String = null, details: Option[JsValue] = None) =
    throw new NaptimeActionException(UNAUTHORIZED,
      Option(errorCode),
      Option(msg),
      details)

  /**
   * Error out with an Forbidden (403) response.
   *
   * Note: Only use this within a Rest Action, and not a general action.
   */
  def Forbidden(errorCode: String = null, msg: String = null, details: Option[JsValue] = None) =
    throw new NaptimeActionException(FORBIDDEN,
      Option(errorCode),
      Option(msg),
      details)

  /**
   * Error out with an Not Found (404) response.
   *
   * Note: Only use this within a Rest Action, and not a general action.
   */
  def NotFound(errorCode: String = null, msg: String = null, details: Option[JsValue] = None) =
    throw new NaptimeActionException(NOT_FOUND,
      Option(errorCode),
      Option(msg),
      details)

  import scala.reflect.runtime.universe.TypeTag

  def NotFound[MissingType](id: Int)(implicit typeTag: TypeTag[MissingType]): Nothing = {
    NotFound("missing", s"No ${typeTag.tpe.toString} with id: $id")
  }

  /**
   * Error out with a conflict (409) response.
   *
   * Note: Only use this within a Rest Action, and not a general action.
   */
  def Conflict(errorCode: String = null, msg: String = null, details: Option[JsValue] = None) =
    throw new NaptimeActionException(CONFLICT,
      Option(errorCode),
      Option(msg),
      details)

  /**
   * Error out with an Gone (410) response.
   *
   * Note: Only use this within a Rest Action, and not a general action.
   */
  def Gone(errorCode: String = null, msg: String = null, details: Option[JsValue] = None) =
    throw new NaptimeActionException(GONE,
      Option(errorCode),
      Option(msg),
      details)

  /**
   * Error out with a precondition failed (412) response.
   *
   * Note: Only use this within a Rest Action, and not a general action.
   */
  def PreconditionFailed(
      errorCode: String = null, msg: String = null, details: Option[JsValue] = None) =
    throw new NaptimeActionException(PRECONDITION_FAILED,
      Option(errorCode),
      Option(msg),
      details)

  /**
   * Error out with an internal server error (500) response.
   *
   * Note: Only use this within a Rest Action, and not a general action.
   */
  def InternalServerError(
      errorCode: String = null, msg: String = null, details: Option[JsValue] = None) =
    throw new NaptimeActionException(INTERNAL_SERVER_ERROR,
      Option(errorCode),
      Option(msg),
      details)

  /**
   * Error out with a bad gateway error (502) response.
   *
   * Note: Only use this within a Rest Action, and not a general action.
   */
  def BadGateway(
      errorCode: String = null, msg: String = null, details: Option[JsValue] = None) =
    throw new NaptimeActionException(BAD_GATEWAY,
      Option(errorCode),
      Option(msg),
      details)

  /**
   * Error out with a service unavailable (503) response.
   *
   * Note: Only use this within a Rest Action, and not a general action.
   */
  def ServiceUnavailable(
      errorCode: String = null, msg: String = null, details: Option[JsValue] = None) =
    throw new NaptimeActionException(SERVICE_UNAVAILABLE,
      Option(errorCode),
      Option(msg),
      details)

  /**
   * Error out with a gateway timeout (504) response.
   *
   * Note: Only use this within a Rest Action, and not a general action.
   */
  def GatewayTimeout(
      errorCode: String = null, msg: String = null, details: Option[JsValue] = None) =
    throw new NaptimeActionException(GATEWAY_TIMEOUT,
      Option(errorCode),
      Option(msg),
      details)


  /**
   * Generate your own HTTP 4XX or 5XX response, specifying your own HTTP code.
   */
  def error(httpCode: Int, errorCode: String = null, msg: String = null, details: Option[JsValue] = None) =
    throw new NaptimeActionException(httpCode, Option(errorCode), Option(msg), details)
}

object Errors extends Errors
