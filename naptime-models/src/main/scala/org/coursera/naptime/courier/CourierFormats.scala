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
import java.time.Clock

import com.linkedin.data.ByteString
import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.Null
import com.linkedin.data.message.Message
import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.BooleanDataSchema
import com.linkedin.data.schema.BytesDataSchema
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.DoubleDataSchema
import com.linkedin.data.schema.EnumDataSchema
import com.linkedin.data.schema.FloatDataSchema
import com.linkedin.data.schema.IntegerDataSchema
import com.linkedin.data.schema.LongDataSchema
import com.linkedin.data.schema.MapDataSchema
import com.linkedin.data.schema.NamedDataSchema
import com.linkedin.data.schema.NullDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.StringDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import com.linkedin.data.schema.validation.CoercionMode
import com.linkedin.data.schema.validation.RequiredMode
import com.linkedin.data.schema.validation.ValidateDataAgainstSchema
import com.linkedin.data.schema.validation.ValidationOptions
import com.linkedin.data.template.DataTemplate
import com.linkedin.data.template.DataTemplateUtil
import com.linkedin.data.template.RecordTemplate
import com.linkedin.data.template.TemplateOutputCastException
import com.linkedin.data.template.UnionTemplate
import com.typesafe.scalalogging.StrictLogging
import org.coursera.common.stringkey.StringKey
import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.courier.templates.DataValidationException
import org.coursera.courier.templates.ScalaArrayTemplate
import org.coursera.courier.templates.ScalaEnumTemplate
import org.coursera.courier.templates.ScalaEnumTemplateSymbol
import org.coursera.courier.templates.ScalaRecordTemplate
import org.coursera.courier.templates.ScalaTemplate
import org.coursera.courier.templates.ScalaUnionTemplate
import org.coursera.naptime.courier.CourierUtils._
import org.coursera.naptime.courier.Exceptions._
import play.api.libs.json.JsonValidationError
import play.api.libs.json.Format
import play.api.libs.json.IdxPathNode
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
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
import scala.collection.Seq
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * Provides utilities for converting between courier and Play JSON.
 */
object CourierFormats extends StrictLogging {

  val codecAnnotation = "codec"
  val stringKeyName = "StringKey"

  val passthroughAnnotation = "passthroughExempt"

  private[this] val emptyPath = SchemaPath()

  private[this] case class SchemaPath(nodes: List[NamedDataSchema] = List.empty) {
    def push(schema: NamedDataSchema): SchemaPath = {
      SchemaPath(schema +: nodes)
    }
    def last: Option[NamedDataSchema] = {
      nodes.headOption
    }
  }

  def recordTemplateFormats[T <: RecordTemplate](implicit tag: ClassTag[T]): OFormat[T] = {
    new OFormat[T] {
      private[this] val clazz = tag.runtimeClass.asInstanceOf[Class[T]]
      private[this] val builder = CourierSerializer.builder(clazz)
      private[this] val schema = CourierSerializer.getSchema(clazz).asInstanceOf[RecordDataSchema]

      override def reads(jsValue: JsValue): JsResult[T] = {
        jsValue match {
          case jsObject: JsObject =>
            try {
              val dataMap = jsObjectToRecord(emptyPath, jsObject, schema)
              builder.validateAndBuild(dataMap) match {
                case Left(validationResult) =>
                  toJsError(validationResult.getMessages.asScala.toSeq)
                case Right(template) =>
                  materialize(template)
                  JsSuccess(template)
              }
            } catch {
              case readException: ReadException =>
                JsError(readException.message)
              case castException: TemplateOutputCastException =>
                JsError(s"Pegasus template cast error: ${castException.getMessage}")
            }
          case unknown: Any => JsError(s"Unsupported JsValue type: ${unknown.getClass}")
        }
      }

      override def writes(record: T): JsObject = {
        recordToJsObject(emptyPath, record.data, schema)
      }
    }
  }

