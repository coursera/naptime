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
import org.coursera.naptime.model.Keyed
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
    mat: Materializer) {

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
   * Set the body type.
   */
  def body[NewBodyType](bodyParser: BodyParser[NewBodyType]): RestActionBuilder[
    RACType,
    AuthType,
    NewBodyType,
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

  def rawJsonBody(maxLength: Int = 100 * 1024) =
    body(BodyParsers.parse.tolerantJson(maxLength))

  def jsonBody[NewBodyType](implicit reads: Reads[NewBodyType]): RestActionBuilder[
    RACType,
    AuthType,
    NewBodyType,
    ResourceKeyType,
    ResourceType,
    ResponseType] = {
    jsonBody[NewBodyType]()
  }

  def jsonBody[NewBodyType](maxLength: Int = 100 * 1024)(
      implicit reads: Reads[NewBodyType]): RestActionBuilder[
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
              None)
            Left(resp.result)
          case NonFatal(e) =>
            logger.error(s"Unknown exception while parsing body of request.", e)
            throw e
        }
      }
    }

    body(parser)
  }

  type BodyBuilder[Category, Response] =
    RestActionBodyBuilder[Category, AuthType, BodyType, ResourceKeyType, ResourceType, Response]

  private[this] def bodyBuilder[Category, Response](): BodyBuilder[Category, Response] = {
    new RestActionBodyBuilder[
      Category,
      AuthType,
      BodyType,
      ResourceKeyType,
      ResourceType,
      Response](auth, bodyParser, errorHandler)
  }

  // Define all the available REST action types and categories.
  /**
   * Gets a resource by ID
   *
   * Example:
   * {{{
   * def get(id: Int) = Rest.get { ctx =>
   *   Ok(Keyed(id, MyResource(name=s"Resource-$id")))
   * }
   * }}}
   *
   * Underlying HTTP request (id = "1"):
   * {{{ GET /api/myResource/1 }}}
   */
  type GetBuilder =
    BodyBuilder[GetRestActionCategory, Keyed[ResourceKeyType, ResourceType]]
  def get: GetBuilder = bodyBuilder()

  /**
   * Gets a batch of resources from this collection.
   *
   * Example:
   * {{{
   * def multiGet(ids: Seq[Int]) = Rest.multiGet { ctx =>
   *   Ok(ids.map(id => Keyed(id, MyResource(name=s"Resource-$id"))))
   * }
   * }}}
   *
   * Underlying HTTP request (ids = "1,2,3,4")
   * {{{ GET /api/myResource?ids=1,2,3,4 }}}
   */
  type MultiGetBuilder =
    BodyBuilder[MultiGetRestActionCategory, Seq[Keyed[ResourceKeyType, ResourceType]]]
  def multiGet: MultiGetBuilder = bodyBuilder()

  /**
   * Gets all elements in a collection. Note: please use paging to avoid OOM-ing.
   *
   * Example:
   * {{{
   * def getAll = Rest.getAll { ctx =>
   *   val results = store.getAll(start=ctx.paging.start, limit=ctx.paging.limit)
   *   Ok(results)
   * }
   * }}}
   *
   * Underlying HTTP request (pagination: start=10, limit=5)
   * {{{ GET /api/myResource?start=10&limit=5 }}}
   */
  type GetAllBuilder =
    BodyBuilder[GetAllRestActionCategory, Seq[Keyed[ResourceKeyType, ResourceType]]]
  def getAll: GetAllBuilder = bodyBuilder()

  /**
   * Creates a new resource given an input.
   *
   * Example:
   * {{{
   * def create = Rest.create { ctx =>
   *   val newElement = createResourceFromBody(ctx.body)
   *   val newId = store.save(newOne)
   *   Ok(Keyed(newId, Some(newElement)))
   * }
   * }}}
   *
   * Underlying HTTP request (body elided):
   * {{{ POST /api/myResource }}}
   */
  type CreateBuilder =
    BodyBuilder[CreateRestActionCategory, Keyed[ResourceKeyType, Option[ResourceType]]]
  def create: CreateBuilder = bodyBuilder()

  /**
   * Updates a resource with a new copy of the resource.
   *
   * Example:
   * {{{
   * def update(id: Int) = Rest.update { ctx =>
   *   val newVersion = validateBody(ctx.body)
   *   store.save(id, newVersion)
   *   Ok(Keyed(id, newVersion))
   * }
   * }}}
   *
   * Underlying HTTP request (body elided, id=2):
   * {{{ PUT /api/myResource/2 }}}
   */
  type UpdateBuilder =
    BodyBuilder[UpdateRestActionCategory, Option[Keyed[ResourceKeyType, ResourceType]]]
  def update: UpdateBuilder = bodyBuilder()

  /**
   * Deletes an element from a collection.
   *
   * Example:
   * {{{
   * def delete(id: Int) = Rest.delete { ctx =>
   *   store.delete(id)
   *   Ok()
   * }
   * }}}
   *
   * Underlying HTTP request (id=4):
   * {{{ DELETE /api/myResource/4 }}}
   */
  type DeleteBuilder = BodyBuilder[DeleteRestActionCategory, Unit]
  def delete: DeleteBuilder = bodyBuilder()

  /**
   * Patch (or partial update) a resource.
   *
   * Example:
   * {{{
   * def patch(id: Int) = Rest.patch { ctx =>
   *   val oldObj = store.get(id)
   *   val newObj = PatchEngine.patch(oldObj, ctx.body)
   *   store.save(id, newObj)
   *   Ok(Keyed(id, newObj))
   * }
   * }}}
   *
   * Underlying HTTP request (id=10, body elided):
   * {{{ PATCH /api/myResource/10 }}}
   */
  type PatchBuilder =
    BodyBuilder[PatchRestActionCategory, Keyed[ResourceKeyType, ResourceType]]
  def patch: PatchBuilder = bodyBuilder()

  /**
   * Retrieval by any method other than retrival by Id [primary key]. (For example, alternate key
   * lookup, or full text search. This is intentionally a flexible API and can be used appropriately
   * in many contexts. A finder MUST NOT have side effects, and must be idempotent.
   *
   * Example:
   * {{{
   * def alternateKey(name: String) = Rest.finder { ctx =>
   *   val results = store.lookupByName(name=name)
   *   Ok(results.toSeq)
   * }
   * }}}
   *
   * Underlying HTTP request (finder name='alternateKey', name='hogwarts'):
   * {{{ GET /api/myResource?q=alternateKey&name=hogwarts }}}
   */
  type FinderResponseType = Seq[Keyed[ResourceKeyType, ResourceType]]
  type FinderBuilder = BodyBuilder[FinderRestActionCategory, FinderResponseType]
  def finder: FinderBuilder = bodyBuilder()

  /**
   * Arbitrary actions on objects that are not standard CRUD operations. This is very flexible, and
   * must be carefully used only for good (and not evil). Actions may have side effects and may not
   * be idempotent.
   *
   * Example:
   * {{{
   * def convertToHtml(ids: Seq[Int]) = Rest.action { ctx =>
   *   val pages = store.multiGet(ids)
   *   val htmlIfied = pages.map { page =>
   *     MarkdownEngine.toHtml(page)
   *   }
   *   store.multiSave(htmlIfied)
   *   Ok(htmlIfied)
   * }
   * }}}
   *
   * Underlying HTTP request (ids=1,2,3,5,8,13):
   * {{{ POST /api/myResource?action=convertToHtml&ids=1,2,3,5,8,13 }}}
   */
  type ActionBuilder[A] = BodyBuilder[ActionRestActionCategory, A]
  def action[A]: ActionBuilder[A] = bodyBuilder()

}
