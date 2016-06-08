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

import java.lang.Iterable
import java.util.Map.Entry

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.linkedin.data.Data
import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.codec.JacksonDataCodec
import com.linkedin.data.codec.JacksonDataCodec.JsonTraverseCallback
import org.coursera.pegasus.TypedDefinitionDataCoercer
import org.coursera.naptime.AllFields
import org.coursera.naptime.DelegateFields
import org.coursera.naptime.RestError
import org.coursera.naptime.FacetField
import org.coursera.naptime.Fields
import org.coursera.naptime.Ok
import org.coursera.naptime.QueryIncludes
import org.coursera.naptime.Redirect
import org.coursera.naptime.RequestFields
import org.coursera.naptime.RequestPagination
import org.coursera.naptime.ResourceName
import org.coursera.naptime.RestResponse
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.Status
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results
import java.io.IOException

import org.coursera.naptime.RequestIncludes
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed

import scala.collection.JavaConversions._

/**
 * 2nd generation engines with Pegasus DataMaps at the core. To use, import them at the top of your
 * file.
 */
object RestActionCategoryEngine2 {

  private[this] def mkOkResponse[T](r: RestResponse[T])(fn: Ok[T] => Result) = {
    r match {
      case ok: Ok[T] => fn(ok)
      case error: RestError => error.error.result
      case redirect: Redirect => redirect.result
    }
  }

  private[this] object ETagHelpers {
    private[this] def constructEtagHeader(etag: String): (String, String) = {
      // ETag needs to be a quoted string. See specs here:
      // http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.11
      HeaderNames.ETAG -> ("\"" + etag + "\"")
    }

    private[naptime] def addProvidedETag[T](ok: Ok[T]): Option[(String, String)] = {
      ok.eTag.map { eTag =>
        constructEtagHeader(eTag)
      }
    }

    private[naptime] def computeETag(dataMap: DataMap,
      pagination: RequestPagination): (String, String) = {
      // For now, use Play!'s built-in hashcode implementations. This should be good enough for now.
      // Note: upgrading Play! versions (even point releases) could change the output of hashCode.
      // Because ETags are just an optimization, we are okay with that for now.
      // Note: for pagination, we explicitly call the eTagHashCode that excludes some fields.
      val hashCode = Set(dataMap.hashCode(), pagination.eTagHashCode()).hashCode()
      constructEtagHeader(hashCode.toString)
    }

  }

  private[naptime] def mkETagHeader[T](pagination: RequestPagination, ok: Ok[T],
    jsRepresentation: DataMap): (String, String) =
    ETagHelpers.addProvidedETag(ok).getOrElse(ETagHelpers.computeETag(jsRepresentation, pagination))

  private[naptime] def mkETagHeaderOpt[T](pagination: RequestPagination, ok: Ok[T],
    jsRepresentation: Option[DataMap]): Option[(String, String)] =
    ETagHelpers.addProvidedETag(ok).orElse(
      jsRepresentation.map(ETagHelpers.computeETag(_, pagination)))

  /**
   * Serializes a collection of Keyed resources into the provided DataList
   *
   * Note: be sure to pre-insert the dataList into the larger response before calling this function
   * in order to avoid expensive and unnecessary cycle checks.
 *
   * @return The complete collection of fields that should be included in the response (includes the
   *         key fields)
   */
  private[naptime] def serializeCollection[K, V](
      dataList: DataList,
      things: scala.collection.Iterable[Keyed[K, V]],
      keyFormat: KeyFormat[K],
      serializer: NaptimeSerializer[V],
      requestFields: RequestFields,
      fields: Fields[V]): RequestFields = {
    // Compute the set of field names provided by the Key type to avoid filtering them out in the
    // response serializer. (This is to maintain backwards compatibility with the legacy Rest
    // Engines.)
    val wireConverter = for {
      first <- things.headOption
      schema <- serializer.schema(first.value)
    } yield new TypedDefinitionDataCoercer(schema)

    // TODO: Verify this is a performant way of computing this. (i.e. consider mutability)
    var keyFields = Set("id")
    for (elem <- things) {
      val key = keyFormat.format.writes(elem.key)
      keyFields ++= key.keys
      // Make a new dataMap and copy other things into it, because the others are locked.
      val dataMap = new DataMap()
      dataList.add(dataMap) // Eagerly insert.
      val valueDataMap = serializer.serialize(elem.value)
      val keyMap = NaptimeSerializer.PlayJson.serialize(key)
      // Insert all the value entries into the data map.
      for (elem <- valueDataMap.entrySet) {
        dataMap.put(elem.getKey, elem.getValue)
      }
      wireConverter.foreach { converter =>
        converter.convertUnionToTypedDefinitionInPlace(dataMap)
      }
      // Insert all the key entries into the data map, overriding any previously set values.
      for (elem <- keyMap.entrySet()) {
        dataMap.put(elem.getKey, elem.getValue)
      }
      // Include the id field if it hasn't been included already.
      if (!dataMap.containsKey("id")) {
        dataMap.put("id", keyFormat.stringKeyFormat.writes(elem.key).key)
      }
    }
    requestFields.mergeWithDefaults(keyFields ++ fields.defaultFields)
  }