  def unionTemplateFormats[T <: UnionTemplate](implicit tag: ClassTag[T]): OFormat[T] = {
    new OFormat[T] {
      private[this] val clazz = tag.runtimeClass.asInstanceOf[Class[T]]
      private[this] val builder = CourierSerializer.builder(clazz)
      private[this] val schema = CourierSerializer
        .getDeclaringTyperefSchema(clazz)
        .getOrElse(CourierSerializer.getSchema(clazz))

      override def reads(jsValue: JsValue): JsResult[T] = {
        jsValue match {
          case jsObject: JsObject =>
            try {
              val dataMap = schema match {
                case typerefSchema: TyperefDataSchema =>
                  jsValueToTyperef(emptyPath, jsValue, typerefSchema).asInstanceOf[DataMap]
                case unionSchema: UnionDataSchema =>
                  jsObjectToUnion(emptyPath, jsObject, unionSchema)
              }
              builder.validateAndBuild(dataMap) match {
                case Left(validationResult) =>
                  toJsError(validationResult.getMessages.asScala.toSeq)
                case Right(template) =>
                  materialize(template)
                  JsSuccess(template)
              }
            } catch {
              case readException: ReadException =>
                JsError(readException.message)
              case castException: TemplateOutputCastException =>
                JsError(s"Pegasus template cast error: ${castException.getMessage}")
            }
          case unknown: Any => JsError(s"Unsupported JsValue type: ${unknown.getClass}")
        }
      }

      override def writes(union: T): JsObject = {
        union.data match {
          case dataMap: DataMap =>
            schema match {
              case typerefSchema: TyperefDataSchema =>
                typerefToJsValue(emptyPath, dataMap, typerefSchema).asInstanceOf[JsObject]
              case unionSchema: UnionDataSchema =>
                unionToJsObject(emptyPath, dataMap, unionSchema)
            }
          case unknown: Any =>
            throw new WriteException(s"Unsupported data type: ${unknown.getClass}")
        }
      }
    }
  }

