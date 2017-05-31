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

package org.coursera.naptime.ari.graphql.schema

import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.schema.Handler
import sangria.marshalling.FromInput
import sangria.schema.Argument
import sangria.schema.BigDecimalType
import sangria.schema.BooleanType
import sangria.schema.FloatType
import sangria.schema.InputType
import sangria.schema.IntType
import sangria.schema.ListInputType
import sangria.schema.LongType
import sangria.schema.OptionInputType
import sangria.schema.StringType

object NaptimeResourceUtils extends StrictLogging {
  private[this] val PAGINATION_ARGUMENT_NAMES: List[String] =
    NaptimePaginationField.paginationArguments.map(_.name)

  def generateHandlerArguments(
      handler: Handler,
      includePagination: Boolean = false): List[Argument[Any]] = {
    val baseParameters = handler.parameters
      .filterNot(parameter => PAGINATION_ARGUMENT_NAMES.contains(parameter.name))
      .map { parameter =>
        val tpe = parameter.`type`
        val inputType = scalaTypeToSangria(tpe)
        val fromInputType = scalaTypeToFromInput(tpe)
        val (optionalInputType, optionalFromInputType: FromInput[Any]) =
          (inputType, parameter.required) match {
            case (_: OptionInputType[Any], _) => (inputType, fromInputType)
            case (_, false) => (OptionInputType(inputType), FromInput.optionInput(fromInputType))
            case (_, true) => (inputType, fromInputType)
          }
        Argument(
          name = parameter.name,
          argumentType = optionalInputType)(optionalFromInputType, implicitly)
            .asInstanceOf[Argument[Any]]
      }.toList
    val paginationParameters = if (includePagination) {
      NaptimePaginationField.paginationArguments
    } else {
      List.empty
    }
    (baseParameters ++ paginationParameters)
      .groupBy(_.name)
      .map(_._2.head.asInstanceOf[Argument[Any]])
      .toList
  }

  private[this] def scalaTypeToSangria(typeName: String): InputType[Any] = {
    import sangria.marshalling.FromInput.seqInput
    import sangria.marshalling.FromInput.coercedScalaInput

    val listPattern = "(Set|List|Seq|immutable.Seq)\\[(.*)\\]".r
    val optionPattern = "(Option)\\[(.*)\\]".r
    // TODO(bryan): Fill in the missing types here
    typeName match {
      case listPattern(_, innerType) => ListInputType(scalaTypeToSangria(innerType))
      case optionPattern(_, innerType) => OptionInputType(scalaTypeToSangria(innerType))
      case "string" | "String" => StringType
      case "int" | "Int" => IntType
      case "long" | "Long" => LongType
      case "float" | "Float" => FloatType
      case "decimal" | "Decimal" => BigDecimalType
      case "boolean" | "Boolean" => BooleanType
      case _ => {
        logger.info(s"could not parse type from $typeName")
        StringType
      }
    }
  }

  private[this] def scalaTypeToFromInput(typeName: String): FromInput[Any] = {
    import sangria.marshalling.FromInput.seqInput
    import sangria.marshalling.FromInput.coercedScalaInput

    val listPattern = "(set|list|seq|immutable.Seq)\\[(.*)\\]".r

    // TODO(bryan): Fix all of this :)
    typeName.toLowerCase match {
      case listPattern(outerType, innerType) =>
        val listType = scalaTypeToFromInput(innerType)
        sangria.marshalling.FromInput.seqInput(listType).asInstanceOf[FromInput[Any]]
      case "string" | "int" | "long" | "float" | "decimal" | "boolean" =>
        sangria.marshalling.FromInput.coercedScalaInput.asInstanceOf[FromInput[Any]]
      case _ =>
        sangria.marshalling.FromInput.coercedScalaInput.asInstanceOf[FromInput[Any]]
    }
  }

  /**
    * Converts a resource name to a GraphQL compatible name. (i.e. 'courses.v1' to 'CoursesV1')
    *
    * @param resourceName Naptime resource name
    * @return GraphQL-safe resource name
    */
  def formatResourceName(resourceName: ResourceName): String = {
    s"${resourceName.topLevelName.capitalize}V${resourceName.version}"
  }
}
