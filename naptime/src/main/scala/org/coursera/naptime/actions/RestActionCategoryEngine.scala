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

import org.coursera.common.stringkey.StringKey
import org.coursera.naptime.ETag
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.RestError
import org.coursera.naptime.Fields
import org.coursera.naptime.JsonUtilities
import org.coursera.naptime.Ok
import org.coursera.naptime.QueryIncludes
import org.coursera.naptime.Redirect
import org.coursera.naptime.RequestFields
import org.coursera.naptime.RequestPagination
import org.coursera.naptime.RestResponse
import play.api.http.HeaderNames
import play.api.libs.json.JsArray
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Writes
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results

import scala.annotation.implicitNotFound

/**
 * Maps a high-level REST response to a low-level HTTP response.
 */
@implicitNotFound(
  """No RestActionCategoryEngine found for category: ${Category},
    key type: ${Key} and resource type: ${Resource} and response type: ${Response}.
    Most likely, you have an inappropriate response type for your action. Please ensure you are
    returning the right type for this action.""")
// sealed // TODO(saeta): Re-add back in sealing by moving 2nd gen engines to this file.
trait RestActionCategoryEngine[Category, Key, Resource, Response] {
  private[naptime] def mkResult(
      request: RequestHeader,
      resourceFields: Fields[Resource],
      requestFields: RequestFields,
      requestIncludes: QueryIncludes,
      pagination: RequestPagination,
      response: RestResponse[Response]): Result
}

object RestActionCategoryEngine extends RestActionCategoryEngine2Impls // Courier-centric engines

/**
 * Define the mappings between category engines.
 *
 * TODO: consider moving these to be defined in the same trait that defines the resource. (e.g. the
 * [[org.coursera.naptime.resources.CollectionResource]].) By moving the engines to the
 * trait, this would allow for different resources to have different engines available. That said,
 * this must all be sealed to disallow further 'customization'.
 */
trait PlayJsonRestActionCategoryEngine {

  private[this] def mkOkResponse[T](r: RestResponse[T])(fn: Ok[T] => Result) = {
    r match {
      case ok: Ok[T]          => fn(ok)
      case error: RestError   => error.error.result
      case redirect: Redirect => redirect.result
    }
  }

  private[this] object ETagHelpers {
    private[this] def constructEtagHeader(etag: ETag): (String, String) = {
      HeaderNames.ETAG -> StringKey.toStringKey(etag).key
    }

    private[naptime] def addProvidedETag[T](ok: Ok[T]): Option[(String, String)] = {
      ok.eTag.map { eTag =>
        constructEtagHeader(eTag)
      }
    }

    private[naptime] def computeETag(
        jsValue: JsValue,
        pagination: RequestPagination): (String, String) = {
      // For now, use Play!'s built-in hashcode implementations. This should be good enough for now.
      // Note: upgrading Play! versions (even point releases) could change the output of hashCode.
      // Because ETags are just an optimization, we are okay with that for now.
      // Note: JsObject's `hashCode` implementation is not the most efficient, and generates a lot
      // of garbage.
      // Note: JsObject's `fieldSet` (used to compute `hashCode`) does not guarantee single values
      // for a particular key.
      // Note: for pagination, we explicitly call the eTagHashCode that excludes some fields.
      val hashCode =
        Set(jsValue.hashCode(), pagination.eTagHashCode()).hashCode()

      constructEtagHeader(ETag(hashCode.toString))
    }

  }

  private[naptime] def mkETagHeader[T](
      pagination: RequestPagination,
      ok: Ok[T],
      jsRepresentation: JsValue): (String, String) =
    ETagHelpers
      .addProvidedETag(ok)
      .getOrElse(ETagHelpers.computeETag(jsRepresentation, pagination))

  private[naptime] def mkETagHeaderOpt[T](
      pagination: RequestPagination,
      ok: Ok[T],
      jsRepresentation: Option[JsValue]): Option[(String, String)] =
    ETagHelpers
      .addProvidedETag(ok)
      .orElse(jsRepresentation.map(ETagHelpers.computeETag(_, pagination)))

