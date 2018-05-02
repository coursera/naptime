package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.NamedDataSchema
import com.linkedin.data.schema.RecordDataSchema.{Field => RecordDataSchemaField}
import com.linkedin.data.schema.UnionDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.Schema
import sangria.schema.UnionType

import scala.collection.JavaConverters._

object NaptimeUnionField {

  val TYPED_DEFINITION_KEY = "typedDefinition"
  val NAMESPACE_KEY = "namespace"

  private[schema] def build(
      schemaMetadata: SchemaMetadata,
      unionDataSchema: UnionDataSchema,
      fieldName: String,
      namespace: Option[String],
      resourceName: ResourceName,
      currentPath: List[String]): Field[SangriaGraphQlContext, DataMapWithParent] = {
    Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
      name = fieldName,
      fieldType =
        getType(schemaMetadata, unionDataSchema, fieldName, namespace, resourceName, currentPath),
      resolve = context => {
        Option(context.value.element.getDataMap(fieldName))
          .map { nestedDataMap =>
            context.value.copy(element = nestedDataMap)
          }
          .orNull[DataMapWithParent]
      }
    )
  }

  def getTypedDefinition(unionDataSchema: UnionDataSchema): Option[Map[String, String]] = {
    Option(unionDataSchema.getProperties.get(TYPED_DEFINITION_KEY)).collect {
      case definitions: java.util.Map[String @unchecked, String @unchecked] =>
        definitions.asScala.toMap
    }
  }

  private[schema] def getType(
      schemaMetadata: SchemaMetadata,
      unionDataSchema: UnionDataSchema,
      fieldName: String,
      namespace: Option[String],
      resourceName: ResourceName,
      currentPath: List[String]): UnionType[SangriaGraphQlContext] = {

    val objects = unionDataSchema.getTypes.asScala.map { unionMember =>
      val typedDefinitions =
        getTypedDefinition(unionDataSchema).getOrElse(Map[String, String]())

      val unionMemberKey = unionMember match {
        case _ if typedDefinitions.contains(unionMember.getUnionMemberKey) =>
          typedDefinitions(unionMember.getUnionMemberKey)
        case namedType: NamedDataSchema
            if typedDefinitions.contains(namedType.getName)
              && unionDataSchema.getProperties.get(NAMESPACE_KEY) == namedType.getNamespace =>
          typedDefinitions(namedType.getName)
        case _ => unionMember.getUnionMemberKey
      }

      val unionMemberFieldName = FieldBuilder.formatName(unionMemberKey)
      val subTypeField =
        FieldBuilder.buildField(
          schemaMetadata,
          new RecordDataSchemaField(unionMember),
          namespace,
          Some(unionMemberKey),
          resourceName = resourceName,
          currentPath = currentPath)

      val field =
        Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
          unionMemberFieldName,
          subTypeField.fieldType,
          resolve = context => {
            if (unionDataSchema.getProperties.containsKey(TYPED_DEFINITION_KEY)) {
              Option(context.value.element.getDataMap("definition"))
                .map(element => context.value.copy(element = element))
                .getOrElse(subTypeField.resolve(context))
            } else {
              subTypeField.resolve(context)
            }
          }
        )
      val formattedResourceName =
        NaptimeResourceUtils.formatResourceName(resourceName)

      ObjectType[SangriaGraphQlContext, DataMapWithParent](
        name = FieldBuilder.formatName(s"$formattedResourceName/${unionMemberKey}Member"),
        fields = List(field))
    }.toList
    val unionName = buildFullyQualifiedName(resourceName, fieldName)
    new UnionType(unionName, None, objects) {
      // write a custom type mapper to use field names to determine the union member type
      override def typeOf[Ctx](value: Any, schema: Schema[Ctx, _]): Option[ObjectType[Ctx, _]] = {
        (if (unionDataSchema.getProperties.containsKey(TYPED_DEFINITION_KEY)) {
           for {
             element <- Option(value.asInstanceOf[DataMapWithParent].element)
             typeName <- Option(element.getString("typeName"))
             matchingObject <- objects.find(_.fieldsByName.keySet.contains(typeName))
           } yield {
             matchingObject
           }
         } else {
           val typedValue = value.asInstanceOf[DataMapWithParent]
           objects.find { obj =>
             val formattedMemberNames = typedValue.element.keySet.asScala
               .flatMap(key => Option(key))
               .map(FieldBuilder.formatName)
             obj.fieldsByName.keySet.intersect(formattedMemberNames).nonEmpty
           }
         }).map(_.asInstanceOf[ObjectType[Ctx, DataMapWithParent]])
      }
    }
  }

  def buildFullyQualifiedName(resourceName: ResourceName, fieldName: String): String = {
    FieldBuilder.formatName(s"${NaptimeResourceUtils.formatResourceName(resourceName)}.$fieldName")
  }

}
