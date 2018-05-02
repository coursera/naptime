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

import com.linkedin.data.DataMap
import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.BooleanDataSchema
import com.linkedin.data.schema.BytesDataSchema
import com.linkedin.data.schema.ComplexDataSchema
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.DoubleDataSchema
import com.linkedin.data.schema.EnumDataSchema
import com.linkedin.data.schema.FloatDataSchema
import com.linkedin.data.schema.IntegerDataSchema
import com.linkedin.data.schema.LongDataSchema
import com.linkedin.data.schema.MapDataSchema
import com.linkedin.data.schema.NullDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.RecordDataSchema.{Field => RecordDataSchemaField}
import com.linkedin.data.schema.StringDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import com.linkedin.data.schema.validation.CoercionMode
import com.linkedin.data.schema.validation.RequiredMode
import com.linkedin.data.schema.validation.ValidateDataAgainstSchema
import com.linkedin.data.schema.validation.ValidationOptions
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.types.NaptimeTypes
import sangria.schema.Action
import sangria.schema.BooleanType
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.FloatType
import sangria.schema.IntType
import sangria.schema.ListType
import sangria.schema.LongType
import sangria.schema.OptionType
import sangria.schema.ScalarType
import sangria.schema.StringType
import sangria.schema.Value

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

object FieldBuilder extends StrictLogging {

  type ResolverType =
    Context[SangriaGraphQlContext, DataMapWithParent] => Action[SangriaGraphQlContext, Any]

  def buildField(
      schemaMetadata: SchemaMetadata,
      field: RecordDataSchemaField,
      namespace: Option[String],
      fieldNameOverride: Option[String] = None,
      followRelations: Boolean = true,
      resourceName: ResourceName,
      currentPath: List[String] = List.empty): Field[SangriaGraphQlContext, DataMapWithParent] = {

    type ResolverType =
      Context[SangriaGraphQlContext, DataMapWithParent] => Value[SangriaGraphQlContext, Any]

    val relatedResourceOption = if (followRelations) {
      ReverseRelation
        .parse(field)
        .map(_.resourceName)
        .flatMap(ResourceName.parse)
    } else {
      None
    }

    val fieldName = fieldNameOverride.getOrElse(field.getName)

    val fieldDoc = if (field.getDoc.isEmpty) None else Some(field.getDoc)

    val sangriaField = (relatedResourceOption, field.getType) match {

      // Related resource in a list
      case (Some(relatedResourceName), _: ArrayDataSchema) =>
        // TODO(bryan): don't throw away errors from the left here
        NaptimePaginatedResourceField
          .build(
            schemaMetadata,
            relatedResourceName,
            fieldName,
            None,
            ReverseRelation.parse(field),
            currentPath)
          .right
          .getOrElse {
            buildField(
              schemaMetadata,
              field,
              namespace,
              fieldNameOverride,
              followRelations = false,
              resourceName = resourceName,
              currentPath = currentPath)
          }

      // Single related resource
      case (Some(relatedResourceName), _) =>
        // TODO(bryan): don't throw away errors from the left here
        NaptimeResourceField
          .build(
            schemaMetadata,
            relatedResourceName,
            fieldName,
            ReverseRelation.parse(field),
            currentPath)
          .right
          .getOrElse {
            buildField(
              schemaMetadata,
              field,
              namespace,
              fieldNameOverride,
              followRelations = false,
              resourceName = resourceName,
              currentPath = currentPath)
          }

      // Passthrough Exempt types (fallback to DataMap)
      case (None, recordDataSchema: RecordDataSchema)
          if recordDataSchema.getProperties.asScala
            .get("passthroughExempt")
            .contains(true) =>
        Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
          name = FieldBuilder.formatName(fieldName),
          fieldType = NaptimeTypes.DataMapType,
          resolve =
            context => context.value.copy(element = context.value.element.getDataMap(fieldName))
        )

      // Complex types
      case (None, recordDataSchema: RecordDataSchema) =>
        NaptimeRecordField.build(
          schemaMetadata,
          recordDataSchema,
          fieldName,
          namespace,
          resourceName,
          currentPath)

      case (None, enumDataSchema: EnumDataSchema) =>
        NaptimeEnumField.build(enumDataSchema, fieldName)

      case (None, unionDataSchema: UnionDataSchema) =>
        NaptimeUnionField.build(
          schemaMetadata,
          unionDataSchema,
          fieldName,
          namespace,
          resourceName,
          currentPath)

      case (None, typerefDataSchema: TyperefDataSchema) =>
        val dereferencedSchema = typerefDataSchema.getDereferencedDataSchema
        val dereferencedSchemaWithProperties = dereferencedSchema match {
          case complex: ComplexDataSchema => {
            val properties =
              (typerefDataSchema.getProperties.asScala ++ Map(
                "namespace" -> typerefDataSchema.getNamespace)).asJava
            complex.setProperties(properties)
            complex
          }
          case _ => dereferencedSchema
        }

        val referencedType = new RecordDataSchemaField(dereferencedSchemaWithProperties)

        val innerField = buildField(
          schemaMetadata,
          referencedType,
          namespace,
          Some(fieldName),
          resourceName = resourceName,
          currentPath = currentPath)
        Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
          name = FieldBuilder.formatName(fieldName),
          fieldType = innerField.fieldType,
          resolve = innerField.resolve)

