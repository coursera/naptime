package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.NamedDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.RecordDataSchema.{Field => RecordDataSchemaField}
import com.linkedin.data.schema.UnionDataSchema
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.Schema
import sangria.schema.UnionType

import scala.collection.JavaConverters._

object NaptimeUnionField {

  val TYPED_DEFINITION_KEY = "typedDefinition"

  private[schema] def build(
      schemaMetadata: SchemaMetadata,
      unionDataSchema: UnionDataSchema,
      fieldName: String,
      namespace: Option[String],
      resourceName: String): Field[SangriaGraphQlContext, DataMap] = {
    Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
      name = fieldName,
      fieldType = getType(schemaMetadata, unionDataSchema, fieldName, namespace, resourceName),
      resolve = context => {
        if (unionDataSchema.getProperties.containsKey(TYPED_DEFINITION_KEY)) {
          val definition = context.value.getDataMap(fieldName).getDataMap("definition")
          val typeName = context.value.getDataMap(fieldName).getString("typeName")
          new DataMap(Map(typeName -> definition).asJava)
        } else {
          context.value.getDataMap(fieldName)
        }
      })
  }

  private[schema] def getType(
      schemaMetadata: SchemaMetadata,
      unionDataSchema: UnionDataSchema,
      fieldName: String,
      namespace: Option[String],
      resourceName: String): UnionType[SangriaGraphQlContext] = {

    val objects = unionDataSchema.getTypes.asScala.map { subType =>

      val typedDefinitions = Option(unionDataSchema.getProperties.get(TYPED_DEFINITION_KEY)).collect {
        case definitions: java.util.Map[String @unchecked, String @unchecked] => definitions.asScala
      }.getOrElse(Map[String, String]())

      val unionMemberKey = subType match {
        case sType if typedDefinitions.contains(sType.getUnionMemberKey) =>
          typedDefinitions(sType.getUnionMemberKey)
        case sType: NamedDataSchema if typedDefinitions.contains(sType.getName) =>
          typedDefinitions(sType.getName)
        case sType => sType.getUnionMemberKey
      }

      val unionMemberFieldName = FieldBuilder.formatName(unionMemberKey)
      val subTypeField = FieldBuilder.buildField(
        schemaMetadata,
        new RecordDataSchemaField(subType),
        namespace,
        Some(unionMemberKey),
        resourceName = resourceName)

      val field = Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
        unionMemberFieldName,
        subTypeField.fieldType,
        resolve = subTypeField.resolve)

      ObjectType[SangriaGraphQlContext, DataMap](
        name = FieldBuilder.formatName(s"$resourceName/${unionMemberKey}Member"),
        fields = List(field))
    }.toList
    val unionName = buildFullyQualifiedName(resourceName, fieldName)
    new UnionType(unionName, None, objects) {
      // write a custom type mapper to use field names to determine the union member type
      override def typeOf[Ctx](value: Any, schema: Schema[Ctx, _]): Option[ObjectType[Ctx, _]] = {
        val typedValue = value.asInstanceOf[DataMap]
        objects.find { obj =>
          val formattedMemberNames = typedValue.keySet.asScala
            .flatMap(key => Option(key))
            .map(FieldBuilder.formatName)
          obj.fieldsByName.keySet.intersect(formattedMemberNames).nonEmpty
        }.map(_.asInstanceOf[ObjectType[Ctx, DataMap]])
      }
    }
  }

  def buildFullyQualifiedName(namespace: String, fieldName: String): String = {
    FieldBuilder.formatName(s"$namespace.$fieldName")
  }


}