  /**
   * Call this after calling [[serializeCollection()]], passing in the returned [[RequestFields]]
 *
   * @return Pass the returned RequestFields to construct the [[FlattenedFilteringJacksonDataCodec]]
   */
  private[this] def serializeRelated[T](
      linked: DataMap,
      response: Ok[T],
      resourceFields: Fields[_],
      requestIncludes: RequestIncludes,
      requestFields: RequestFields): RequestFields = {
    val updatedRelatedFields = for {
      (resourceName, relation) <- response.related
      fieldName <- resourceFields.inverseRelations.get(resourceName)
      if requestIncludes.includeFieldsRelatedResource(fieldName)
    } yield {
      val dataList = new DataList()
      linked.put(resourceName.identifier, dataList)
      val relationFields = requestFields.forResource(resourceName).getOrElse(RequestFields.empty)
      resourceName -> relation.toPegasus(relationFields, dataList)
    }
    DelegateFields(requestFields, updatedRelatedFields)
  }

  private[this] def mkDataCollections() = {
    val response = new DataMap()
    val elements = new DataList()
    response.put("elements", elements)
    val paging = new DataMap()
    response.put("paging", paging)
    val linked = new DataMap()
    response.put("linked", linked)
    (response, elements, paging, linked)
  }

  case class ProcessedResponse(
      response: DataMap,
      codec: FlattenedFilteringJacksonDataCodec,
      etag: (String, String)) {
    def elements: DataMap = response.get("elements").asInstanceOf[DataMap]
    def paging: DataMap = response.get("paging").asInstanceOf[DataMap]
    def linked: DataMap = response.get("linked").asInstanceOf[DataMap]

    def playResponse(code: Int, ifNoneMatchHeader: Option[String]): Result = {
      if (ifNoneMatchHeader.contains(etag._2)) {
        Results.NotModified.withHeaders(etag)
      } else {
        Results.Status(code)(codec.mapToBytes(response)).as(ContentTypes.JSON).withHeaders(etag)
      }
    }
  }

  private[this] def serializeFacets(dataMap: DataMap, facets: Map[String, FacetField]): Unit = {
    for {
      (key, value) <- facets
      if value.facetEntries.nonEmpty || value.fieldCardinality.isDefined
    } {
      val facetArray = new DataList()
      val facetMap = new DataMap()
      facetMap.put("facetEntries", facetArray)
      dataMap.put(key, facetArray)
      value.fieldCardinality.foreach { cardinality =>
        facetMap.put("fieldCardinality", new java.lang.Long(cardinality))
      }
      for (facetEntry <- value.facetEntries) {
        val facetEntryDataMap = new DataMap()
        facetArray.add(facetEntryDataMap)
        facetEntryDataMap.put("id", facetEntry.id)
        facetEntryDataMap.put("count", new java.lang.Long(facetEntry.count))
        facetEntry.name.foreach { name =>
          facetEntryDataMap.put("name", name)
        }
      }
    }
  }

