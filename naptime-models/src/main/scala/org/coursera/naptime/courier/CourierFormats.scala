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

package org.coursera.naptime.courier

import java.io.IOException

import com.linkedin.data.ByteString
import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.Null
import com.linkedin.data.message.Message
import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.validation.CoercionMode
import com.linkedin.data.schema.validation.RequiredMode
import com.linkedin.data.schema.validation.ValidateDataAgainstSchema
import com.linkedin.data.schema.validation.ValidationOptions
import com.linkedin.data.schema.validator.DataSchemaAnnotationValidator
import com.linkedin.data.template.DataTemplate
import com.linkedin.data.template.DataTemplateUtil
import com.linkedin.data.template.RecordTemplate
import com.linkedin.data.template.TemplateOutputCastException
import com.linkedin.data.template.UnionTemplate
import com.typesafe.scalalogging.StrictLogging
import org.coursera.common.jsonformat.JsonFormats
import org.coursera.common.stringkey.StringKey
import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.courier.templates.DataValidationException
import org.coursera.courier.templates.ScalaEnumTemplate
import org.coursera.courier.templates.ScalaEnumTemplateSymbol
import org.coursera.pegasus.TypedDefinitionDataCoercer
import play.api.data.validation.ValidationError
import play.api.libs.json.Format
import play.api.libs.json.IdxPathNode
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsError
import play.api.libs.json.JsError
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.KeyPathNode
import play.api.libs.json.OFormat

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * Provides utilities for converting between courier and Play JSON.
 */
object CourierFormats extends StrictLogging {

  /**
   * WARN: this performs a full copy of data between Play JSON and Pegasus raw data types and is
   * intended for temporary use only during the transition to Naptime v2.
   *
   * Returns a formatter for the given Courier record type that converts it to and from
   * Play JSON classes.
   *
   * Data is validated against it's Pegasus schema when read.
   */
  def recordTemplateFormats[T <: RecordTemplate](implicit tag: ClassTag[T]): OFormat[T] = {
    dataTemplateFormats(tag.runtimeClass.asInstanceOf[Class[T]])
  }

  def enumerationFormat[T <: ScalaEnumTemplateSymbol](
      enumeration: ScalaEnumTemplate[T]): Format[T] =
    new Format[T] {

    def reads(json: JsValue) = {
      val jsResult = for {
        name <- json.validate[String]
        value <- Try(enumeration.withName(name)) match {
          case Success(t) => JsSuccess(t)
          case Failure(e) => JsError(e.toString)
        }
      } yield value

      jsResult.orElse {
        JsError(s"Unrecognized enumeration ($enumeration) value: $json")
      }
    }

    def writes(value: T) = JsString(value.toString)
  }

  def enumerationStringKeyFormat[T <: ScalaEnumTemplateSymbol](enumeration: ScalaEnumTemplate[T]):
    StringKeyFormat[T] = {
    StringKeyFormat[T](
      k => Try(enumeration.withName(k.key)).toOption,
      v => StringKey(v.toString))
  }

  /**
   * WARN: this performs a full copy of data between Play JSON and Pegasus raw data types and is
   * intended for temporary use only during the transition to Naptime v2.
   *
   * Returns a formatter for the given Courier record type that converts it to and from
   * Play JSON classes.
   *
   * Data is validated against it's Pegasus schema when read.
   */
  def unionTemplateFormats[T <: UnionTemplate](implicit tag: ClassTag[T]): OFormat[T] = {
    val clazz = tag.runtimeClass.asInstanceOf[Class[T]]
    // If available, use the schema of the typeref that declares the union, since it contains
    // typed definition mapping properties that are required to correctly serialize typed
    // definitions.
    val schema = CourierSerializer.getDeclaringTyperefSchema(clazz)
      .getOrElse(CourierSerializer.getSchema(clazz))

    dataTemplateFormats(clazz, schema)
  }

  private[this] val recordValidationOptions = new ValidationOptions()

  private[this] def toJsError(validationMessages: Seq[Message]): JsError = {
    JsError(validationMessages.map { message =>
      val path = JsPath(message.getPath.map {
        case idx: java.lang.Integer => IdxPathNode(idx)
        case name: String => KeyPathNode(name)
      }.toList)
      path -> Seq(ValidationError(message.toString))
    })
  }