  implicit def getActionCategoryEngine[Key, Resource](
      implicit writes: OWrites[Resource],
      keyWrites: KeyFormat[Key])
    : RestActionCategoryEngine[GetRestActionCategory, Key, Resource, Keyed[Key, Resource]] = {
    new RestActionCategoryEngine[GetRestActionCategory, Key, Resource, Keyed[Key, Resource]] {
      override def mkResult(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Keyed[Key, Resource]]): Result = {
        mkOkResponse(response) { ok =>
          val elements =
            JsArray(Seq(JsonUtilities.outputOneObj(ok.content, requestFields)))
          val body = JsonUtilities.formatSuccessfulResponseBody(
            ok,
            elements,
            resourceFields,
            request,
            requestFields,
            requestIncludes)
          val etag = mkETagHeader(pagination, ok, elements)
          if (request.headers.get(HeaderNames.IF_NONE_MATCH) == Some(etag._2)) {
            Results.NotModified.withHeaders(etag)
          } else {
            Results.Ok(body).withHeaders(etag)
          }
        }
      }
    }
  }

  implicit def createActionCategoryEngine[Key, Resource](
      implicit writes: OWrites[Resource],
      keyWrites: KeyFormat[Key]): RestActionCategoryEngine[
    CreateRestActionCategory,
    Key,
    Resource,
    Keyed[Key, Option[Resource]]] = {

    new RestActionCategoryEngine[
      CreateRestActionCategory,
      Key,
      Resource,
      Keyed[Key, Option[Resource]]] {
      override def mkResult(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Keyed[Key, Option[Resource]]]): Result = {
        mkOkResponse(response) { ok =>
          val key = keyWrites.stringKeyFormat.writes(ok.content.key).key
          val newLocation = if (request.path.endsWith("/")) {
            request.path + key
          } else {
            request.path + "/" + key
          }
          val baseHeaders =
            List(HeaderNames.LOCATION -> newLocation, "X-Coursera-Id" -> key)

          ok.content.value
            .map { value =>
              val keyedResource = Keyed(ok.content.key, value)
              val elements = JsArray(Seq(JsonUtilities.outputOneObj(keyedResource, requestFields)))
              val body =
                JsonUtilities.formatSuccessfulResponseBody(
                  ok,
                  elements,
                  resourceFields,
                  request,
                  requestFields,
                  requestIncludes)
              Results
                .Created(body)
                .withHeaders(mkETagHeader(pagination, ok, elements) :: baseHeaders: _*)
            }
            .getOrElse {
              // No body, just a 201 Created.
              Results.Created.withHeaders(mkETagHeaderOpt(pagination, ok, None).toList ++
                baseHeaders: _*)
            }
        }
      }
    }
  }

  implicit def updateActionCategoryEngine[Key, Resource](
      implicit writes: OWrites[Resource],
      keyWrites: KeyFormat[Key]): RestActionCategoryEngine[
    UpdateRestActionCategory,
    Key,
    Resource,
    Option[Keyed[Key, Resource]]] = {

    new RestActionCategoryEngine[
      UpdateRestActionCategory,
      Key,
      Resource,
      Option[Keyed[Key, Resource]]] {
      override def mkResult(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Option[Keyed[Key, Resource]]]): Result = {
        mkOkResponse(response) { ok =>
          ok.content
            .map { result =>
              val elements =
                JsArray(Seq(JsonUtilities.outputOneObj(result, requestFields)))
              val body =
                JsonUtilities.formatSuccessfulResponseBody(
                  ok,
                  elements,
                  resourceFields,
                  request,
                  requestFields,
                  requestIncludes)
              Results
                .Ok(body)
                .withHeaders(mkETagHeader(pagination, ok, elements))
            }
            .getOrElse {
              Results.NoContent.withHeaders(mkETagHeaderOpt(pagination, ok, None).toList: _*)
            }
        }
      }
    }
  }

  implicit def patchActionCategoryEngine[Key, Resource](
      implicit writes: OWrites[Resource],
      keyWrites: KeyFormat[Key])
    : RestActionCategoryEngine[PatchRestActionCategory, Key, Resource, Keyed[Key, Resource]] = {

    new RestActionCategoryEngine[PatchRestActionCategory, Key, Resource, Keyed[Key, Resource]] {
      override def mkResult(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Keyed[Key, Resource]]): Result = {
        mkOkResponse(response) { ok =>
          val elements =
            JsArray(Seq(JsonUtilities.outputOneObj(ok.content, requestFields)))
          val body = JsonUtilities.formatSuccessfulResponseBody(
            ok,
            elements,
            resourceFields,
            request,
            requestFields,
            requestIncludes)
          Results.Ok(body).withHeaders(mkETagHeader(pagination, ok, elements))
        }
      }
    }
  }

  implicit def deleteActionCategoryEngine[Key, Resource](
      implicit writes: OWrites[Resource],
      keyWrites: KeyFormat[Key])
    : RestActionCategoryEngine[DeleteRestActionCategory, Key, Resource, Unit] = {

    new RestActionCategoryEngine[DeleteRestActionCategory, Key, Resource, Unit] {
      override def mkResult(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Unit]): Result = {
        mkOkResponse(response) { ok =>
          Results.NoContent.withHeaders(mkETagHeaderOpt(pagination, ok, None).toList: _*)
        }
      }
    }
  }

  implicit def multiGetActionCategoryEngine[Key, Resource](
      implicit writes: OWrites[Resource],
      keyWrites: KeyFormat[Key]): RestActionCategoryEngine[
    MultiGetRestActionCategory,
    Key,
    Resource,
    Seq[Keyed[Key, Resource]]] = {

    new RestActionCategoryEngine[
      MultiGetRestActionCategory,
      Key,
      Resource,
      Seq[Keyed[Key, Resource]]] {
      override def mkResult(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Seq[Keyed[Key, Resource]]]): Result = {
        mkOkResponse(response) { ok =>
          val elements =
            Json.toJson(JsonUtilities.outputSeq(ok.content, requestFields))
          val body = JsonUtilities.formatSuccessfulResponseBody(
            ok,
            elements,
            resourceFields,
            request,
            requestFields,
            requestIncludes)
          val etag = mkETagHeader(pagination, ok, elements)
          if (request.headers.get(HeaderNames.IF_NONE_MATCH) == Some(etag._2)) {
            Results.NotModified.withHeaders(etag)
          } else {
            Results.Ok(body).withHeaders(etag)
          }
        }
      }
    }
  }

  implicit def getAllActionCategoryEngine[Key, Resource](
      implicit writes: OWrites[Resource],
      keyWrites: KeyFormat[Key]): RestActionCategoryEngine[
    GetAllRestActionCategory,
    Key,
    Resource,
    Seq[Keyed[Key, Resource]]] = {

    new RestActionCategoryEngine[GetAllRestActionCategory, Key, Resource, Seq[Keyed[Key, Resource]]] {
      override def mkResult(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Seq[Keyed[Key, Resource]]]): Result = {
        mkOkResponse(response) { ok =>
          val elements =
            Json.toJson(JsonUtilities.outputSeq(ok.content, requestFields))
          val body = JsonUtilities.formatSuccessfulResponseBody(
            ok,
            elements,
            resourceFields,
            request,
            requestFields,
            requestIncludes)
          val etag = mkETagHeader(pagination, ok, elements)
          if (request.headers.get(HeaderNames.IF_NONE_MATCH) == Some(etag._2)) {
            Results.NotModified.withHeaders(etag)
          } else {
            Results.Ok(body).withHeaders(etag)
          }
        }
      }
    }
  }

  implicit def finderActionCategoryEngine[Key, Resource](
      implicit writes: OWrites[Resource],
      keyWrites: KeyFormat[Key]): RestActionCategoryEngine[
    FinderRestActionCategory,
    Key,
    Resource,
    Seq[Keyed[Key, Resource]]] = {

    new RestActionCategoryEngine[FinderRestActionCategory, Key, Resource, Seq[Keyed[Key, Resource]]] {
      override def mkResult(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Seq[Keyed[Key, Resource]]]): Result = {
        mkOkResponse(response) { ok =>
          val elements =
            Json.toJson(JsonUtilities.outputSeq(ok.content, requestFields))
          val body = JsonUtilities.formatSuccessfulResponseBody(
            ok,
            elements,
            resourceFields,
            request,
            requestFields,
            requestIncludes)
          val etag = mkETagHeader(pagination, ok, elements)
          if (request.headers.get(HeaderNames.IF_NONE_MATCH) == Some(etag._2)) {
            Results.NotModified.withHeaders(etag)
          } else {
            Results.Ok(body).withHeaders(etag)
          }
        }
      }
    }
  }

  implicit def actionActionCategoryEngine[Key, Resource, Response](
      implicit responseWrites: Writes[Response],
      writes: OWrites[Resource],
      keyWrites: KeyFormat[Key])
    : RestActionCategoryEngine[ActionRestActionCategory, Key, Resource, Response] = {

    new RestActionCategoryEngine[ActionRestActionCategory, Key, Resource, Response] {
      override def mkResult(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Response]): Result = {
        mkOkResponse(response) { ok =>
          val elements = Json.toJson(ok.content)
          Results.Ok(elements)
        }
      }
    }
  }
}

object PlayJsonRestActionCategoryEngine extends PlayJsonRestActionCategoryEngine