  private[this] def buildResponse[K, V](
      things: scala.collection.Iterable[Keyed[K, V]],
      ok: Ok[_],
      keyFormat: KeyFormat[K],
      serializer: NaptimeSerializer[V],
      requestFields: RequestFields,
      requestIncludes: RequestIncludes,
      fields: Fields[V],
      pagination: RequestPagination): ProcessedResponse = {
    val (response, elements, paging, linked) = mkDataCollections()
    val elementsFields = serializeCollection(
      elements, things, keyFormat, serializer, requestFields, fields)
    ok.pagination.foreach { pagination =>
      pagination.next.foreach { next =>
        paging.put("next", next)
      }
      pagination.total.foreach { total =>
        paging.put("total", new java.lang.Long(total))
      }
      pagination.facets.foreach { facets =>
        val facetsMap = new DataMap()
        paging.put("facets", facetsMap)
        serializeFacets(facetsMap, facets)
      }
    }
    val newFields = serializeRelated(linked, ok, fields, requestIncludes, elementsFields)
    val codec = new FlattenedFilteringJacksonDataCodec(newFields)
    val etag = mkETagHeader(pagination, ok, response)
    ProcessedResponse(response, codec, etag)
  }

  implicit def getActionCategoryEngine[Key, Resource](
      implicit naptimeSerializer: NaptimeSerializer[Resource], keyFormat: KeyFormat[Key]):
    RestActionCategoryEngine[GetRestActionCategory, Key, Resource, Keyed[Key, Resource]] = {
    new RestActionCategoryEngine[GetRestActionCategory, Key, Resource, Keyed[Key, Resource]] {
      override def mkResponse(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Keyed[Key, Resource]]): Result = {
        mkOkResponse(response) { ok =>
          val response = buildResponse(List(ok.content), ok, keyFormat, naptimeSerializer,
            requestFields, requestIncludes, resourceFields, pagination)
          response.playResponse(Status.OK, request.headers.get(HeaderNames.IF_NONE_MATCH))
        }
      }
    }
  }

  implicit def createActionCategoryEngine[Key, Resource](
      implicit naptimeSerializer: NaptimeSerializer[Resource], keyFormat: KeyFormat[Key]):
    RestActionCategoryEngine[CreateRestActionCategory, Key, Resource,
      Keyed[Key, Option[Resource]]] = {

    new RestActionCategoryEngine[CreateRestActionCategory, Key, Resource,
        Keyed[Key, Option[Resource]]] {
      override def mkResponse(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Keyed[Key, Option[Resource]]]): Result = {
        mkOkResponse(response) { ok =>
          val key = keyFormat.stringKeyFormat.writes(ok.content.key).key
          val newLocation = if (request.path.endsWith("/")) {
            request.path + key
          } else {
            request.path + "/" + key
          }
          val baseHeaders = List(HeaderNames.LOCATION -> newLocation, "X-Coursera-Id" -> key)

          ok.content.value.map { value =>
            val response = buildResponse(List(Keyed(ok.content.key, value)), ok, keyFormat,
              naptimeSerializer, requestFields, requestIncludes, resourceFields, pagination)
            response.playResponse(Status.CREATED, None).withHeaders(baseHeaders: _*)
          }.getOrElse {
            // No body, just a 201 Created.
            Results.Created.withHeaders(mkETagHeaderOpt(pagination, ok, None).toList ++
              baseHeaders: _*)
          }
        }
      }
    }
  }

  implicit def updateActionCategoryEngine[Key, Resource](
      implicit naptimeSerializer: NaptimeSerializer[Resource], keyFormat: KeyFormat[Key]):
    RestActionCategoryEngine[UpdateRestActionCategory, Key, Resource,
      Option[Keyed[Key, Resource]]] = {

    new RestActionCategoryEngine[UpdateRestActionCategory, Key, Resource,
        Option[Keyed[Key, Resource]]] {
      override def mkResponse(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Option[Keyed[Key, Resource]]]): Result = {
        mkOkResponse(response) { ok =>
          ok.content.map { result =>
            val response = buildResponse(List(result), ok, keyFormat,
              naptimeSerializer, requestFields, requestIncludes, resourceFields, pagination)
            response.playResponse(Status.OK, None)
          }.getOrElse {
            Results.NoContent.withHeaders(mkETagHeaderOpt(pagination, ok, None).toList: _*)
          }
        }
      }
    }
  }

  implicit def patchActionCategoryEngine[Key, Resource](
      implicit naptimeSerializer: NaptimeSerializer[Resource], keyFormat: KeyFormat[Key]):
    RestActionCategoryEngine[PatchRestActionCategory, Key, Resource, Keyed[Key, Resource]] = {

    new RestActionCategoryEngine[PatchRestActionCategory, Key, Resource, Keyed[Key, Resource]] {
      override def mkResponse(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Keyed[Key, Resource]]): Result = {
        mkOkResponse(response) { ok =>
          val response = buildResponse(List(ok.content), ok, keyFormat, naptimeSerializer,
            requestFields, requestIncludes, resourceFields, pagination)
          response.playResponse(Status.OK, None)
        }
      }
    }
  }

  implicit def deleteActionCategoryEngine[Key, Resource](
      implicit naptimeSerializer: NaptimeSerializer[Resource], keyFormat: KeyFormat[Key]):
    RestActionCategoryEngine[DeleteRestActionCategory, Key, Resource, Unit] = {

    new RestActionCategoryEngine[DeleteRestActionCategory, Key, Resource, Unit] {
      override def mkResponse(
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
      implicit naptimeSerializer: NaptimeSerializer[Resource], keyFormat: KeyFormat[Key]):
    RestActionCategoryEngine[
      MultiGetRestActionCategory, Key, Resource, Seq[Keyed[Key, Resource]]] = {

    new RestActionCategoryEngine[
      MultiGetRestActionCategory, Key, Resource, Seq[Keyed[Key, Resource]]] {
      override def mkResponse(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Seq[Keyed[Key, Resource]]]): Result = {
        mkOkResponse(response) { ok =>
          val response = buildResponse(ok.content, ok, keyFormat, naptimeSerializer, requestFields,
            requestIncludes, resourceFields, pagination)
          response.playResponse(Status.OK, request.headers.get(HeaderNames.IF_NONE_MATCH))
        }
      }
    }
  }

  implicit def getAllActionCategoryEngine[Key, Resource](
      implicit naptimeSerializer: NaptimeSerializer[Resource], keyFormat: KeyFormat[Key]):
    RestActionCategoryEngine[GetAllRestActionCategory, Key, Resource,
      Seq[Keyed[Key, Resource]]] = {

    new RestActionCategoryEngine[GetAllRestActionCategory, Key, Resource,
      Seq[Keyed[Key, Resource]]] {
      override def mkResponse(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Seq[Keyed[Key, Resource]]]): Result = {
        mkOkResponse(response) { ok =>
          val response = buildResponse(ok.content, ok, keyFormat, naptimeSerializer, requestFields,
            requestIncludes, resourceFields, pagination)
          response.playResponse(Status.OK, request.headers.get(HeaderNames.IF_NONE_MATCH))
        }
      }
    }
  }

  implicit def finderActionCategoryEngine[Key, Resource](
      implicit naptimeSerializer: NaptimeSerializer[Resource], keyFormat: KeyFormat[Key]):
    RestActionCategoryEngine[FinderRestActionCategory, Key, Resource, Seq[Keyed[Key, Resource]]] = {

    new RestActionCategoryEngine[
      FinderRestActionCategory, Key, Resource, Seq[Keyed[Key, Resource]]] {
      override def mkResponse(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Seq[Keyed[Key, Resource]]]): Result = {
        mkOkResponse(response) { ok =>
          val response = buildResponse(ok.content, ok, keyFormat, naptimeSerializer, requestFields,
            requestIncludes, resourceFields, pagination)
          response.playResponse(Status.OK, request.headers.get(HeaderNames.IF_NONE_MATCH))
        }
      }
    }
  }

  implicit def actionActionCategoryEngine[Key, Resource, Response](
      implicit naptimeSerializer: NaptimeSerializer[Resource], keyFormat: KeyFormat[Key],
      responseWrites: NaptimeSerializer[Response]):
    RestActionCategoryEngine[ActionRestActionCategory, Key, Resource, Response] = {

    new RestActionCategoryEngine[ActionRestActionCategory, Key, Resource, Response] {
      override def mkResponse(
          request: RequestHeader,
          resourceFields: Fields[Resource],
          requestFields: RequestFields,
          requestIncludes: QueryIncludes,
          pagination: RequestPagination,
          response: RestResponse[Response]): Result = {
        mkOkResponse(response) { ok =>
          val responseBody = responseWrites.serialize(ok.content)
          val codec = new JacksonDataCodec() // No filtering (maintains backwards compatibility)
          Results.Ok(codec.mapToBytes(responseBody)).as(ContentTypes.JSON)
        }
      }
    }
  }

  object FlattenedFilteringJacksonDataCodec {
    private val prettyPrinter = new DefaultPrettyPrinter()
  }
  private[naptime] class FlattenedFilteringJacksonDataCodec(fields: RequestFields)
    extends JacksonDataCodec {
    // Quick extra feature in order to differentiate between new and old engines.
    if (fields != AllFields && fields.hasField("_prettyPrint")) {
      setPrettyPrinter(FlattenedFilteringJacksonDataCodec.prettyPrinter)
    }

    override def writeObject(`object`: scala.Any, generator: JsonGenerator): Unit = {
      try {
        val callback = new FilteringJsonTraverseCallback(generator)
        Data.traverse(`object`, callback)
        generator.flush()
      } catch {
        case e: IOException => throw e
      } finally {
        try {
          generator.close()
        } catch {
          case e: IOException => // pass
        }
      }
    }

    override def objectToJsonGenerator(`object`: scala.Any, generator: JsonGenerator): Unit = {
      val callback = new FilteringJsonTraverseCallback(generator)
      Data.traverse(`object`, callback)
    }

    private[this] class FilteringJsonTraverseCallback(jsonGenerator: JsonGenerator)
      extends JsonTraverseCallback(jsonGenerator) {
      private[this] var inElements = false
      private[this] var levelsDeep = 0
      private[this] var inLinked = false
      private[this] var linkedResourceName: String = null
      private[this] var linkedFieldsFilter: Option[RequestFields] = None

      override def orderMap(map: DataMap): Iterable[Entry[String, AnyRef]] = {
        import scala.collection.JavaConverters._

        if (inElements && levelsDeep == 2) {
          val unfiltered = super.orderMap(map)
          // Use Scala's filtering, as by code inspection it is very efficient.
          unfiltered.asScala.filter { entry =>
            fields.hasField(entry.getKey)
          }.asJava
        } else if (inLinked && levelsDeep == 2) {
          val unfiltered = super.orderMap(map)
          unfiltered.asScala.filter { entry =>
            ResourceName.parse(entry.getKey).exists { resourceName =>
              fields.forResource(resourceName).isDefined
            }
          }.asJava
        } else if (inLinked && levelsDeep == 3 && linkedResourceName != null) {
          val unfiltered = super.orderMap(map)
          linkedFieldsFilter.map { fields =>
            // Use Scala's filtering, as by code inspection it is very efficient.
            unfiltered.asScala.filter { entry =>
              fields.hasField(entry.getKey)
            }.asJava
          }.getOrElse {
            unfiltered
          }
        } else {
          super.orderMap(map)
        }
      }

      override def startMap(map: DataMap): Unit = {
        levelsDeep += 1
        super.startMap(map)
      }

      override def endMap(): Unit = {
        levelsDeep -= 1
        if (levelsDeep == 0) {
          // Reset because we're top-level now.
          inElements = false
          inLinked = false
        } else if (levelsDeep == 1 && inLinked) {
          linkedResourceName = null
          linkedFieldsFilter = None
        }
        super.endMap()
      }

      override def key(key: String): Unit = {
        if (levelsDeep == 1) {
          inElements = "elements" == key
          inLinked = "linked" == key
        } else if (levelsDeep == 2 && inLinked) {
          linkedResourceName = key
          linkedFieldsFilter = ResourceName.parse(linkedResourceName).flatMap { resourceName =>
            fields.forResource(resourceName)
          }
        }
        super.key(key)
      }
    }
  }

}
