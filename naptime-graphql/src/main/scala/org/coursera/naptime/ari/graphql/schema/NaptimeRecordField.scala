package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.StringType

import scala.collection.JavaConverters._

object NaptimeRecordField {

  private[schema] def build(
      schemaMetadata: SchemaMetadata,
      recordDataSchema: RecordDataSchema,
      fieldName: String,
      namespace: Option[String]) = {

    Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
      name = fieldName,
      fieldType = getType(schemaMetadata, recordDataSchema, namespace),
      resolve = context => context.value.getDataMap(fieldName))
  }

  private[schema] def getType(
      schemaMetadata: SchemaMetadata,
      recordDataSchema: RecordDataSchema,
      namespace: Option[String]): ObjectType[SangriaGraphQlContext, DataMap] = {

    ObjectType[SangriaGraphQlContext, DataMap](
      FieldBuilder.formatName(recordDataSchema.getFullName),
      recordDataSchema.getDoc,
      fieldsFn = () => {
        val fields = recordDataSchema.getFields.asScala.map(field =>
          FieldBuilder.buildField(schemaMetadata, field, namespace)).toList
        if (fields.isEmpty) {
          // TODO(bryan): Handle this case better
          EMPTY_FIELDS_FALLBACK
        } else {
          fields
        }
      })
  }

  val EMPTY_FIELDS_FALLBACK = List(
    Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
      "ArbitraryField",
      StringType,
      resolve = context => null))


}
