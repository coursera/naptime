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

import akka.stream.Materializer
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.RestError
import org.coursera.naptime.access.HeaderAccessControl
import play.api.http.Status
import play.api.libs.json.JsArray
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.Reads
import play.api.libs.streams.Accumulator
import play.api.mvc.BodyParser
import play.api.mvc.BodyParsers
import play.api.mvc.RequestHeader
import play.api.mvc.Result

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

/**
 * A builder that helps build Rest Actions.
 */
class RestActionBuilder[RACType, AuthType, BodyType, ResourceKeyType, ResourceType, ResponseType](
    auth: HeaderAccessControl[AuthType],
    bodyParser: BodyParser[BodyType],
    errorHandler: PartialFunction[Throwable, RestError])(
    implicit keyFormat: KeyFormat[ResourceKeyType],
    resourceFormat: OFormat[ResourceType],
    ec: ExecutionContext,
    mat: Materializer)
    extends RestActionBuilderTerminators[
      RACType,
      AuthType,
      BodyType,
      ResourceKeyType,
      ResourceType,
      ResponseType] {

  /**
   * Set the body type.
   */
  def body[NewBodyType](bodyParser: BodyParser[NewBodyType]): DefinedBodyTypeRestActionBuilder[
    RACType,
    AuthType,
    NewBodyType,
    ResourceKeyType,
    ResourceType,
    ResponseType] =
    new DefinedBodyTypeRestActionBuilder(auth, bodyParser, errorHandler)

  /**
   * Set the authentication framework.
   */
  def auth[NewAuthType](auth: HeaderAccessControl[NewAuthType]): RestActionBuilder[
    RACType,
    NewAuthType,
    BodyType,
    ResourceKeyType,
    ResourceType,
    ResponseType] =
    new RestActionBuilder(auth, bodyParser, errorHandler)

  /**
   * Adds an error handling function to allow exceptions to generate custom errors.
   *
   * Note: all of the partial functions are stacked, with later functions getting an earlier crack
   * at an exception to handle it.
   *
   * @param errorHandler Error handling partial function.
   * @return the immutable RestActionBuilder to be used to build the naptime resource action.
   */
  def catching(errorHandler: PartialFunction[Throwable, RestError])
    : RestActionBuilder[RACType, AuthType, BodyType, ResourceKeyType, ResourceType, ResponseType] =
    new RestActionBuilder(auth, bodyParser, errorHandler.orElse(this.errorHandler))

  /**
   * Set the response type.
   * TODO: is this necessary?
   */
  def returning[NewResponseType](): RestActionBuilder[
    RACType,
    AuthType,
    BodyType,
    ResourceKeyType,
    ResourceType,
    NewResponseType] =
    new RestActionBuilder(auth, bodyParser, errorHandler)

  def rawJsonBody(maxLength: Int = 100 * 1024) = body(BodyParsers.parse.tolerantJson(maxLength))

  def jsonBody[NewBodyType](implicit reads: Reads[NewBodyType]): DefinedBodyTypeRestActionBuilder[
    RACType,
    AuthType,
    NewBodyType,
    ResourceKeyType,
    ResourceType,
    ResponseType] = {
    jsonBody[NewBodyType]()
  }

  def jsonBody[NewBodyType](maxLength: Int = 100 * 1024)(
      implicit reads: Reads[NewBodyType]): DefinedBodyTypeRestActionBuilder[
    RACType,
    AuthType,
    NewBodyType,
    ResourceKeyType,
    ResourceType,
    ResponseType] = {

    val parser: BodyParser[NewBodyType] = new BodyParser[NewBodyType] with StrictLogging {
      override def apply(
          rh: RequestHeader): Accumulator[ByteString, Either[Result, NewBodyType]] = {
        val innerParser = BodyParsers.parse.tolerantJson(maxLength)
        innerParser(rh).map(_.right.map(toJsObj).joinRight)
      }

      private[this] def toJsObj(js: JsValue): Either[Result, NewBodyType] = {
        try {
          js.validate[NewBodyType] match {
            case JsSuccess(obj, _) =>
              Right(obj)
            case JsError(parseErrors) =>
              val errorDetails = JsObject(for {
                (path, errors) <- parseErrors
              } yield {
                path.toString -> JsArray(for {
                  error <- errors
                } yield {
                  Json.obj("message" -> error.message, "args" -> error.args.map(_.toString))
                })
              })
              val response = NaptimeActionException(
                Status.BAD_REQUEST,
                None,
                Some("JSON didn't validate"),
                Some(errorDetails))
              Left(response.result)
          }
        } catch {
          case e: IllegalArgumentException =>
            logger.info(s"Request failed validation.", e)
            val resp = NaptimeActionException(
              Status.BAD_REQUEST,
              Some("request.validation"),
              Some(e.getMessage),
              None,
              Some(e))
            Left(resp.result)
          case NonFatal(e) =>
            logger.error(s"Unknown exception while parsing body of request.", e)
            throw e
        }
      }
    }

    body(parser)
  }

  override protected def bodyBuilder[Category, Response](): BodyBuilder[Category, Response] = {
    new RestActionBodyBuilder[
      Category,
      AuthType,
      BodyType,
      ResourceKeyType,
      ResourceType,
      Response](auth, bodyParser, errorHandler)
  }

}