  def enumerationFormat[T <: ScalaEnumTemplateSymbol](
      enumeration: ScalaEnumTemplate[T]): Format[T] = {
    new Format[T] {
      def reads(json: JsValue): JsResult[T] = {
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

      def writes(value: T): JsValue = {
        JsString(value.toString)
      }
    }
  }

  private[this] def toJsError(validationMessages: Seq[Message]): JsError = {
    JsError(validationMessages.map { message =>
      val path = JsPath(message.getPath.map {
        case idx: java.lang.Integer => IdxPathNode(idx)
        case name: String           => KeyPathNode(name)
      }.toList)
      path -> Seq(JsonValidationError(message.toString))
    })
  }

  /* serialization helpers */

  def recordToJsObject(dataMap: DataMap, recordSchema: RecordDataSchema): JsObject = {
    recordToJsObject(emptyPath, dataMap, recordSchema)
  }

  private[this] def recordToJsObject(
      schemaPath: SchemaPath,
      dataMap: DataMap,
      recordSchema: RecordDataSchema): JsObject = {
    val passthrough =
      recordSchema.getProperties.get(passthroughAnnotation) ==
        java.lang.Boolean.TRUE.asInstanceOf[AnyRef]

    val newSchemaPath = schemaPath.push(recordSchema)

    JsObject(dataMap.entrySet.asScala.flatMap { field =>
      val fieldName = field.getKey
      val fieldData = field.getValue
      val fieldSchema = recordSchema.getField(fieldName)
      if (fieldSchema != null) {
        Some(fieldName -> dataToJsValue(newSchemaPath, fieldData, fieldSchema.getType))
      } else {
        if (passthrough) {
          Some(fieldName -> schemalessDataToJsValue(fieldData))
        } else {
          None
        }
      }
    }.toSeq)
  }

  private[this] def unionToJsObject(
      schemaPath: SchemaPath,
      dataMap: DataMap,
      unionSchema: UnionDataSchema): JsObject = {
    val entryCount = dataMap.entrySet.size
    if (entryCount != 1) {
      throw new WriteException(
        s"The DataMap backing a union should have exactly 1 entry, but it has $entryCount")
    }
    JsObject(dataMap.entrySet.asScala.map { member =>
      val memberName = member.getKey
      val memberData = member.getValue
      val memberSchema = unionSchema.getType(memberName)
      (memberName, dataToJsValue(schemaPath, memberData, memberSchema))
    }.toSeq)
  }

  private[this] def recordToJsValue(
      schemaPath: SchemaPath,
      dataMap: DataMap,
      recordSchema: RecordDataSchema): JsValue = {
    if (recordSchema.getProperties.get(codecAnnotation) == stringKeyName) {
      val codec = new StringKeyCodec(recordSchema)
      val stringKey = new String(codec.mapToBytes(dataMap))
      JsString(stringKey)
    } else {
      recordToJsObject(schemaPath, dataMap, recordSchema)
    }
  }

  private[this] def dataToJsValue(
      schemaPath: SchemaPath,
      data: AnyRef,
      schema: DataSchema): JsValue = {
    (schema, data) match {
      case (typerefSchema: TyperefDataSchema, data: AnyRef) =>
        typerefToJsValue(schemaPath, data, typerefSchema)
      case (arraySchema: ArrayDataSchema, dataList: DataList) =>
        arrayToJsValue(schemaPath, dataList, arraySchema)
      case (recordSchema: RecordDataSchema, dataMap: DataMap) =>
        recordToJsValue(schemaPath, dataMap, recordSchema)
      case (mapSchema: MapDataSchema, dataMap: DataMap) =>
        mapToJsValue(schemaPath, dataMap, mapSchema)
      case (unionSchema: UnionDataSchema, dataMap: DataMap) =>
        unionToJsValue(schemaPath, dataMap, unionSchema)
      case (_: StringDataSchema, string: String) =>
        JsString(string)
      case (_: EnumDataSchema, string: String) =>
        JsString(string)
      case (_, null) =>
        checkSchema[NullDataSchema](schemaPath, "serializing a NULL", schema)
        JsNull
      case (_, boolean: java.lang.Boolean) =>
        checkSchema[BooleanDataSchema](schemaPath, "serializing a BOOLEAN", schema)
        JsBoolean(boolean.booleanValue)
      case (_, integer: java.lang.Integer) =>
        checkSchema[IntegerDataSchema](schemaPath, "serializing an INTEGER", schema)
        JsNumber(BigDecimal(integer))
      case (_, long: java.lang.Long) =>
        checkSchema[LongDataSchema](schemaPath, "serializing a LONG", schema)
        JsNumber(BigDecimal(long))
      case (_, float: java.lang.Float) =>
        checkSchema[FloatDataSchema](schemaPath, "serializing a FLOAT", schema)
        JsNumber(BigDecimal(float.toDouble))
      case (_, double: java.lang.Double) =>
        checkSchema[DoubleDataSchema](schemaPath, "serializing a DOUBLE", schema)
        JsNumber(BigDecimal(double))
      case (_, bytes: ByteString) =>
        checkSchema[BytesDataSchema](schemaPath, "serializing BYTES", schema)
        JsString(bytes.asAvroString)
      case (_, unknown: AnyRef) =>
        throw new WriteException(s"Unsupported JSON type: ${unknown.getClass}")
    }
  }

  private[this] def checkSchema[ExpectedSchemaType <: DataSchema](
      schemaPath: SchemaPath,
      context: String,
      observedSchema: DataSchema)(implicit tag: ClassTag[ExpectedSchemaType]): Unit = {
    val expectedSchemaClass = tag.runtimeClass.asInstanceOf[Class[ExpectedSchemaType]]
    val observedSchemaClass = observedSchema.getClass
    if (observedSchemaClass != expectedSchemaClass) {
      val schemaInfo = schemaPath.last
        .map { schema =>
          s" in ${schema.getFullName}"
        }
        .getOrElse("")
      logger.warn(
        s"Schema mismatch in NewCourierFormats when $context$schemaInfo: " +
          s"expected schema of type ${expectedSchemaClass.getName}, " +
          s"but got ${observedSchemaClass.getName}")
    }
  }

  private[this] def arrayToJsValue(
      schemaPath: SchemaPath,
      dataList: DataList,
      arraySchema: ArrayDataSchema): JsValue = {
    val itemSchema = arraySchema.getItems
    JsArray(dataList.iterator.asScala.map { itemData =>
      dataToJsValue(schemaPath, itemData, itemSchema)
    }.toSeq)
  }

  private[this] def mapToJsValue(
      schemaPath: SchemaPath,
      dataMap: DataMap,
      mapSchema: MapDataSchema): JsValue = {
    val valueSchema = mapSchema.getValues
    JsObject(dataMap.entrySet.asScala.map { entry =>
      (entry.getKey, dataToJsValue(schemaPath, entry.getValue, valueSchema))
    }.toSeq)
  }

  private[this] def unionToJsValue(
      schemaPath: SchemaPath,
      dataMap: DataMap,
      unionSchema: UnionDataSchema): JsValue = {
    unionToJsObject(schemaPath, dataMap, unionSchema)
  }

  private[this] def typerefToJsValue(
      schemaPath: SchemaPath,
      data: AnyRef,
      typerefSchema: TyperefDataSchema): JsValue = {
    val newSchemaPath = schemaPath.push(typerefSchema)

    (typerefSchema.getDereferencedDataSchema, data) match {
      case (unionSchema: UnionDataSchema, dataMap: DataMap) =>
        typerefUnionToJsObject(newSchemaPath, typerefSchema, dataMap, unionSchema)
      case (refSchema, _) =>
        dataToJsValue(newSchemaPath, data, refSchema)
    }
  }

  private[this] def typerefUnionToJsObject(
      schemaPath: SchemaPath,
      typerefSchema: TyperefDataSchema,
      dataMap: DataMap,
      unionSchema: UnionDataSchema): JsObject = {
    getTypedDefinition(typerefSchema) match {
      case None =>
        unionToJsObject(schemaPath, dataMap, unionSchema)
      case Some(TypedDef(nameMap)) =>
        val (memberKey, memberData, memberSchema) =
          destructureUnionMemberDataMap(dataMap, unionSchema)
        val typeName = memberKeyToTypeName(typerefSchema, nameMap, memberKey)
        val definition = dataToJsValue(schemaPath, memberData, memberSchema)
        JsObject(
          Seq(
            typeNameField -> JsString(typeName),
            definitionField -> definition
          ))
      case Some(FlatTypedDef(nameMap)) =>
        val (memberKey, memberData, memberSchema) =
          destructureUnionMemberDataMap(dataMap, unionSchema)
        val typeName = memberKeyToTypeName(typerefSchema, nameMap, memberKey)
        val definition = dataToJsValue(schemaPath, memberData, memberSchema).asInstanceOf[JsObject]
        definition + (typeNameField -> JsString(typeName))
    }
  }

  private[this] def schemalessDataToJsValue(data: AnyRef): JsValue = {
    data match {
      case dataMap: DataMap           => schemalessDataMapToJsObject(dataMap)
      case dataList: DataList         => schemalessDataListToJsArray(dataList)
      case integer: java.lang.Integer => JsNumber(BigDecimal(integer))
      case long: java.lang.Long       => JsNumber(BigDecimal(long))
      case float: java.lang.Float     => JsNumber(BigDecimal(float.toDouble))
      case double: java.lang.Double   => JsNumber(BigDecimal(double))
      case boolean: java.lang.Boolean => JsBoolean(boolean.booleanValue)
      case string: String             => JsString(string)
      case bytes: ByteString          => JsString(bytes.asAvroString)
      case _: Null                    => JsNull
      case unknown: AnyRef =>
        throw new WriteException(s"Unsupported data type: ${unknown.getClass}")
    }
  }

  private[this] def schemalessDataMapToJsObject(dataMap: DataMap): JsObject = {
    JsObject(dataMap.entrySet.asScala.map { entry =>
      entry.getKey -> schemalessDataToJsValue(entry.getValue)
    }.toSeq)
  }

  private[this] def schemalessDataListToJsArray(dataList: DataList): JsArray = {
    JsArray(dataList.iterator.asScala.map { item =>
      schemalessDataToJsValue(item)
    }.toSeq)
  }

  /* deserialization helpers */

  private[this] def jsValueToData(
      schemaPath: SchemaPath,
      jsValue: JsValue,
      schema: DataSchema): AnyRef = {
    (schema, jsValue) match {
      case (_, JsNull) =>
        checkSchema[NullDataSchema](schemaPath, "deserializing a NULL", schema)
        Null.getInstance
      case (arraySchema: ArrayDataSchema, jsArray: JsArray) =>
        jsArrayToDataList(schemaPath, jsArray, arraySchema)
      case (recordSchema: RecordDataSchema, jsValue: JsValue) =>
        jsValueToRecord(schemaPath, jsValue, recordSchema)
      case (mapSchema: MapDataSchema, jsObject: JsObject) =>
        jsObjectToMap(schemaPath, jsObject, mapSchema)
      case (unionSchema: UnionDataSchema, jsObject: JsObject) =>
        jsObjectToUnion(schemaPath, jsObject, unionSchema)
      case (typerefSchema: TyperefDataSchema, jsValue: JsValue) =>
        jsValueToTyperef(schemaPath, jsValue, typerefSchema)
      case (_: StringDataSchema, JsString(string)) =>
        string
      case (_: EnumDataSchema, JsString(name)) =>
        name
      case (_: BytesDataSchema, JsString(bytes)) =>
        ByteString.copyString(bytes, "UTF-8")
      case (_: IntegerDataSchema, JsNumber(value)) =>
        new java.lang.Integer(value.toInt)
      case (_: LongDataSchema, JsNumber(value)) =>
        new java.lang.Long(value.toLong)
      case (_: FloatDataSchema, JsNumber(value)) =>
        new java.lang.Float(value.toFloat)
      case (_: DoubleDataSchema, JsNumber(value)) =>
        new java.lang.Double(value.toDouble)
      case (_, JsBoolean(boolean)) =>
        checkSchema[BooleanDataSchema](schemaPath, "deserializing a BOOLEAN", schema)
        new java.lang.Boolean(boolean)
      case (_, unknown: AnyRef) =>
        throw new ReadException(s"Unsupported JSON type: ${unknown.getClass}")
    }
  }

  private[this] def jsArrayToDataList(
      schemaPath: SchemaPath,
      jsArray: JsArray,
      arraySchema: ArrayDataSchema): DataList = {
    val JsArray(items) = jsArray
    val itemSchema = arraySchema.getItems
    new DataList(items.map { item =>
      jsValueToData(schemaPath, item, itemSchema)
    }.asJava)
  }

  private[this] def jsValueToRecord(
      schemaPath: SchemaPath,
      jsValue: JsValue,
      recordSchema: RecordDataSchema): DataMap = {
    val properties = recordSchema.getProperties
    jsValue match {
      case jsObject: JsObject =>
        jsObjectToRecord(schemaPath, jsObject, recordSchema)
      case JsString(stringKey) if properties.get(codecAnnotation) == stringKeyName =>
        val codec = new StringKeyCodec(recordSchema)
        val dataMap = codec.bytesToMap(stringKey.getBytes("UTF-8"))
        dataMap
    }
  }

  def jsObjectToRecord(jsObject: JsObject, recordSchema: RecordDataSchema): DataMap = {
    jsObjectToRecord(emptyPath, jsObject, recordSchema)
  }

  private[this] def jsObjectToRecord(
      schemaPath: SchemaPath,
      jsObject: JsObject,
      recordSchema: RecordDataSchema): DataMap = {
    val passthrough =
      recordSchema.getProperties.get(passthroughAnnotation) ==
        java.lang.Boolean.TRUE.asInstanceOf[AnyRef]

    val newSchemaPath = schemaPath.push(recordSchema)

    val JsObject(entries) = jsObject
    new DataMap(entries.flatMap {
      case (key, value) =>
        val field = recordSchema.getField(key)
        if (field != null) {
          val result = jsValueToData(newSchemaPath, value, field.getType)
          if (result != Null.getInstance) {
            Some(field.getName -> result)
          } else {
            None
          }
        } else {
          if (passthrough) {
            Some(key -> schemalessJsValueToData(value))
          } else {
            None
          }
        }
    }.asJava)
  }

  private[this] def jsObjectToMap(
      schemaPath: SchemaPath,
      jsObject: JsObject,
      mapSchema: MapDataSchema): DataMap = {
    val JsObject(entries) = jsObject
    val valueSchema = mapSchema.getValues
    new DataMap(entries.map {
      case (key, value) =>
        (key, jsValueToData(schemaPath, value, valueSchema))
    }.asJava)
  }

  private[this] def jsObjectToUnion(
      schemaPath: SchemaPath,
      jsObject: JsObject,
      unionSchema: UnionDataSchema): DataMap = {
    val JsObject(entries) = jsObject
    if (entries.size != 1) {
      throw new ReadException("Union should have exactly one entry.")
    }
    new DataMap(entries.map {
      case (memberName, memberValue) =>
        val memberSchema = unionSchema.getType(memberName)
        (memberName, jsValueToData(schemaPath, memberValue, memberSchema))
    }.asJava)
  }

  private[this] def jsValueToTyperef(
      schemaPath: SchemaPath,
      jsValue: JsValue,
      typerefSchema: TyperefDataSchema): AnyRef = {
    val newSchemaPath = schemaPath.push(typerefSchema)

    (typerefSchema.getDereferencedDataSchema, jsValue) match {
      case (unionSchema: UnionDataSchema, jsObject: JsObject) =>
        jsObjectToTyperefUnion(newSchemaPath, typerefSchema, jsObject, unionSchema)
      case (refSchema, _) =>
        jsValueToData(newSchemaPath, jsValue, refSchema)
    }
  }

  private[this] def jsObjectToTyperefUnion(
      schemaPath: SchemaPath,
      typerefSchema: TyperefDataSchema,
      jsObject: JsObject,
      unionSchema: UnionDataSchema): AnyRef = {
    getTypedDefinition(typerefSchema) match {
      case None =>
        jsObjectToUnion(schemaPath, jsObject, unionSchema)
      case Some(TypedDef(nameMap)) =>
        val (typeName, memberJson) = destructureTypedDefinitionJsObject(jsObject)
        val memberSchema = typeNameToMemberSchema(typerefSchema, unionSchema, nameMap, typeName)
        new DataMap(Seq(memberSchema.getUnionMemberKey -> jsValueToData(
          schemaPath,
          memberJson,
          memberSchema)).toMap.asJava)
      case Some(FlatTypedDef(nameMap)) =>
        val (typeName, memberJson) = destructureFlatTypedDefinitionJsObject(jsObject)
        val memberSchema = typeNameToMemberSchema(typerefSchema, unionSchema, nameMap, typeName)
        new DataMap(Seq(memberSchema.getUnionMemberKey -> jsValueToData(
          schemaPath,
          memberJson,
          memberSchema)).toMap.asJava)
    }
  }

  private[this] def schemalessJsValueToData(jsValue: JsValue): AnyRef = {
    jsValue match {
      case array: JsArray     => schemalessJsObjectToDataMap(array)
      case obj: JsObject      => schemalessJsObjectToDataMap(obj)
      case number: JsNumber   => bigDecimalToNumber(number.value)
      case boolean: JsBoolean => Boolean.box(boolean.value)
      case string: JsString   => string.value
      case JsNull             => Null.getInstance
    }
  }

  private[this] def schemalessJsObjectToDataMap(jsObject: JsObject): DataMap = {
    new DataMap(
      jsObject.fields
        .map {
          case (key, value) =>
            (key, schemalessJsValueToData(value))
        }
        .toMap
        .asJava)
  }

  private[this] def schemalessJsObjectToDataMap(array: JsArray): DataList = {
    new DataList(
      array.value
        .map { item =>
          schemalessJsValueToData(item)
        }
        .toList
        .asJava)
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
    } else {
      val double = value.toDouble
      /* The result will be `Double.NEGATIVE_INFINITY` or `Double.POSITIVE_INFINITY`
         if the `BigDecimal` is outside of the range of `Double`, hence this check. */
      if (double.isInfinity) {
        throw new ReadException(
          s"Unable to convert to numeric value without loss of information: $value")
      }
      double
    }
  }

