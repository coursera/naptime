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

import com.linkedin.data.DataMap
import com.linkedin.data.codec.JacksonDataCodec
import com.linkedin.data.codec.TextDataCodec
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.validation.ValidateDataAgainstSchema
import com.linkedin.data.schema.validation.ValidationOptions
import com.linkedin.data.schema.validation.ValidationResult
import com.linkedin.data.schema.validator.DataSchemaAnnotationValidator
import com.linkedin.data.template.DataTemplate
import com.linkedin.data.template.UnionTemplate
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.courier.templates.DataValidationException
import org.coursera.pegasus.TypedDefinitionCodec

import scala.reflect.ClassTag

/**
 * Provides methods for serializing and deserializing the Pegasus data types used by Courier
 * to JSON.
 *
 * This uses [[org.coursera.pegasus.TypedDefinitionCodec]], the default codec for use with Courier
 * at Coursera.
 *
 * For example, given a generated Courier data binding class named `Profile`, to serialize the
 * Courier data binding class (a.k.a. data template) to JSON:
 *
 * ```
 * val profile = Profile(...)
 * val jsonString = CourierSerializer.write(profile)
 * ```
 *
 * And to Deserialize JSON to the Courier data binding:
 *
 * ```
 * val profile = CourierSerializer.read[Profile](jsonString)
 * ```
 */
object CourierSerializer {

  /**
   * Reads a record template from a JSON string.
   *
   * @throws org.coursera.courier.templates.DataValidationException if validation fails.
   */
  def read[T <: DataTemplate[DataMap]](json: String)(implicit tag: ClassTag[T]): T = {
    val clazz = tag.runtimeClass.asInstanceOf[Class[T]]
    val dataMap = readDataMap(json, tag.runtimeClass.asInstanceOf[Class[T]])
    CourierSerializer.builder(clazz).validateAndBuild(dataMap) match {
      case Right(template) => template
      case Left(validationResult) =>
        throw new DataValidationException(validationResult)
    }
  }

  def write[T <: DataTemplate[DataMap]](template: T)(implicit tag: ClassTag[T]): String = {
    writeDataMap(template.data(), tag.runtimeClass.asInstanceOf[Class[T]])
  }

  /**
   * Reads a union template from a JSON string.
   *
   * @throws org.coursera.courier.templates.DataValidationException if validation fails.
   */
  def readUnion[T <: UnionTemplate](json: String)(implicit tag: ClassTag[T]): T = {
    val clazz = tag.runtimeClass.asInstanceOf[Class[T]]
    val dataMap = readUnion(json, clazz)
    CourierSerializer.builder(clazz).validateAndBuild(dataMap) match {
      case Right(template) => template
      case Left(validationResult) =>
        throw new DataValidationException(validationResult)
    }
  }

  def writeUnion[T <: UnionTemplate](template: T)(implicit tag: ClassTag[T]): String = {
    template.data() match {
      case dataMap: DataMap =>
        writeUnion(dataMap, tag.runtimeClass.asInstanceOf[Class[T]])
      case _: AnyRef =>
        throw new IllegalArgumentException("Null union values not supported by CourierSerializers")
    }
  }

  private[this] val recordValidationOptions = new ValidationOptions()

  class TemplateBuilder[T <: DataTemplate[_ <: AnyRef]](private val clazz: Class[T]) {
    private[this] val companionInstance = companion(clazz)
    private[this] val schema = getSchema(clazz)
    private[this] val annotationValidator = new DataSchemaAnnotationValidator(schema)

    private[this] val buildMethod = {
      companionInstance.getClass.getDeclaredMethod(
        "build",
        classOf[DataMap],
        classOf[DataConversion])
    }

    def build(dataMap: DataMap): T = {
      buildMethod
        .invoke(companionInstance, dataMap, DataConversion.SetReadOnly)
        .asInstanceOf[T]
    }

    def validateAndBuild(dataMap: DataMap): Either[ValidationResult, T] = {
      val validationResult =
        ValidateDataAgainstSchema.validate(
          dataMap,
          schema,
          recordValidationOptions,
          annotationValidator)
      if (!validationResult.isValid) {
        Left(validationResult)
      } else {
        Right(
          buildMethod
            .invoke(companionInstance, dataMap, DataConversion.SetReadOnly)
            .asInstanceOf[T])
      }
    }
  }

  def builder[T <: DataTemplate[_ <: AnyRef]](clazz: Class[T]): TemplateBuilder[T] = {
    new TemplateBuilder(clazz)
  }

  def getSchema[T <: DataTemplate[_]](implicit clazz: Class[T]): DataSchema = {
    getSchema(clazz, schemaFieldName)
  }

  /**
   * For unions declared in a typeref, gets the typeref schema.
   */
  def getDeclaringTyperefSchema[T <: DataTemplate[_]](
      implicit clazz: Class[T]): Option[TyperefDataSchema] = {
    try {
      getSchema(clazz, typerefSchemaFieldName) match {
        case schema: TyperefDataSchema => Some(schema)
        case unknown: DataSchema =>
          throw new IllegalStateException(
            s"$typerefSchemaFieldName must be a TyperefDataSchema but found $unknown")
      }
    } catch {
      case e: NoSuchMethodException => None
    }
  }

  private[this] def getSchema[T <: DataTemplate[_]](
      clazz: Class[T],
      fieldName: String): DataSchema = {
    val companionInstance = companion(clazz)
    val companionClass = companionInstance.getClass
    companionClass
      .getDeclaredMethod(fieldName)
      .invoke(companionInstance)
      .asInstanceOf[DataSchema]
  }

  private[this] val schemaFieldName = "SCHEMA"
  private[this] val typerefSchemaFieldName = "TYPEREF_SCHEMA"

  private[this] def companion(clazz: Class[_]): AnyRef = {
    import scala.reflect.runtime.universe

    val mirror = universe.runtimeMirror(clazz.getClassLoader)
    val classSymbol = mirror.classSymbol(clazz)
    val companionMirror = mirror.reflectModule(classSymbol.companion.asModule)
    companionMirror.instance.asInstanceOf[AnyRef]
  }

  private[this] val underlyingCodec = new JacksonDataCodec()

  private[this] def readDataMap[T <: DataTemplate[DataMap]](
      json: String,
      clazz: Class[T]): DataMap = {
    codec(clazz).stringToMap(json)
  }

  private[this] def readUnion[T <: UnionTemplate](json: String, clazz: Class[T]): DataMap = {
    codec(clazz).stringToMap(json)
  }

  private[this] def writeDataMap[T <: DataTemplate[DataMap]](
      dataMap: DataMap,
      clazz: Class[T]): String = {
    codec(clazz).mapToString(dataMap)
  }

  private[this] def writeUnion[T <: DataTemplate[AnyRef]](
      dataMap: DataMap,
      clazz: Class[T]): String = {
    codec(clazz).mapToString(dataMap)
  }

  private[this] def codec[T <: DataTemplate[_]](clazz: Class[T]): TextDataCodec = {
    codec(CourierSerializer.getSchema(clazz))
  }

  private[this] def codec[T <: DataTemplate[_]](schema: DataSchema): TextDataCodec = {
    new TypedDefinitionCodec(schema, underlyingCodec)
  }
}
