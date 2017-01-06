package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
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
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.Types.Relations
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.schema.NaptimePaginatedResourceField.ForwardRelation
import org.coursera.naptime.ari.graphql.schema.NaptimePaginatedResourceField.ReverseRelation
import org.coursera.naptime.ari.graphql.types.NaptimeTypes
import org.coursera.naptime.schema.ReverseRelationAnnotation
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

  type ResolverType = Context[SangriaGraphQlContext, DataMap] => Value[SangriaGraphQlContext, Any]

  def buildField(
      schemaMetadata: SchemaMetadata,
      field: RecordDataSchemaField,
      namespace: Option[String],
      fieldNameOverride: Option[String] = None,
      followRelations: Boolean = true): Field[SangriaGraphQlContext, DataMap] = {
    type ResolverType = Context[SangriaGraphQlContext, DataMap] => Value[SangriaGraphQlContext, Any]

    val fieldProperties = field.getProperties.asScala
    val forwardRelationOption = fieldProperties.get(Relations.PROPERTY_NAME).map(_.toString)
    val reverseRelationOption = fieldProperties.get(Relations.REVERSE_PROPERTY_NAME)
      .map(d => ReverseRelationAnnotation(d.asInstanceOf[DataMap], DataConversion.SetReadOnly))

    val relatedResourceOption = if (followRelations) {
      forwardRelationOption.orElse(reverseRelationOption.map(_.resourceName))
    } else {
      None
    }

    val fieldName = fieldNameOverride.getOrElse(field.getName)

    val fieldDoc = if (field.getDoc.isEmpty) None else Some(field.getDoc)

    val sangriaField = (relatedResourceOption, field.getType) match {

      // Related resource in a list
      case (Some(relatedResourceName), _: ArrayDataSchema) =>
        val fieldRelation = forwardRelationOption.map(ForwardRelation)
          .orElse(reverseRelationOption.map(ReverseRelation))
        NaptimePaginatedResourceField
          .build(schemaMetadata, relatedResourceName, fieldName, None, fieldRelation)
          .getOrElse {
            buildField(schemaMetadata, field, namespace, fieldNameOverride, followRelations = false)
          }

      // Single related resource
      case (Some(relatedResourceName), _) =>
        NaptimeResourceField
          .build(schemaMetadata, relatedResourceName, fieldName)
          .getOrElse {
            buildField(schemaMetadata, field, namespace, fieldNameOverride, followRelations = false)
          }

      // Passthrough Exempt types (fallback to DataMap)
      case (None, recordDataSchema: RecordDataSchema)
        if recordDataSchema.getProperties.asScala.get("passthroughExempt").contains(true) =>

        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = FieldBuilder.formatName(fieldName),
          fieldType = NaptimeTypes.DataMapType,
          resolve = context => context.value.getDataMap(fieldName))

      // Complex types
      case (None, recordDataSchema: RecordDataSchema) =>
        NaptimeRecordField.build(schemaMetadata, recordDataSchema, fieldName, namespace)

      case (None, enumDataSchema: EnumDataSchema) =>
        NaptimeEnumField.build(enumDataSchema, fieldName)

      case (None, unionDataSchema: UnionDataSchema) =>
        NaptimeUnionField.build(schemaMetadata, unionDataSchema, fieldName, namespace)

      case (None, typerefDataSchema: TyperefDataSchema) =>
        val referencedType = new RecordDataSchemaField(typerefDataSchema.getDereferencedDataSchema)
        val innerField = buildField(schemaMetadata, referencedType, namespace, Some(fieldName))
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = FieldBuilder.formatName(fieldName),
          fieldType = innerField.fieldType,
          resolve = innerField.resolve)

      case (None, arrayDataSchema: ArrayDataSchema) =>
        val subType = new RecordDataSchemaField(arrayDataSchema.getItems)
        val innerField = buildField(schemaMetadata, subType, namespace, Some(fieldName))
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = FieldBuilder.formatName(fieldName),
          fieldType = ListType(innerField.fieldType),
          resolve = context => Option(context.value.getDataList(fieldName))
            .map(_.asScala)
            .getOrElse(null))

      case (None, _: MapDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = FieldBuilder.formatName(fieldName),
          fieldType = NaptimeTypes.DataMapType,
          resolve = context => context.value.getDataMap(fieldName))

      // Primitives
      case (None, ds: StringDataSchema) => buildPrimitiveField[String](fieldName, ds, StringType)
      case (None, ds: BytesDataSchema) => buildPrimitiveField[String](fieldName, ds, StringType)
      case (None, ds: IntegerDataSchema) => buildPrimitiveField[Int](fieldName, ds, IntType)
      case (None, ds: LongDataSchema) => buildPrimitiveField[Long](fieldName, ds, LongType)
      case (None, ds: BooleanDataSchema) => buildPrimitiveField[Boolean](fieldName, ds, BooleanType)
      case (None, ds: DoubleDataSchema) => buildPrimitiveField[Double](fieldName, ds, FloatType)
      case (None, ds: FloatDataSchema) => buildPrimitiveField[Float](fieldName, ds, FloatType)

      case (None, _: NullDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = FieldBuilder.formatName(fieldName),
          fieldType = NaptimeTypes.DataMapType,
          resolve = context => null)

      case (None, _) =>
        logger.error(s"Constructing field for unknown type ${field.getType} [$fieldName]")
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = FieldBuilder.formatName(fieldName),
          fieldType = NaptimeTypes.DataMapType,
          resolve = context => context.value)
    }

    val fieldTypeWithOptionality = if (field.getOptional) {
      OptionType(sangriaField.fieldType)
    } else {
      sangriaField.fieldType
    }

    sangriaField.copy(
      description = fieldDoc,
      fieldType = fieldTypeWithOptionality)
  }

  private[this] val validationOptions = new ValidationOptions(
    RequiredMode.FIXUP_ABSENT_WITH_DEFAULT,
    CoercionMode.STRING_TO_PRIMITIVE)

  private[schema] def buildPrimitiveField[ParseType](
      fieldName: String,
      dataSchema: DataSchema,
      sangriaScalarType: ScalarType[_])
      (implicit tag: ClassTag[ParseType]) = {
    Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
      name = FieldBuilder.formatName(fieldName),
      fieldType = sangriaScalarType,
      resolve = context => {
        Option(context.value.get(fieldName)).map { rawValue =>
          val result = ValidateDataAgainstSchema.validate(rawValue, dataSchema, validationOptions)

          if (result.isValid) {
            result.getFixed match {
              case value: ParseType => value
              case _ => throw ResponseFormatException(s"$fieldName's value is an invalid type")
            }
          } else {
            throw ResponseFormatException(s"$fieldName could not be fixed-up or parsed")
          }
        }.getOrElse(null)
      })
  }

  /**
    * Converts a field or namespace name to a GraphQL compatible name, replacing '.' with '_'
    *
    * @param name Original field name
    * @return GraphQL-safe field name
    */
  def formatName(name: String): String = {
    name.replaceAll("\\.", "_")
  }

}
