package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema.{Field => RecordDataSchemaField}
import com.linkedin.data.schema.UnionDataSchema
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.Schema
import sangria.schema.UnionType

import scala.collection.JavaConverters._

object NaptimeUnionField {

  private[schema] def build(
      schemaMetadata: SchemaMetadata,
      unionDataSchema: UnionDataSchema,
      fieldName: String,
      namespace: Option[String]): Field[SangriaGraphQlContext, DataMap] = {
    Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
      name = fieldName,
      fieldType = getType(schemaMetadata, unionDataSchema, fieldName, namespace),
      resolve = context => context.value.getDataMap(fieldName))
  }

  private[schema] def getType(
      schemaMetadata: SchemaMetadata,
      unionDataSchema: UnionDataSchema,
      fieldName: String,
      namespace: Option[String]): UnionType[SangriaGraphQlContext] = {

    val objects = unionDataSchema.getTypes.asScala.map { subType =>
      val fieldName = FieldBuilder.formatName(subType.getUnionMemberKey)
      val subTypeField = FieldBuilder.buildField(
        schemaMetadata,
        new RecordDataSchemaField(subType),
        namespace,
        Some(fieldName))

      val field = Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
        FieldBuilder.formatName(subType.getUnionMemberKey),
        subTypeField.fieldType,
        resolve = subTypeField.resolve)
      ObjectType[SangriaGraphQlContext, DataMap](
        name = FieldBuilder.formatName(s"${subType.getUnionMemberKey}Member"),
        fields = List(field))
    }.toList
    val unionName = buildFullyQualifiedName(namespace.getOrElse(""), fieldName)
    new UnionType(unionName, None, objects) {
      // write a custom type mapper to use field names to determine the union member type
      override def typeOf[Ctx](value: Any, schema: Schema[Ctx, _]): Option[ObjectType[Ctx, _]] = {
        val typedValue = value.asInstanceOf[DataMap]
        objects.find { obj =>
          obj.fieldsByName.keySet.intersect(typedValue.keySet().asScala).nonEmpty
        }.map(_.asInstanceOf[ObjectType[Ctx, DataMap]])
      }
    }
  }

  def buildFullyQualifiedName(namespace: String, fieldName: String): String = {
    FieldBuilder.formatName(s"$namespace.$fieldName")
  }


}
