package org.coursera.naptime.ari.graphql.marshaller

import com.linkedin.data.DataMap
import org.coursera.naptime.actions.NaptimeSerializer
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsError
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import sangria.marshalling.ArrayMapBuilder
import sangria.marshalling.FromInput
import sangria.marshalling.InputParser
import sangria.marshalling.InputParsingError
import sangria.marshalling.InputUnmarshaller
import sangria.marshalling.ResultMarshaller
import sangria.marshalling.ResultMarshallerForType
import sangria.marshalling.ScalarValueInfo
import sangria.marshalling.ToInput

import scala.util.Try

/**
 * Modified from https://github.com/sangria-graphql/sangria-play-json,
 * with added support for DataMaps
 */
object NaptimeMarshaller extends PlayJsonSupportLowPrioImplicits {
  implicit object PlayJsonResultMarshaller extends ResultMarshaller {
    type Node = JsValue
    type MapBuilder = ArrayMapBuilder[Node]

    def emptyMapNode(keys: Seq[String]) = new ArrayMapBuilder[Node](keys)
    def addMapNodeElem(builder: MapBuilder, key: String, value: Node, optional: Boolean) =
      builder.add(key, value)

    def mapNode(builder: MapBuilder) = JsObject(builder.toSeq)
    def mapNode(keyValues: Seq[(String, JsValue)]) = JsObject(keyValues)

    def arrayNode(values: Vector[JsValue]) = JsArray(values)

    def optionalArrayNodeValue(value: Option[JsValue]) = value match {
      case Some(v) => v
      case None    => nullNode
    }

    def scalarNode(value: Any, typeName: String, info: Set[ScalarValueInfo]) =
      value match {
        case v: String     => JsString(v)
        case v: Boolean    => JsBoolean(v)
        case v: Int        => JsNumber(v)
        case v: Long       => JsNumber(v)
        case v: Double     => JsNumber(v)
        case v: BigInt     => JsNumber(BigDecimal(v))
        case v: BigDecimal => JsNumber(v)
        case v: DataMap    => NaptimeSerializer.PlayJson.deserialize(v)
        case v =>
          throw new IllegalArgumentException("Unsupported scalar value: " + v)
      }

    def enumNode(value: String, typeName: String) = JsString(value)

    def nullNode = JsNull

    def renderCompact(node: JsValue) = Json.stringify(node)
    def renderPretty(node: JsValue) = Json.prettyPrint(node)
  }

  implicit object PlayJsonMarshallerForType extends ResultMarshallerForType[JsValue] {
    val marshaller = PlayJsonResultMarshaller
  }

  implicit object PlayJsonInputUnmarshaller extends InputUnmarshaller[JsValue] {
    def getRootMapValue(node: JsValue, key: String) =
      node.asInstanceOf[JsObject].value get key

    def isListNode(node: JsValue) = node.isInstanceOf[JsArray]
    def getListValue(node: JsValue) = node.asInstanceOf[JsArray].value

    def isMapNode(node: JsValue) = node.isInstanceOf[JsObject]
    def getMapValue(node: JsValue, key: String) =
      node.asInstanceOf[JsObject].value get key
    def getMapKeys(node: JsValue) = node.asInstanceOf[JsObject].keys

    def isDefined(node: JsValue) = node != JsNull
    def getScalarValue(node: JsValue) = node match {
      case JsBoolean(b) => b
      case JsNumber(d)  => d.toBigIntExact getOrElse d
      case JsString(s)  => s
      case _            => throw new IllegalStateException(s"$node is not a scalar value")
    }

    def getScalaScalarValue(node: JsValue) = getScalarValue(node)

    def isEnumNode(node: JsValue) = node.isInstanceOf[JsString]

    def isScalarNode(node: JsValue) = node match {
      case _: JsBoolean | _: JsNumber | _: JsString => true
      case _                                        => false
    }

    def isVariableNode(node: JsValue) = false
    def getVariableName(node: JsValue) =
      throw new IllegalArgumentException("variables are not supported")

    def render(node: JsValue) = Json.stringify(node)
  }

  private object PlayJsonToInput extends ToInput[JsValue, JsValue] {
    def toInput(value: JsValue) = (value, PlayJsonInputUnmarshaller)
  }

  implicit def playJsonToInput[T <: JsValue]: ToInput[T, JsValue] =
    PlayJsonToInput.asInstanceOf[ToInput[T, JsValue]]

  implicit def playJsonWriterToInput[T: Writes]: ToInput[T, JsValue] =
    new ToInput[T, JsValue] {
      def toInput(value: T) =
        implicitly[Writes[T]].writes(value) → PlayJsonInputUnmarshaller
    }

  private object PlayJsonFromInput extends FromInput[JsValue] {
    val marshaller = PlayJsonResultMarshaller
    def fromResult(node: marshaller.Node) = node
  }

  implicit def playJsonFromInput[T <: JsValue]: FromInput[T] =
    PlayJsonFromInput.asInstanceOf[FromInput[T]]

  implicit def playJsonReaderFromInput[T: Reads]: FromInput[T] =
    new FromInput[T] {
      val marshaller = PlayJsonResultMarshaller
      def fromResult(node: marshaller.Node) =
        implicitly[Reads[T]].reads(node) match {
          case JsSuccess(v, _) => v
          case JsError(errors) =>
            val formattedErrors = errors.toVector.flatMap {
              case (JsPath(nodes), es) =>
                es.map(e => s"At path '${nodes.mkString(".")}': ${e.message}")
            }

            throw InputParsingError(formattedErrors)
        }
    }

  implicit object PlayJsonInputParser extends InputParser[JsValue] {
    def parse(str: String) = Try(Json.parse(str))
  }
}

trait PlayJsonSupportLowPrioImplicits {
  implicit val PlayJsonInputUnmarshallerJObject =
    NaptimeMarshaller.PlayJsonInputUnmarshaller
      .asInstanceOf[InputUnmarshaller[JsObject]]
}