  private[this] def dataTemplateFormats[T <: DataTemplate[_ <: AnyRef]](
      clazz: Class[T]): OFormat[T] = {
    dataTemplateFormats(clazz, CourierSerializer.getSchema(clazz))
  }

  private[this] def dataTemplateFormats[T <: DataTemplate[_ <: AnyRef]](
      clazz: Class[T], schema: DataSchema): OFormat[T] = {
    new OFormat[T] {
      val coercer = new TypedDefinitionDataCoercer(schema)
      val builder = CourierSerializer.builder(clazz)
      val annotationValidator = new DataSchemaAnnotationValidator(schema)
      override def reads(json: JsValue): JsResult[T] = {
        json match {
          case jsObject: JsObject =>
            val dataMap = coercer.convertTypedDefinitionToUnion(objToDataMap(jsObject))
            try {
              builder.validateAndBuild(dataMap) match {
                case Left(validationResult) => toJsError(validationResult.getMessages.asScala.toSeq)
                case Right(template) => JsSuccess(template)
              }
            } catch {
              case castException: TemplateOutputCastException =>
                JsError(s"Pegasus template cast error: ${castException.getMessage}")
            }
          case unknown: Any => JsError(s"Unsupported JsValue type: ${unknown.getClass}")
        }
      }

      override def writes(o: T): JsObject = {
        o.data() match {
          case dataMap: DataMap =>
            dataMapToObj(coercer.convertUnionToTypedDefinition(dataMap))
          case unknown: Any => throw new IOException(s"Unsupported data type: ${unknown.getClass}")
        }
      }
    }
  }

  def objToDataMap(obj: JsObject): DataMap = {
    val dataMap = new DataMap(obj.fields.size)
    obj.fields.foreach { case (key, value) =>
      dataMap.put(key, jsValueToData(value))
    }
    dataMap
  }

  private[this] def arrayToDataList(array: JsArray): DataList = {
    val dataList = new DataList(array.value.size)
    array.value.foreach { item =>
      dataList.add(jsValueToData(item))
    }
    dataList
  }

  private[this] def jsValueToData(obj: JsValue): AnyRef = {
    obj match {
      case array: JsArray => arrayToDataList(array)
      case obj: JsObject => objToDataMap(obj)
      case number: JsNumber => bigDecimalToNumber(number.value)
      case boolean: JsBoolean => Boolean.box(boolean.value)
      case string: JsString => string.value
      case JsNull => Null.getInstance()
      case unknown: JsValue =>
        throw new IOException(s"Unsupported json type: ${unknown.getClass}")
    }
  }

  /**
   * Convert a BigDecimal (used by Play JSON) to a Number (used by Pegasus).
   *
   * Attempts to convert using the same rules as Pegasus's JacksonDataCodec:
   *
   * https://github.com/linkedin/rest.li/blob/
   *   master/data/src/main/java/com/linkedin/data/codec/JacksonDataCodec.java#L767
   *
   * In particular, decimal values that cannot be represented as Number (int, long, double, float)
   * are considered invalid.
   *
   */
  def bigDecimalToNumber(value: BigDecimal): Number = {
    if (value.isValidInt) {
      value.toIntExact
    } else if (value.isValidLong) {
      value.toLongExact
    } else if (value.isExactFloat) {
      value.toFloat
    // per http://www.scala-lang.org/api/2.11.5/index.html#scala.math.BigDecimal
    // this is the most lenient check we can make to validate that the conversion to
    // number will not lose information.
    } else if (value.isDecimalDouble) {
      value.toDouble
    } else {
      throw new IOException(
        s"Unable to convert to numeric value without loss of information: $value")
    }
  }

  def dataMapToObj(dataMap: DataMap): JsObject = {
    JsObject(dataMap.entrySet().asScala.map { entry =>
      entry.getKey -> dataToJsValue(entry.getValue)
    }.toSeq)
  }

  private[this] def dataListToArray(dataList: DataList): JsArray = {
    JsArray(dataList.iterator().asScala.map { item =>
      dataToJsValue(item)
    }.toSeq)
  }

