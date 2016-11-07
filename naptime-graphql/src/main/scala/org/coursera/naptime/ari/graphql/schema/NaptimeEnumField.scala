package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.EnumDataSchema
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.schema.EnumType
import sangria.schema.EnumValue
import sangria.schema.Field

import scala.collection.JavaConverters._

object NaptimeEnumField {

  private[schema] def build(
      enumDataSchema: EnumDataSchema,
      fieldName: String): Field[SangriaGraphQlContext, DataMap] = {

    Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
      name = fieldName,
      fieldType = getType(enumDataSchema),
      resolve = context => context.value.getString(fieldName))
  }

  private[schema] def getType(enumDataSchema: EnumDataSchema): EnumType[String] = {
    EnumType(
      name = FieldBuilder.formatName(enumDataSchema.getFullName),
      values = enumDataSchema.getSymbols.asScala.toList.map(symbol =>
        EnumValue(
          name = symbol,
          description = enumDataSchema.getSymbolDocs.asScala.get(symbol),
          value = symbol)))
  }


}
