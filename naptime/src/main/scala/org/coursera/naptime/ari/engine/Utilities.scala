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

package org.coursera.naptime.ari.engine

import com.linkedin.data.DataMap
import com.linkedin.data.element.DataElement
import com.linkedin.data.it.Builder
import com.linkedin.data.it.IterationOrder
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import scala.collection.JavaConverters._


object Utilities extends StrictLogging {
  private val TYPED_DEFINITION_KEY = "typedDefinition"
  private def getTypedDefinitionMappings(elements: Iterator[DataElement]): Map[String, String] = {
    val typedDefinitionMappings: List[Map[String, String]] = elements.map { element =>
      Option(element.getSchema.getProperties.get(TYPED_DEFINITION_KEY)).collect {
        case definitions: java.util.Map[String@unchecked, String@unchecked] =>
          definitions.asScala.toMap // toMap as the .asScala map is default mutable.
      }.getOrElse(Map.empty[String, String])
    }.toList

    val typedDefinitionsFlattened = typedDefinitionMappings.flatten
    typedDefinitionsFlattened.toMap
  }

  /**
   * Iterates through a data map and returns the value(s) stored at a particular path in the data map,
   * respecting `typedDefinition` annotations.
   *
   * We only iterate on value types to avoid needing to think about container and value types.
   *
   * This is a 1 to many operation: a single path may return multiple values.
   * For example, the path is for an array of records: suppose
   * the DataMap is {"courses": [{"id": "courseId1", "name": "my fav"}, {"id": "courseId2", "name": "not my fav"}]},
   * then the path `courses/id` should return `["courseId1", "courseId2"]`.
   *
   * Another common case is if the path is for an array: suppose the DataMap is
   * {"courseIds": ["courseId1", "courseId2"]}, then the path `courseIds` should return
   * ["courseId1", "courseId2"]
   *
   * @param element top-level element for which we are retrieving the values.
   * @param schema data schema defining the fields on the top-level element
   * @param path list of strings defining the path to one or many target values.
   */
  private[naptime] def getValuesAtPath(
      element: DataMap,
      schema: RecordDataSchema,
      path: Seq[String]): List[String] = {

    def getIterator() = {
      val dataIterator = Builder.create(element, schema, IterationOrder.PRE_ORDER).dataIterator()

      Iterator
      .continually(dataIterator.next)
      .takeWhile(_ != null)
    }
    val typedDefinitionMappings = getTypedDefinitionMappings(getIterator())

    logger.trace(s"getValuesAtPath for path: $path with typedDefinitionMappings: $typedDefinitionMappings")
    getIterator()
      // Filter out non-value types, so the below logic can focus on computing whether a value
      // should be included because it satisfies the path.
      .filterNot(dataElement => {
        val filteredSchemaTypes = Set(DataSchema.Type.ARRAY, DataSchema.Type.RECORD, DataSchema.Type.UNION)
        filteredSchemaTypes.contains(dataElement.getSchema.getType)
      })
      .filter(dataElement => {
        logger.trace(s"Checking if value: `${dataElement.getValue.toString}` " +
          s"(${dataElement.getSchema.getType}) at " +
          s"${dataElement.pathAsString()} should be included...")

        // Substitute with typed definition names.
        // e.g `org.coursera.naptime.ari.graphql.models.OldPlatformData -> old` allows
        // for users to substitute the fully qualified path for a readable and stable path.
        val withTypedDefinitionNameReplacementPath = dataElement.path()
          .map { p =>
            val path = p.toString
            val replacementPath = typedDefinitionMappings.get(path)
            replacementPath.getOrElse(path)
          }

        // Remove array indices from path to allow 1 path to many value mappings.
        // For example, element paths `/courses/0/instructorIds/0`, `/courses/0/instructorIds/1`,
        // `/courses/1/instructorIds/0`, ... etc. should match user supplied path `/courses/instructorIds`
        val withArrayIndicesRemoved = withTypedDefinitionNameReplacementPath
          .filterNot(_.forall(_.isDigit))
          .toList

        val shouldBeIncluded = withArrayIndicesRemoved == path ||
          // Special case: Because we deal with only value types, for unions, the values have a
          // path that contains the type information of the union [e.g `path = /myUnionedId`,
          // and `myUnionedId = union[int, string]`, then
          // the data for the provided path is under `/myUnionedId/string` or `/myUnionedId/int`.
          (dataElement.getParent.getSchema.getType == DataSchema.Type.UNION &&
            withArrayIndicesRemoved.dropRight(1) == path)

        logger.trace(s"Data element to be included? $shouldBeIncluded " +
          s"[checked path: $withArrayIndicesRemoved not equal to $path]")
        shouldBeIncluded
      })
      .map(_.getValue.toString)
      .toList
      .distinct
  }

  def stringifyArg(value: JsValue): String = {
    value match {
      case JsArray(arrayElements) =>
        arrayElements.map(stringifyArg).filterNot(_.isEmpty).mkString(",")
      case stringValue: JsString =>
        stringValue.as[String]
      case number: JsNumber =>
        number.toString
      case boolean: JsBoolean =>
        boolean.toString
      case jsObject: JsObject =>
        Json.stringify(jsObject)
      case JsNull =>
        ""
    }
  }

  def jsValueIsEmpty(value: JsValue): Boolean = {
    value match {
      case JsArray(arrayElements) =>
        arrayElements.isEmpty || arrayElements.forall(jsValueIsEmpty)
      case stringValue: JsString =>
        stringValue.value.isEmpty
      case _: JsNumber =>
        false
      case _: JsBoolean =>
        false
      case _: JsObject =>
        false
      case JsNull =>
        true
    }
  }
}