  private[this] def dataToJsValue(data: AnyRef): JsValue = {
    data match {
      case dataMap: DataMap => dataMapToObj(dataMap)
      case dataList: DataList => dataListToArray(dataList)
      case integer: java.lang.Integer => JsNumber(integer.toInt)
      case long: java.lang.Long => JsNumber(long.toLong)
      case float: java.lang.Float => JsNumber(float.toDouble)
      case double: java.lang.Double => JsNumber(double.toDouble)
      case boolean: java.lang.Boolean => JsBoolean(boolean.booleanValue())
      case string: String => JsString(string)
      case bytes: ByteString => JsString(bytes.asAvroString())
      case _: Null => JsNull
      case unknown: AnyRef =>
        throw new IOException(s"Unsupported json type: ${unknown.getClass}")
    }
  }

  /**
   * Returns a string key format for a given Courier record type.
   */
  def recordTemplateStringKeyFormat[T <: RecordTemplate](
      implicit tag: ClassTag[T]): StringKeyFormat[T] = {
    val clazz = tag.runtimeClass.asInstanceOf[Class[T]]
    CourierSerializer.getSchema(clazz) match {
      case recordSchema: RecordDataSchema => recordTemplateStringKeyFormat(clazz, recordSchema)
      case unknown: DataSchema =>
        throw new IllegalArgumentException(
          s"Expected RecordDataSchema but found: ${unknown.getClass}")
    }
  }

  private[this] def recordTemplateStringKeyFormat[T <: RecordTemplate](
      clazz: Class[T], schema: RecordDataSchema): StringKeyFormat[T] = {
    new StringKeyFormat[T] {
      val stringKeyCodec = new StringKeyCodec(schema)

      override def reads(key: StringKey): Option[T] = {
        val string = key.key
        val dataMap = stringKeyCodec.bytesToMap(string.getBytes(StringKeyCodec.charset))
        validateAndFixUp(dataMap, schema)
        try {
          dataMap.setReadOnly()
          Some(DataTemplateUtil.wrap(dataMap, clazz))
        } catch {
          case castException: TemplateOutputCastException =>
            logger.debug(s"Template cast failed for stringKey: $string", castException)
            None
          case validationException: DataValidationException =>
            logger.debug(s"Validation failed for stringKey: $string", validationException)
            None
        }
      }

      override def writes(t: T): StringKey = {
        val string = new String(stringKeyCodec.mapToBytes(t.data()), StringKeyCodec.charset)
        StringKey(string)
      }
    }
  }

  /**
   * Returns a string key format for a given Courier array type.
   */
  def arrayTemplateStringKeyFormat[T <: DataTemplate[DataList]](
      implicit tag: ClassTag[T]): StringKeyFormat[T] = {
    val clazz = tag.runtimeClass.asInstanceOf[Class[T]]
    CourierSerializer.getSchema(clazz) match {
      case arraySchema: ArrayDataSchema => arrayTemplateStringKeyFormat(clazz, arraySchema)
      case unknown: DataSchema =>
        throw new IllegalArgumentException(
          s"Expected ArrayDataSchema but found: ${unknown.getClass}")
    }
  }

  private[this] def arrayTemplateStringKeyFormat[T <: DataTemplate[DataList]](
      clazz: Class[T], schema: ArrayDataSchema): StringKeyFormat[T] = {
    new StringKeyFormat[T] {
      val stringKeyCodec = new StringKeyCodec(schema)

      override def reads(key: StringKey): Option[T] = {
        val string = key.key
        val dataList = stringKeyCodec.bytesToList(string.getBytes(StringKeyCodec.charset))
        validateAndFixUp(dataList, schema)
        try {
          dataList.setReadOnly()
          Some(DataTemplateUtil.wrap(dataList, clazz.getConstructor(classOf[DataList])))
        } catch {
          case castException: TemplateOutputCastException =>
            logger.debug(s"Template cast failed for stringKey: $string", castException)
            None
          case validationException: DataValidationException =>
            logger.debug(s"Validation failed for stringKey: $string", validationException)
            None
        }
      }

      override def writes(t: T): StringKey = {
        val string = new String(stringKeyCodec.listToBytes(t.data()), StringKeyCodec.charset)
        StringKey(string)
      }
    }
  }

  private[this] val validationOptions = new ValidationOptions(
    RequiredMode.FIXUP_ABSENT_WITH_DEFAULT,
    CoercionMode.STRING_TO_PRIMITIVE)

  private[this] def validateAndFixUp(data: Any, schema: DataSchema): Unit = {
    val result = ValidateDataAgainstSchema.validate(data, schema, validationOptions)
    if (!result.isValid) {
      throw new DataValidationException(result)
    }
  }
}
