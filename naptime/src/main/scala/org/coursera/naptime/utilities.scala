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

import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.MapDataSchema
import com.linkedin.data.schema.NamedDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.UnionDataSchema
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.mvc.RequestHeader

import scala.collection.JavaConverters._
import scala.language.existentials

/**
 * Helpers to work with Json.
 */
private[naptime] object JsonUtilities {

  def filterJsonFields(jsObj: JsObject, fields: RequestFields): JsObject = {
    JsObject(jsObj.fields.filter {
      case (field, _) =>
        field == KeyFormat.ID_FIELD || fields.hasField(field)
    })
  }

  def outputOneObj[K, T](obj: Keyed[K, T], fields: RequestFields)(
      implicit writes: OWrites[T],
      keyFormat: KeyFormat[K]): JsObject = {
    val filtered =
      JsonUtilities.filterJsonFields(Keyed.writes.writes(obj), fields)

    val keyFields = keyFormat.format.writes(obj.key)

    keyFields ++ filtered
  }

  def outputSeq[K, T](objs: Seq[Keyed[K, T]], fields: RequestFields)(
      implicit writes: OWrites[T],
      keyWrites: KeyFormat[K]): Seq[JsObject] = {
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

      val filteredRelated = ok.related.filter {
        case (name, _) =>
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
    val visibleIncludes =
      ok.related.filterKeys(requestFields.forResource(_).isDefined)
    val formatted = visibleIncludes.map {
      case (name, related) =>
        name.identifier ->
          related.fields.makeMetaRelationsMap(queryIncludes.resources.getOrElse(name, Set.empty))
    }.toList
    JsObject(
      "elements" -> fields
        .makeMetaRelationsMap(queryIncludes.fields) :: formatted)
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

/**
 * Helpers for working with Courier/Pegasus Schemas.
 */
object SchemaUtils {

  /**
   * Fixes up inferred schemas with additional type information.
   *
   * When computing the schemas for resources, if the model or value type is not a courier
   * (or known type), the macro calls [[org.coursera.naptime.courier.SchemaInference]], which
   * attempts to infer the schema via reflection. There are a number of types that the schema
   * inferencer cannot handle. This function will take a scala-guice map-binding and fix up
   * the inappropriately inferred schemas with configured types.
   *
   * Note: this is a destructive operation, and mutates the input record data schema. Please
   * use with care to ensure that the schemas are not being modified inappropriately.
   */
  def fixupInferredSchemas(
      schemaToFix: RecordDataSchema,
      typeOverrides: NaptimeModule.SchemaTypeOverrides,
      visitedFields: Set[String] = Set.empty): Unit = {

    def getTypeOverride(schema: DataSchema): Option[DataSchema] = {
      schema.getDereferencedDataSchema match {
        case named: NamedDataSchema if typeOverrides.contains(named.getFullName) =>
          typeOverrides.get(named.getFullName)
        case _ =>
          None
      }
    }

    def recursivelyFixup(schema: DataSchema) = {
      schema.getDereferencedDataSchema match {
        case recordType: RecordDataSchema if !visitedFields.contains(recordType.getFullName) =>
          val updatedVisitedFields = visitedFields + recordType.getFullName
          fixupInferredSchemas(recordType, typeOverrides, updatedVisitedFields)
        case _ =>
      }
    }

    Option(schemaToFix.getFields).map(_.asScala).getOrElse(List.empty).foreach { field =>
      val fieldType = field.getType
      Option(fieldType.getDereferencedDataSchema).foreach {

        case named: NamedDataSchema if typeOverrides.contains(named.getFullName) =>
          getTypeOverride(named).foreach { overrideType =>
            field.setType(overrideType)
          }

        case arrayType: ArrayDataSchema =>
          getTypeOverride(arrayType.getItems).foreach { overrideType =>
            field.setType(new ArrayDataSchema(overrideType))
          }
          recursivelyFixup(arrayType.getItems)

        case unionType: UnionDataSchema =>
          val updatedTypes = unionType.getTypes.asScala.map { innerType =>
            recursivelyFixup(innerType)
            getTypeOverride(innerType).getOrElse(innerType)
          }
          val newUnion = new UnionDataSchema()
          val stringBuilder = new java.lang.StringBuilder()
          newUnion.setTypes(updatedTypes.asJava, stringBuilder)
          newUnion

        case mapType: MapDataSchema =>
          getTypeOverride(mapType.getValues).foreach { overrideType =>
            field.setType(new MapDataSchema(overrideType))
          }
          recursivelyFixup(mapType.getValues)

        case recordType: RecordDataSchema if !visitedFields.contains(recordType.getFullName) =>
          val updatedVisitedFields = visitedFields + recordType.getFullName
          fixupInferredSchemas(recordType, typeOverrides, updatedVisitedFields)

        case named: NamedDataSchema =>
          getTypeOverride(named).foreach { overrideType =>
            field.setType(overrideType)
          }

        case _ => // Do nothing otherwise.
      }
    }
  }
}
