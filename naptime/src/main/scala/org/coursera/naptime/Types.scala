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

package org.coursera.naptime

import java.lang.StringBuilder

import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.MapDataSchema
import com.linkedin.data.schema.Name
import com.linkedin.data.schema.PrimitiveDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.RecordDataSchema.RecordType
import com.linkedin.data.schema.StringDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.schema.RelationType.FINDER
import org.coursera.naptime.schema.RelationType.GET
import org.coursera.naptime.schema.RelationType.MULTI_GET
import org.coursera.naptime.schema.RelationType.SINGLE_ELEMENT_FINDER

import scala.collection.JavaConverters._

object Types extends StrictLogging {

  object Relations {
    val PROPERTY_NAME = "related"
    val REVERSE_PROPERTY_NAME = "relatedOn"
  }

  @deprecated("Please use the one with fields included", "0.2.4")
  def computeAsymType(
      typeName: String,
      keyType: DataSchema,
      valueType: RecordDataSchema): RecordDataSchema = {
    if (keyType.hasError || valueType.hasError) {
      throw new RuntimeException(s"Input schemas have error: $keyType $valueType")
    }
    keyType match {
      case primitive: PrimitiveDataSchema =>
        computeAsymTypeWithPrimitiveKey(typeName, primitive, valueType)
      case record: RecordDataSchema =>
        computeAsymTypeWithRecordKey(typeName, record, valueType)
      case typeref: TyperefDataSchema =>
        ??? // TODO(saeta): handle typeref schemas
      case unknown: DataSchema =>
        throw new RuntimeException(s"Cannot compute asymmetric type for key type: $unknown")
    }
  }

  /**
   * Computes an asymmetric type from the schemas for the input types.
   *
   * TODO: extend to support server-computed fields!
   *
   * @param typeName The full name of the computed type.
   * @param keyType The schema for the key.
   * @param valueType The schema for the values.
   * @return The computed asymmetric type.
   * @throws RuntimeException if problems are encountered.
   */
  def computeAsymType(
      typeName: String,
      keyType: DataSchema,
      valueType: RecordDataSchema,
      fields: Fields[_]): RecordDataSchema = {
    if (keyType.hasError || valueType.hasError) {
      throw new RuntimeException(s"Input schemas have error: $keyType $valueType")
    }
    val mergedSchema = keyType match {
      case primitive: PrimitiveDataSchema =>
        computeAsymTypeWithPrimitiveKey(typeName, primitive, valueType)
      case record: RecordDataSchema =>
        computeAsymTypeWithRecordKey(typeName, record, valueType)
      case typeref: TyperefDataSchema =>
        ??? // TODO(saeta): handle typeref schemas
      case unknown: DataSchema =>
        throw new RuntimeException(s"Cannot compute asymmetric type for key type: $unknown")
    }
    for ((name, reverseRelation) <- fields.reverseRelations) {
      Option(mergedSchema.getField(name)) match {
        case Some(field) =>
          logger.warn(
            s"Fields for resource $typeName tries to add reverse relation on field " +
              s"'$name', but that field is already defined on the model")
        case None =>
          val errorMessageBuilder = new StringBuilder
          val newField = reverseRelation.toAnnotation.relationType match {
            case FINDER | MULTI_GET =>
              val newField = new RecordDataSchema.Field(new ArrayDataSchema(new StringDataSchema)) // TODO(bryan): fix type here
              newField.setOptional(false)
              newField
            case SINGLE_ELEMENT_FINDER | GET =>
              val newField = new RecordDataSchema.Field(new StringDataSchema) // TODO(bryan): fix type here
              newField.setOptional(true)
              newField
            case _ =>
              throw new RuntimeException(
                "unknown relation type: " +
                  reverseRelation.toAnnotation.relationType.toString)
          }
          val reverseRelatedMap = Map[String, AnyRef](
            Relations.REVERSE_PROPERTY_NAME -> reverseRelation.toAnnotation
              .data())
          newField.setProperties(reverseRelatedMap.asJava)
          newField.setDoc(reverseRelation.description)
          newField.setName(name.split("/").last, errorMessageBuilder)
          insertFieldAtLocation(mergedSchema, name.split("/").dropRight(1).toList, newField)
      }
    }
    mergedSchema
  }

