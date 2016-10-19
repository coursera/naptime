package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.BooleanDataSchema
import com.linkedin.data.schema.BytesDataSchema
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
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.types.NaptimeTypes
import sangria.schema.BooleanType
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.FloatType
import sangria.schema.IntType
import sangria.schema.ListType
import sangria.schema.LongType
import sangria.schema.OptionType
import sangria.schema.StringType
import sangria.schema.Value

import scala.collection.JavaConverters._


object FieldBuilder extends StrictLogging {

  type ResolverType = Context[SangriaGraphQlContext, DataMap] => Value[SangriaGraphQlContext, Any]

  def buildField(
      schemaMetadata: SchemaMetadata,
      field: RecordDataSchemaField,
      namespace: Option[String],
      fieldNameOverride: Option[String] = None): Field[SangriaGraphQlContext, DataMap] = {
    type ResolverType = Context[SangriaGraphQlContext, DataMap] => Value[SangriaGraphQlContext, Any]

    val relatedResourceOption = field.getProperties.asScala.get("related").map(_.toString)

    val fieldName = fieldNameOverride.getOrElse(field.getName)

    val fieldDoc = if (field.getDoc.isEmpty) None else Some(field.getDoc)

    val sangriaField = (relatedResourceOption, field.getType) match {

      // Related resource in a list
      case (Some(relatedResourceName), _: ArrayDataSchema) =>
        NaptimePaginatedResourceField.build(schemaMetadata, relatedResourceName, fieldName)

      // Single related resource
      case (Some(relatedResourceName), _) =>
        NaptimeResourceField.build(schemaMetadata, relatedResourceName, fieldName)

      // Passthrough Exempt types (fallback to DataMap)
      case (None, recordDataSchema: RecordDataSchema)
        if recordDataSchema.getProperties.asScala.get("passthroughExempt").contains(true) =>

        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
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
          name = fieldName,
          fieldType = innerField.fieldType,
          resolve = innerField.resolve)

      case (None, arrayDataSchema: ArrayDataSchema) =>
        val subType = new RecordDataSchemaField(arrayDataSchema.getItems)
        val innerField = buildField(schemaMetadata, subType, namespace, Some(fieldName))
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
          fieldType = ListType(innerField.fieldType),
          resolve = context => context.value.getDataList(fieldName).asScala)

      case (None, _: MapDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
          fieldType = NaptimeTypes.DataMapType,
          resolve = context => context.value.getDataMap(fieldName))

      // Primitives
      case (None, _: StringDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
          fieldType = StringType,
          resolve = context => context.value.getString(fieldName))

      case (None, _: IntegerDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
          fieldType = IntType,
          resolve = context => context.value.getInteger(fieldName))

      case (None, _: LongDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
          fieldType = LongType,
          resolve = context => context.value.getLong(fieldName))

      case (None, _: BooleanDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
          fieldType = BooleanType,
          resolve = context => context.value.getBoolean(fieldName))

      case (None, _: BytesDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
          fieldType = StringType,
          resolve = context => context.value.getByteString(fieldName))

      case (None, _: DoubleDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
          fieldType = FloatType,
          resolve = context => context.value.getDouble(fieldName))

      case (None, _: FloatDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
          fieldType = FloatType,
          resolve = context => context.value.getFloat(fieldName))

      case (None, _: NullDataSchema) =>
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
          fieldType = NaptimeTypes.DataMapType,
          resolve = context => null)

      case (None, _) =>
        logger.error(s"Constructing field for unknown type ${field.getType} [$fieldName]")
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
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
