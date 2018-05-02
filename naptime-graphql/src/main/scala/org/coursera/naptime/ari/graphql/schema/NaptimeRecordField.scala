package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.StringType
import sangria.schema.Value

import scala.collection.JavaConverters._

object NaptimeRecordField extends StrictLogging {

  private[schema] def build(
      schemaMetadata: SchemaMetadata,
      recordDataSchema: RecordDataSchema,
      fieldName: String,
      namespace: Option[String],
      resourceName: ResourceName,
      currentPath: List[String]) = {

    Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
      name = FieldBuilder.formatName(fieldName),
      fieldType = getType(
        schemaMetadata,
        recordDataSchema,
        namespace,
        resourceName,
        currentPath :+ fieldName),
      resolve = context => {
        context.value.element.get(fieldName) match {
          case dataMap: DataMap =>
            context.value.copy(element = dataMap)
          case other: Any =>
            logger.warn(s"Expected DataMap but got $other")
            Value(null)
          case null =>
            Value(null)
        }

      }
    )
  }

  private[schema] def getType(
      schemaMetadata: SchemaMetadata,
      recordDataSchema: RecordDataSchema,
      namespace: Option[String],
      resourceName: ResourceName,
      currentPath: List[String]): ObjectType[SangriaGraphQlContext, DataMapWithParent] = {

    val formattedResourceName =
      NaptimeResourceUtils.formatResourceName(resourceName)
    ObjectType[SangriaGraphQlContext, DataMapWithParent](
      FieldBuilder.formatName(s"${formattedResourceName}_${recordDataSchema.getFullName}"),
      recordDataSchema.getDoc,
      fieldsFn = () => {
        val fields = recordDataSchema.getFields.asScala.map { field =>
          FieldBuilder.buildField(
            schemaMetadata,
            field,
            namespace,
            resourceName = resourceName,
            currentPath = currentPath)
        }.toList
        if (fields.isEmpty) {
          // TODO(bryan): Handle this case better
          EMPTY_FIELDS_FALLBACK
        } else {
          fields
        }
      }
    )
  }

  val EMPTY_FIELDS_FALLBACK = List(
    Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
      "ArbitraryField",
      StringType,
      resolve = context => null))

}
