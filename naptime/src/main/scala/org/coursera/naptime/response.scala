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

import com.linkedin.data.DataList
import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.actions.NaptimeSerializer
import org.coursera.naptime.actions.RestActionCategoryEngine2
import play.api.libs.json.JsValue
import play.api.libs.json.OFormat
import play.api.mvc.Result
import play.api.mvc.Results

sealed abstract class RestResponse[+T] {
  def isOk: Boolean
  def isError: Boolean
  def isRedirect: Boolean

  def map[U](fn: T => U): RestResponse[U]
}

final case class Ok[+T](
    content: T,
    related: Map[ResourceName, Ok.Related[_, _]] = Map.empty,
    pagination: Option[ResponsePagination] = None,
    /**
     * Optional ETag to be returned in the response. Note that quotes required for ETag response
     * header are added by Naptime, so `eTag` should be without quotes.
     */
    eTag: Option[ETag] = None)
    extends RestResponse[T] {

  override val isOk = true
  override val isError = false
  override val isRedirect = false

  override def map[U](fn: T => U): RestResponse[U] = copy(content = fn(content))

  def withPagination(next: String): Ok[T] = {
    copy(pagination = Some(ResponsePagination(Some(next), None, None)))
  }

  def withPagination(
      next: Option[String],
      total: Option[Long] = None,
      facets: Option[Map[String, FacetField]] = None): Ok[T] = {
    copy(pagination = Some(ResponsePagination(next, total, facets)))
  }

  def withPagination(pagination: ResponsePagination): Ok[T] = {
    copy(pagination = Some(pagination))
  }

  def withRelated[K, A](name: ResourceName, objects: Seq[Keyed[K, A]])(
      implicit valueFormat: OFormat[A],
      keyFormat: KeyFormat[K],
      fields: Fields[A]): Ok[T] = {
    val newRelated = related + (name -> Ok.Related(name, objects, valueFormat, keyFormat, fields))
    copy(related = newRelated)
  }

  def withETag(eTag: ETag): Ok[T] = copy(eTag = Some(eTag))

}

object Ok {

  /**
   * Captures a related collection of objects.
   */
  case class Related[K, A](
      resourceName: ResourceName,
      objects: Seq[Keyed[K, A]],
      jsonFormat: OFormat[A],
      keyFormat: KeyFormat[K],
      fields: Fields[A]) {
    def toJson(requestFields: RequestFields): Seq[JsValue] = {
      val finalFields = requestFields
        .forResource(resourceName)
        .getOrElse(RequestFields.empty)
        .mergeWithDefaults(fields.defaultFields)
      JsonUtilities.outputSeq(objects, finalFields)(jsonFormat, keyFormat)
    }

    def toPegasus(requestFields: RequestFields, dataList: DataList): RequestFields = {
      RestActionCategoryEngine2.serializeCollection(
        dataList,
        objects,
        keyFormat,
        NaptimeSerializer.playJsonFormats(jsonFormat),
        requestFields,
        fields)
    }
  }

}

final case class RestError(error: NaptimeActionException) extends RestResponse[Nothing] {
  override val isOk = false
  override val isError = true
  override val isRedirect = false

  override def map[U](fn: Nothing => U): RestResponse[U] = this
}

final case class Redirect(url: String, isTemporary: Boolean) extends RestResponse[Nothing] {
  override val isOk = false
  override val isError = false
  override val isRedirect = true

  override def map[U](fn: Nothing => U): RestResponse[U] = this

  def result: Result = {
    if (isTemporary) {
      Results.TemporaryRedirect(url)
    } else {
      Results.MovedPermanently(url)
    }
  }
}
