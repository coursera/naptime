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
      fieldName: String): Field[SangriaGraphQlContext, DataMapWithParent] = {

    Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
      name = FieldBuilder.formatName(fieldName),
      fieldType = getType(enumDataSchema),
      resolve = context => context.value.element.getString(fieldName))
  }

  private[schema] def getType(enumDataSchema: EnumDataSchema): EnumType[String] = {
    val enumSymbols = if (enumDataSchema.getSymbols.asScala.nonEmpty) {
      enumDataSchema.getSymbols.asScala.toList
    } else {
      List("UNKNOWN")
    }
    EnumType(
      name = FieldBuilder.formatName(enumDataSchema.getFullName),
      values = enumSymbols.map(
        symbol =>
          EnumValue(
            name = FieldBuilder.formatName(symbol),
            description = enumDataSchema.getSymbolDocs.asScala.get(symbol),
            value = symbol))
    )
  }

}
