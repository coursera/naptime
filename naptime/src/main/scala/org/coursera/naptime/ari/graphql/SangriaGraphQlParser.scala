package org.coursera.naptime.ari.graphql

import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.TopLevelRequest
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader
import sangria.ast.Argument
import sangria.ast.Field
import sangria.ast.BigIntValue
import sangria.ast.IntValue
import sangria.ast.Value
import sangria.parser.QueryParser

object SangriaGraphQlParser extends GraphQlParser {

  def parse(request: String, requestHeader: RequestHeader): Option[Request] = {
    val parsedDocumentOption = QueryParser.parse(request).toOption
    // TODO(bryan): Handle error cases here
    val topLevelRequests = for {
      parsedDocument <- parsedDocumentOption.toList
      operation <- parsedDocument.operations
      operationName <- operation._1.toList
      operationData = operation._2
      selection <- operationData.selections
      field <- {
        (selection match {
          case field: Field => Some(field)
          case _ => None
        }).toList
      }
      resource <- fieldNameToNaptimeResource(field.name).toList
    } yield {
      TopLevelRequest(resource, parseField(field))
    }
    Some(Request(requestHeader, topLevelRequests))
  }

  def parseField(field: Field): RequestField = {
    val subfields = field.selections.flatMap {
      case subfield: Field => Some(parseField(subfield))
      case _ => None
    }
    RequestField(
      field.name,
      field.alias,
      field.arguments.map(parseArgument).toSet,
      subfields)
  }

  def parseArgument(argument: Argument): (String, JsValue) = {
    // TODO(bryan): Handle other value types here
    val parsedValue: JsValue = argument.value match {
      case IntValue(value, _, _) => JsNumber(value)
      case BigIntValue(value, _, _) => JsNumber(BigDecimal(value))
      case a: Value => JsNull
    }
    (argument.name, parsedValue)
  }

  val TOP_LEVEL_RESOURCE_REGEX = "([\\w\\d]+)V(\\d)".r

  def fieldNameToNaptimeResource(fieldName: String): Option[ResourceName] = {
    fieldName match {
      case TOP_LEVEL_RESOURCE_REGEX(resourceName, version) =>
        try {
          Some(ResourceName(resourceName, version.toInt))
        } catch {
          case e: NumberFormatException => None
        }
      case _ => None
    }
  }

}