  /* wrappers for complete backwards compatibility with the original CourierFormats */

  def dataMapToObj(dataMap: DataMap): JsObject = {
    schemalessDataMapToJsObject(dataMap)
  }

  def objToDataMap(obj: JsObject): DataMap = {
    schemalessJsObjectToDataMap(obj)
  }

  /* legacy KeyFormats for complete backwards compatibility with the original CourierFormats */

  def enumerationStringKeyFormat[T <: ScalaEnumTemplateSymbol](
      enumeration: ScalaEnumTemplate[T]): StringKeyFormat[T] = {
    StringKeyFormat[T](k => Try(enumeration.withName(k.key)).toOption, v => StringKey(v.toString))
  }

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
      clazz: Class[T],
      schema: RecordDataSchema): StringKeyFormat[T] = {
    new StringKeyFormat[T] {
      val stringKeyCodec = new StringKeyCodec(schema)

      override def reads(key: StringKey): Option[T] = {
        val string = key.key
        val bytes = string.getBytes(StringKeyCodec.charset)
        try {
          val dataMap = stringKeyCodec.bytesToMap(bytes)
          validateAndFixUp(dataMap, schema)
          dataMap.setReadOnly()
          val wrapped = DataTemplateUtil.wrap(dataMap, clazz)
          materialize(wrapped)
          Some(wrapped)
        } catch {
          case parseException: IOException =>
            logger.debug(s"${parseException.getMessage}", parseException)
            None
          case parseException: NumberFormatException =>
            logger.debug(s"${parseException.getMessage}", parseException)
            None
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
      clazz: Class[T],
      schema: ArrayDataSchema): StringKeyFormat[T] = {
    new StringKeyFormat[T] {
      val stringKeyCodec = new StringKeyCodec(schema)

      override def reads(key: StringKey): Option[T] = {
        val string = key.key
        val bytes = string.getBytes(StringKeyCodec.charset)
        try {
          val dataList = stringKeyCodec.bytesToList(bytes)
          validateAndFixUp(dataList, schema)
          dataList.setReadOnly()
          val wrapped = DataTemplateUtil.wrap(dataList, clazz.getConstructor(classOf[DataList]))
          materialize(wrapped)
          Some(wrapped)
        } catch {
          case parseException: IOException =>
            logger.debug(s"${parseException.getMessage}", parseException)
            None
          case parseException: NumberFormatException =>
            logger.debug(s"${parseException.getMessage}", parseException)
            None
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

  /**
   * Recursively force materialization of lazy vals that are typerefs by materializing the entire tree recursively.
   * This forces any validation logic in the typeref's custom Scala constructor or coercer to run.
   */
  private[this] def materialize(template: DataTemplate[_]): Unit = {
    try {
      // Only record, array, union and map can contain typerefs as elements.
      template match {
        case arrayTemplate: IndexedSeq[_] =>
          arrayTemplate.foreach(materializeIfDataTemplate)
        case mapTemplate: scala.collection.immutable.Map[_, _] =>
          mapTemplate.foreach {
            case (key, value) =>
              materializeIfDataTemplate(key)
              materializeIfDataTemplate(value)
          }
        // Product pattern must come last because arrays and maps are also Products
        case recordOrUnionTemplate: Product =>
          recordOrUnionTemplate.productIterator.foreach(materializeIfDataTemplate)
        case _: Any =>
      }
    } catch {
      case e: TemplateOutputCastException =>
        throw e
      case e: Exception =>
        throw new TemplateOutputCastException(s"${e.getClass.getSimpleName}: ${e.getMessage}", e)
    }
  }

  private[this] def materializeIfDataTemplate(element: Any): Unit = {
    element match {
      case dataTemplate: DataTemplate[_] => materialize(dataTemplate)
      case _: Any                        =>
    }
  }

  private[this] val validationOptions =
    new ValidationOptions(RequiredMode.FIXUP_ABSENT_WITH_DEFAULT, CoercionMode.STRING_TO_PRIMITIVE)

  private[this] def validateAndFixUp(data: Any, schema: DataSchema): Unit = {
    val result = ValidateDataAgainstSchema.validate(data, schema, validationOptions)
    if (!result.isValid) {
      throw new DataValidationException(result)
    }
  }

}