  /**
   * Merges a Record key type and a value [Record] type into a new asymmetric type.
   *
   * TODO: change the schemas / types of embedded key types to primitives.
   *
   * @param typeName The name of the asymmetric type to create.
   * @param keyType The schema for the key type of the naptime resource.
   * @param valueType The schema for the value type of the naptime resource.
   * @return The new asymmetric type.
   */
  private[this] def computeAsymTypeWithRecordKey(
      typeName: String,
      keyType: RecordDataSchema,
      valueType: RecordDataSchema): RecordDataSchema = {
    val errorMessageBuilder = new StringBuilder
    val recordDataSchema =
      new RecordDataSchema(new Name(typeName), RecordType.RECORD)
    val combinedFields = keyType.getFields().asScala.toList ++ valueType
      .getFields()
      .asScala
    val fields = if (combinedFields.exists(_.getName == "id")) {
      combinedFields // The key type had an `id` field defined as part of it.
    } else {
      val idField = new RecordDataSchema.Field(new StringDataSchema)
      idField.setOptional(false)
      idField.setName("id", errorMessageBuilder)
      idField.setRecord(recordDataSchema)
      idField :: combinedFields
    }
    recordDataSchema.setFields(fields.asJava, errorMessageBuilder)
    if (errorMessageBuilder.length() > 0) {
      logger.warn(s"Error while computing asymmetric type $typeName: $errorMessageBuilder")
    }
    recordDataSchema
  }

  /**
   * Merges a primitive key type and a value [Record] type into a new asymmetric type.
   *
   * TODO: change the schemas / types of embedded key types to primitives.
   *
   * @param typeName THe name of the asymmetric type to create.
   * @param keyType The schema for the key type of the naptime resource.
   * @param valueType The schema for the value type of the naptime resource.
   * @return The new asymmetric type.
   */
  private[this] def computeAsymTypeWithPrimitiveKey(
      typeName: String,
      keyType: PrimitiveDataSchema,
      valueType: RecordDataSchema): RecordDataSchema = {
    val errorMessageBuilder = new StringBuilder
    val recordDataSchema =
      new RecordDataSchema(new Name(typeName), RecordType.RECORD)
    val idField = new RecordDataSchema.Field(keyType)
    idField.setOptional(false)
    idField.setName("id", errorMessageBuilder)
    idField.setRecord(recordDataSchema)
    val fields = idField :: valueType.getFields().asScala.toList
    recordDataSchema.setFields(fields.asJava, errorMessageBuilder)
    if (errorMessageBuilder.length() > 0) {
      logger.warn(s"Error while computing asymmetric type $typeName: $errorMessageBuilder")
    }
    recordDataSchema
  }

  // Adapted from DataSchemaUtil.getField
  private[this] def insertFieldAtLocation(
      schema: DataSchema,
      location: List[String],
      field: RecordDataSchema.Field): DataSchema = {

    schema.getDereferencedDataSchema match {
      case mapDataSchema: MapDataSchema =>
        insertFieldAtLocation(mapDataSchema.getValues, location, field)
      case arrayDataSchema: ArrayDataSchema =>
        insertFieldAtLocation(arrayDataSchema.getItems, location, field)
      case recordDataSchema: RecordDataSchema =>
        if (location.isEmpty) {
          field.setRecord(recordDataSchema)
          val existingFields = recordDataSchema.getFields
          val newFields = (existingFields.asScala ++ List(field)).asJava
          val errorMessageBuilder = new StringBuilder
          recordDataSchema.setFields(newFields, errorMessageBuilder)
          val error = errorMessageBuilder.toString
          if (error.nonEmpty) {
            logger.warn("Error while inserting field", error)
          }
          recordDataSchema
        } else {
          val fieldOption = Option(recordDataSchema.getField(location.head))
          fieldOption
            .map { recordField =>
              insertFieldAtLocation(recordField.getType, location.tail, field)
            }
            .getOrElse {
              logger.warn(s"Could not find field ${location.headOption} on record $schema")
              schema
            }
        }
      case unionDataSchema: UnionDataSchema =>
        location.headOption
          .flatMap(loc => Option(unionDataSchema.getType(loc)))
          .map { unionSchema =>
            insertFieldAtLocation(unionSchema, location.tail, field)
          }
          .getOrElse {
            logger.warn(s"Could not find type ${location.headOption} on union $schema")
            schema
          }
      case _ =>
        schema
    }

  }
}
