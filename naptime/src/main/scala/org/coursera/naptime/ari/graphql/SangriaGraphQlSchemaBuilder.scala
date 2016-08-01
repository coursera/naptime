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

import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.BooleanDataSchema
import com.linkedin.data.schema.BytesDataSchema
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.DoubleDataSchema
import com.linkedin.data.schema.EnumDataSchema
import com.linkedin.data.schema.FloatDataSchema
import com.linkedin.data.schema.IntegerDataSchema
import com.linkedin.data.schema.LongDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.StringDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import org.coursera.courier.templates.ScalaRecordTemplate
import org.coursera.naptime.schema.Resource
import sangria.schema.BooleanType
import sangria.schema.Context
import sangria.schema.EnumType
import sangria.schema.EnumValue
import sangria.schema.Field
import sangria.schema.FloatType
import sangria.schema.IntType
import sangria.schema.ListType
import sangria.schema.LongType
import sangria.schema.ObjectType
import sangria.schema.OptionType
import sangria.schema.OutputType
import sangria.schema.Schema
import sangria.schema.StringType
import sangria.schema.UnionType
import sangria.schema.Value

import scala.collection.JavaConverters._

class SangriaGraphQlSchemaBuilder(
    resources: Set[Resource],
    schemas: Map[String, RecordDataSchema]) {

  /**
    * Generates a GraphQL schema for the provided set of resources to this class
    * Returns a "root" object that has one field available for each Naptime Resource provided.*
    *
    * @return a Sangria GraphQL Schema with all resources defined
    */
  def generateSchema(): Schema[Unit, ScalaRecordTemplate] = {
    val topLevelResourceObjects = resources.map { resource =>
      val resourceObject = generateObjectTypeForResource(resource.name)
      Field.apply[Unit, ScalaRecordTemplate, Unit, Any](
        formatResourceName(resource.name),
        resourceObject,
        resolve = sangriaResolveFn)
    }

    val rootObject = ObjectType[Unit, ScalaRecordTemplate](
      name = "Root",
      description = "Top-level accessor for Naptime resources",
      fields = topLevelResourceObjects.toList)
    Schema(rootObject)
  }

  /**
    * Generates an object-type for a given resource name, with each field on the merged output
    * schema available on this object-type.
    *
    * @param resourceName String name of the resource (i.e. 'courses.v1')
    * @return ObjectType for the resource
    */
  def generateObjectTypeForResource(resourceName: String): ObjectType[Unit, ScalaRecordTemplate] = {
    val resource = resources.find(_.name == resourceName).getOrElse {
      throw new RuntimeException(s"Cannot find resource with name $resourceName")
    }

    val schema = schemas.getOrElse(resource.mergedType, {
      throw new RuntimeException(s"Cannot find schema for ${resource.mergedType}")
    })

    ObjectType[Unit, ScalaRecordTemplate](
      name = formatResourceName(resource.name),
      description = "",
      fieldsFn = () => {
        schema.getFields.asScala.map { field =>
          generateField(field, schema.getNamespace)
        }.toList
      })
  }

  // NOTE: We don't currently use sangria's `resolve` functionality for now
  val sangriaResolveFn = (context: Context[Unit, ScalaRecordTemplate]) => Value[Unit, Unit](Unit)

  /**
    * Generates a single GraphQL schema field for a RecordDataSchema field type.
    * If the field is marked as a related resource, generates the field as a relationship to the
    * associated resource. Otherwise, generates a generic schema for the model definition.
    *
    * @param field RecordDataSchema.Field for the field, pulled off the Courier schema for the model
    * @param namespace The namespace for the source model, used to prevent name collisions
    * @return GraphQL schema Field with nested schema information
    */
  def generateField(
      field: RecordDataSchema.Field,
      namespace: String): Field[Unit, ScalaRecordTemplate] = {

    val fieldScalarType = (field.getProperties.asScala.get("related"), field.getType) match {
      case (Some(relatedResourceName), _: ArrayDataSchema) =>
        ListType(generateObjectTypeForResource(relatedResourceName.toString))
      case (Some(relatedResourceName), _) =>
        generateObjectTypeForResource(relatedResourceName.toString)
      case (None, _) =>
        getSangriaTypeForSchema(field.getType, field.getName, namespace)
    }
    val fieldScalarTypeWithOptionality = if (field.getOptional) {
      OptionType(fieldScalarType)
    } else {
      fieldScalarType
    }
    Field.apply[Unit, ScalaRecordTemplate, Unit, Any](
      field.getName,
      fieldScalarTypeWithOptionality,
      resolve = sangriaResolveFn)
  }

  /**
    * Converts a Pegasus DataSchema to Sangria GraphQL Schema type for use when generating a schema.
    *
    * Nested objects schemas are computed recursively.
    * Union types generate child ObjectTypes for their member classes
    *
    * @param schemaType DataSchema from the field, which specifies the source field type
    * @param fieldName The field's name, which is used to generate union field member types
    * @param namespace The field's namespace, which is used to prevent name colissions.
    * @return Sangria GraphQL OutputType, which represents the structure of the field in the schema
    */
  def getSangriaTypeForSchema(
      schemaType: DataSchema,
      fieldName: String,
      namespace: String): OutputType[Any] = {

    schemaType match {
      case stringField: StringDataSchema => StringType
      case intField: IntegerDataSchema => IntType
      case longField: LongDataSchema => LongType
      case booleanField: BooleanDataSchema => BooleanType
      case bytesField: BytesDataSchema => StringType
      case doubleField: DoubleDataSchema => FloatType
      case floatField: FloatDataSchema => FloatType
      case enumField: EnumDataSchema =>
        EnumType(
          name = formatName(enumField.getFullName),
          values = enumField.getSymbols.asScala.map(symbol =>
            EnumValue(
              name = symbol,
              description = enumField.getSymbolDocs.asScala.get(symbol),
              value = symbol)).toList)
      case arrayField: ArrayDataSchema =>
        ListType(getSangriaTypeForSchema(arrayField.getItems, fieldName, namespace))
      case typeRefField: TyperefDataSchema =>
        getSangriaTypeForSchema(typeRefField.getRef, typeRefField.getName, namespace)
      case recordField: RecordDataSchema =>
        ObjectType[Unit, ScalaRecordTemplate](
          formatName(recordField.getFullName),
          recordField.getDoc,
          recordField.getFields.asScala.map(generateField(_, namespace)).toList)
      case unionField: UnionDataSchema =>
        val objects = unionField.getTypes.asScala.map { subType =>
          val field = Field.apply[Unit, ScalaRecordTemplate, Unit, Any](
            formatName(subType.getUnionMemberKey),
            getSangriaTypeForSchema(subType, fieldName, namespace),
            resolve = sangriaResolveFn)
          ObjectType[Unit, ScalaRecordTemplate](
            formatName(s"${subType.getUnionMemberKey}Member"),
            "",
            List(field))
        }.toList
        val unionName = formatName(s"$namespace.$fieldName")
        UnionType(unionName, None, objects)
      case _ =>
        throw new Exception(s"Cannot find type for $schemaType")
    }
  }

  /**
    * Converts a field or namespace name to a GraphQL compatible name, replacing '.' with '_'
    * @param name Original field name
    * @return GraphQL-safe field name
    */
  def formatName(name: String): String = {
    name.replaceAll("\\.", "_")
  }

  /**
    * Converts a resource name to a GraphQL compatible name. (i.e. 'courses.v1' to 'CoursesV1')
    * @param name Original resource name
    * @return GraphQL-safe resource name
    */
  def formatResourceName(name: String): String = {
    name.replace(".v", "V").capitalize
  }
}