      case (None, arrayDataSchema: ArrayDataSchema) =>
        val subType = new RecordDataSchemaField(arrayDataSchema.getItems)
        val innerField = buildField(
          schemaMetadata,
          subType,
          namespace,
          Some(fieldName),
          resourceName = resourceName,
          currentPath = currentPath)
        Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
          name = FieldBuilder.formatName(fieldName),
          fieldType = ListType(innerField.fieldType),
          resolve = context =>
            Option(context.value.element.getDataList(fieldName))
              .map(_.asScala.map {
                case dataMap: DataMap => context.value.copy(element = dataMap)
                case element: Any     => element
              })
              .getOrElse(null)
        )

      case (None, _: MapDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
          name = FieldBuilder.formatName(fieldName),
          fieldType = NaptimeTypes.DataMapType,
          resolve = context => context.value.element.getDataMap(fieldName))

      // Primitives
      case (None, ds: StringDataSchema) =>
        buildPrimitiveField[String](fieldName, ds, StringType)
      case (None, ds: BytesDataSchema) =>
        buildPrimitiveField[String](fieldName, ds, StringType)
      case (None, ds: IntegerDataSchema) =>
        buildPrimitiveField[Int](fieldName, ds, IntType)
      case (None, ds: LongDataSchema) =>
        buildPrimitiveField[Long](fieldName, ds, LongType)
      case (None, ds: BooleanDataSchema) =>
        buildPrimitiveField[Boolean](fieldName, ds, BooleanType)
      case (None, ds: DoubleDataSchema) =>
        buildPrimitiveField[Double](fieldName, ds, FloatType)
      case (None, ds: FloatDataSchema) =>
        buildPrimitiveField[Float](fieldName, ds, FloatType)

      case (None, _: NullDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
          name = FieldBuilder.formatName(fieldName),
          fieldType = NaptimeTypes.DataMapType,
          resolve = context => null)

      case (None, _) =>
        logger.error(s"Constructing field for unknown type ${field.getType} [$fieldName]")
        Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
          name = FieldBuilder.formatName(fieldName),
          fieldType = NaptimeTypes.DataMapType,
          resolve = context => context.value)
    }

    val (fieldTypeWithOptionality, resolverWithOptionality) =
      if (field.getOptional) {
        val updatedFieldType = OptionType(sangriaField.fieldType)
        val updatedResolver =
          (context: Context[SangriaGraphQlContext, DataMapWithParent]) => {
            sangriaField
              .resolve(context)
              .map {
                case null =>
                  None
                case value: DataMapWithParent =>
                  if (value.element == null) {
                    None
                  } else {
                    value
                  }
                case value: Any =>
                  Option(value)
              }(context.ctx.executionContext)
          }
        (updatedFieldType, updatedResolver)
      } else {
        (sangriaField.fieldType, sangriaField.resolve)
      }

    sangriaField.copy(
      description = fieldDoc,
      fieldType = fieldTypeWithOptionality,
      resolve = resolverWithOptionality)
  }

  private[this] val validationOptions =
    new ValidationOptions(RequiredMode.FIXUP_ABSENT_WITH_DEFAULT, CoercionMode.STRING_TO_PRIMITIVE)

  private[schema] def buildPrimitiveField[ParseType](
      fieldName: String,
      dataSchema: DataSchema,
      sangriaScalarType: ScalarType[_])(implicit tag: ClassTag[ParseType]) = {
    Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
      name = FieldBuilder.formatName(fieldName),
      fieldType = sangriaScalarType,
      resolve = context => {
        Option(context.value.element.get(fieldName))
          .map {
            rawValue =>
              val result =
                ValidateDataAgainstSchema.validate(rawValue, dataSchema, validationOptions)

              if (result.isValid) {
                result.getFixed match {
                  case value: ParseType => value
                  case _ =>
                    throw ResponseFormatException(s"$fieldName's value is an invalid type")
                }
              } else {
                throw ResponseFormatException(s"$fieldName could not be fixed-up or parsed")
              }
          }
          .getOrElse(null)
      }
    )
  }

  /**
   * Converts a field or namespace name to a GraphQL compatible name, replacing '.' with '_',
   * '/' with '_', and fields starting with '__' to '_'
   *
   * @param name Original field name
   * @return GraphQL-safe field name
   */
  def formatName(name: String): String = {
    val replacedName = name.replaceAll("\\.", "_").replaceAll("/", "_")
    if (replacedName.startsWith("__")) {
      replacedName.replaceFirst("__", "_")
    } else {
      replacedName
    }
  }

}
