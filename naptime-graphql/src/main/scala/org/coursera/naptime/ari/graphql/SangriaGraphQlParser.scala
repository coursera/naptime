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

package org.coursera.naptime.ari.graphql

import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.TopLevelRequest
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader
import sangria.ast.BigDecimalValue
import sangria.ast.Field
import sangria.ast.BigIntValue
import sangria.ast.BooleanValue
import sangria.ast.EnumValue
import sangria.ast.FloatValue
import sangria.ast.IntValue
import sangria.ast.ListValue
import sangria.ast.NullValue
import sangria.ast.ObjectField
import sangria.ast.ObjectValue
import sangria.ast.StringValue
import sangria.ast.Value
import sangria.ast.VariableValue
import sangria.parser.QueryParser

/**
  * The SangriaGraphQlParser uses the [Sangria library](https://github.com/sangria-graphql/sangria)
  * to parse a GraphQL input into a Naptime ARI [[Request]] for further processing.
  */
object SangriaGraphQlParser extends GraphQlParser {

  /**
    * For a given request, consisting of a GraphQL query (represented as a string) and a
    * [[RequestHeader]], parse the input into a Naptime [[Request]] to be passed to the
    * [[org.coursera.naptime.ari.EngineApi]]
    *
    * @param request A string representation of a GraphQL query / mutation
    * @param requestHeader RequestHeader from the incoming request, which gets propagated down to
    *                      the engine. May be used for authentication at a future time.
    * @return a [[Request]] if the parsing of the request was successful, otherwise None
    */
  def parse(request: String, requestHeader: RequestHeader): Option[Request] = {
    val parsedDocumentOption = QueryParser.parse(request).toOption
    // TODO(bryan): Handle error cases here
    val topLevelRequests = for {
      parsedDocument <- parsedDocumentOption.toList
      operation <- parsedDocument.operations
      operationName = operation._1.getOrElse("")
      operationData = operation._2
      selection <- operationData.selections
      field <- {
        (selection match {
          case field: Field => Some(field)
          case _ => None
        }).toList
      }
      resource <- fieldNameToNaptimeResource(field.name).toList
      fields = parseField(field)
      fieldWithoutResourceName <- fields.selections.headOption
    } yield {
      TopLevelRequest(resource, fieldWithoutResourceName, fields.alias)
    }
    Some(Request(requestHeader, topLevelRequests))
  }

  private[this] def parseField(field: Field): RequestField = {
    val subfields = field.selections.flatMap {
      case subfield: Field => Some(parseField(subfield))
      case _ => None
    }
    RequestField(
      field.name,
      field.alias,
      field.arguments.map(argument => (argument.name, parseValue(argument.value))).toSet,
      subfields)
  }

  private[this] def parseValue(sangriaValue: Value): JsValue = {
    sangriaValue match {
      case IntValue(value, _, _) => JsNumber(value)
      case BigIntValue(value, _, _) => JsNumber(BigDecimal(value))
      case FloatValue(value, _, _) => JsNumber(value)
      case BigDecimalValue(value, _, _) => JsNumber(value)
      case StringValue(value, _, _) => JsString(value)
      case BooleanValue(value, _, _) => JsBoolean(value)
      case EnumValue(value, _, _) => JsString(value)
      case ListValue(value, _, _) => JsArray(value.map(parseValue))
      case VariableValue(value, _, _) => JsString(value)
      case NullValue(_, _) => JsNull
      case ObjectValue(fields, _, _) => JsObject(fields.map { case ObjectField(name, value, _, _) =>
        name -> parseValue(value)
      })
      case a: Value => JsNull
    }
  }

  val TOP_LEVEL_RESOURCE_REGEX = "([\\w\\d]+)V(\\d)Resource".r

  /**
    * Converts a GraphQL top-level field name to a standard Naptime [[ResourceName]].
    * For example, CoursesV1 gets parsed as ResourceName("Courses", 1).
    * Invalid field names will return a None.
    * @param fieldName field name string, in the format CoursesV1
    * @return parsed [[ResourceName]] if successful, None if unsuccessful.
    */
  def fieldNameToNaptimeResource(fieldName: String): Option[ResourceName] = {
    // TODO(bryan): provide more information on a parse error than simply returning a None
    fieldName match {
      case TOP_LEVEL_RESOURCE_REGEX(resourceName, version) =>
        try {
          val lowercaseResourceName = Character.toLowerCase(resourceName.charAt(0)) + resourceName.substring(1)
          Some(ResourceName(lowercaseResourceName, version.toInt))
        } catch {
          case e: NumberFormatException => None
        }
      case _ => None
    }
  }

}
