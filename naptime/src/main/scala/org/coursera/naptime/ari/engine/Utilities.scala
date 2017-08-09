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

  // TODO(bryan): Dedupe this from NaptimeUnionField
  val TYPED_DEFINITION_KEY = "typedDefinition"
  def getTypedDefinition(dataSchema: DataSchema): Option[Map[String, String]] = {
    Option(dataSchema).flatMap(schema => Option(schema.getProperties.get(TYPED_DEFINITION_KEY)).collect {
      case definitions: java.util.Map[String @unchecked, String @unchecked] => definitions.asScala.toMap
    })
  }

  private[this] def replaceTypedDefinitionNames(dataElement: DataElement): List[String] = {
    val thisName = (for {
      parent <- Option(dataElement.getParent)
      schema <- Option(parent.getSchema)
      _ <- getTypedDefinition(schema)
        if dataElement.getName.toString == "definition"
      typeName <- Option(dataElement.getParent.getValue).flatMap {
        case dataMap: DataMap => Option(dataMap.getString("typeName"))
        case _ => None
      }
    } yield {
      typeName
    }).getOrElse(dataElement.getName.toString)
    val parentNames = Option(dataElement.getParent).map(replaceTypedDefinitionNames)
    (parentNames.getOrElse(List.empty) :+ thisName).filterNot(_.isEmpty)
  }

  /**
    * Iterates through a data map and returns the value stored at a particular path in the data map,
    * or None if the path isn't found (or the data is null)
    *
    * @param element top-level element to be updated
    * @param schema data schema defining the fields on the element
    * @param path list of strings defining the path to the target element
    */
  private[naptime] def getValueAtPath(
      element: DataMap,
      schema: RecordDataSchema,
      path: Seq[String]): Option[Object] = {
    val it = Builder.create(element, schema, IterationOrder.PRE_ORDER).dataIterator()
    Iterator
      .continually(it.next)
      .takeWhile(_ != null)
      .find(dataElement => replaceTypedDefinitionNames(dataElement) == path.dropRight(1))
      .map(_.getValue.asInstanceOf[DataMap].get(path.last))
      .flatMap(Option(_))
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
