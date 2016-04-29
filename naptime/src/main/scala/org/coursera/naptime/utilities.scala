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

import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.mvc.RequestHeader

import scala.language.existentials

/**
 * Helpers to work with Json.
 */
private[naptime] object JsonUtilities {

  def filterJsonFields(jsObj: JsObject, fields: RequestFields): JsObject = {
    JsObject(jsObj.fields.filter { case (field, _) =>
      field == KeyFormat.ID_FIELD || fields.hasField(field)
    })
  }

  def outputOneObj[K, T](obj: Keyed[K, T], fields: RequestFields)
      (implicit writes: OWrites[T], keyFormat: KeyFormat[K]): JsObject = {
    val filtered = JsonUtilities.filterJsonFields(Keyed.writes.writes(obj), fields)

    // TODO(saeta): Modify this to reflect the resolution in INFRA-2754.
    val keyFields = keyFormat.format.writes(obj.key)

    keyFields ++ filtered
  }

  def outputSeq[K, T](objs: Seq[Keyed[K, T]], fields: RequestFields)
      (implicit writes: OWrites[T], keyWrites: KeyFormat[K]): Seq[JsObject] = {
    objs.map { obj =>
      outputOneObj(obj, fields)
    }
  }

  def formatInclude[K, M](related: Ok.Related[K, M], fields: RequestFields): (String, JsArray) = {
    related.resourceName.identifier -> JsArray(related.toJson(fields))
  }

  def formatIncludes(
      ok: Ok[_],
      requestFields: RequestFields,
      queryIncludes: QueryIncludes,
      fields: Fields[_]): Option[JsObject] = {
    if (ok.related.isEmpty || queryIncludes.fields.isEmpty) {
      None
    } else {
      case class FieldsHolder(fields: Fields[_])
      val fieldsMap = ok.related.map { related =>
        related._1 -> FieldsHolder(related._2.fields)
      }
      val oneHopResourcesToInclude = queryIncludes.fields.flatMap { fieldName =>
        fields.relations.get(fieldName)
      }.toSet
      val multiHopeResourcesToInclude = (for {
        (resourceName, fieldNames) <- queryIncludes.resources
        fieldName <- fieldNames
        resourceFields <- fieldsMap.get(resourceName)
        resourceToInclude <- resourceFields.fields.relations.get(fieldName)
      } yield resourceToInclude).toSet

      val resourcesToInclude = oneHopResourcesToInclude ++ multiHopeResourcesToInclude

      val filteredRelated = ok.related.filter { case (name, _) =>
        resourcesToInclude.contains(name)
      }.values

      Some(JsObject(filteredRelated.map(formatInclude(_, requestFields)).toList))
    }
  }

  def requestAskingForLinksInformation(request: RequestHeader): Boolean = {
    request.queryString.get("includes").exists { includes =>
      includes.exists(_.contains("_links"))
    }
  }

  def formatLinksMeta(
      queryIncludes: QueryIncludes,
      requestFields: RequestFields,
      fields: Fields[_],
      ok: Ok[_]): JsObject = {
    // Don't bother outputting metadata if there are no fields present in the response.
    val visibleIncludes = ok.related.filterKeys(requestFields.forResource(_).isDefined)
    val formatted = visibleIncludes.map { case (name, related) =>
      name.identifier ->
        related.fields.makeMetaRelationsMap(queryIncludes.resources.getOrElse(name, Set.empty))
    }.toList
    JsObject("elements" -> fields.makeMetaRelationsMap(queryIncludes.fields) :: formatted)
  }

  // TODO(future): Format differently based on the request header accepts.
  def formatSuccessfulResponseBody(
      response: Ok[_],
      elements: JsValue,
      fields: Fields[_],
      request: RequestHeader,
      requestFields: RequestFields,
      queryIncludes: QueryIncludes): JsObject = {
    var obj = Json.obj(
      "elements" -> elements,
      "paging" -> Json.toJson(response.pagination),
      "linked" -> formatIncludes(response, requestFields, queryIncludes, fields)
    )
    if (requestAskingForLinksInformation(request)) {
      obj = obj + ("links" -> formatLinksMeta(queryIncludes, requestFields, fields, response))
    }
    obj
  }
}
