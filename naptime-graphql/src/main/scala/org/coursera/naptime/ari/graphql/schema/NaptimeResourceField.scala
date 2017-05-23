package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.Types.Relations
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.SangriaGraphQlSchemaBuilder
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.Resource
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.OptionType
import sangria.schema.Value

import scala.collection.JavaConverters._

object NaptimeResourceField extends StrictLogging {

  type IdExtractor = (Context[SangriaGraphQlContext, DataMap]) => Any

  val COMPLEXITY_COST = 10.0D

  def build(
      schemaMetadata: SchemaMetadata,
      resourceName: String,
      fieldName: String,
      idExtractor: Option[IdExtractor] = None): Option[Field[SangriaGraphQlContext, DataMap]] = {

    schemaMetadata.getResourceOpt(resourceName).map { resource =>
      val arguments = resource.handlers.find(_.kind == HandlerKind.MULTI_GET).map { handler =>
        SangriaGraphQlSchemaBuilder
          .generateHandlerArguments(handler, includePagination = false)
          .filterNot(_.name == "ids")
      }.getOrElse(List.empty)

      Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
        name = fieldName,
        fieldType = getType(schemaMetadata, resourceName),
        resolve = getResolver(resourceName, fieldName, idExtractor),
        arguments = arguments,
        complexity = Some(
          (ctx, args, childScore) => {
            COMPLEXITY_COST * childScore
          }))
    }
  }

  private[schema] def getType(
      schemaMetadata: SchemaMetadata,
      resourceName: String): OptionType[DataMap] = {
    val resource = schemaMetadata.getResource(resourceName)
    val schema = schemaMetadata.getSchema(resource).getOrElse {
      throw SchemaGenerationException(s"Cannot find schema for $resourceName")
    }

    val resourceObjectType = OptionType(ObjectType[SangriaGraphQlContext, DataMap](
      name = formatResourceName(resource),
      description = schema.getDoc,
      fieldsFn = () => {
        Option(schema.getFields).map(_.asScala.map { field =>
          generateField(field, schemaMetadata, resource, schema)
        }.toList).getOrElse(List.empty).flatten
      }))
    resourceObjectType
  }

  private[schema] def generateField(
      field: RecordDataSchema.Field,
      schemaMetadata: SchemaMetadata,
      resource: Resource,
      schema: RecordDataSchema): Option[Field[SangriaGraphQlContext, DataMap]] = {
    val forwardRelationOption = field.getProperties.asScala.get(Relations.PROPERTY_NAME).map(_.toString)
    if (forwardRelationOption.isDefined) {
      val relatedResource = schemaMetadata.getResource(forwardRelationOption.get)
      if (relatedResource.handlers.exists(_.kind == HandlerKind.MULTI_GET)) {
        Some(FieldBuilder.buildField(schemaMetadata, field, Option(schema.getNamespace),
          resourceName = formatResourceName(resource)))
      } else {
        logger.warn(s"Unable to build field for forward relation from " +
          s"${resource.name} -> ${forwardRelationOption.get} due to the lack of a MULTI_GET handler")
        None
      }
    } else {
      Some(FieldBuilder.buildField(schemaMetadata, field, Option(schema.getNamespace),
        resourceName = formatResourceName(resource)))
    }
  }

  private[this] def getResolver(
      resourceName: String,
      fieldName: String,
      idExtractor: Option[IdExtractor]): FieldBuilder.ResolverType = {
    (context: Context[SangriaGraphQlContext, DataMap]) => {
      val id = idExtractor.map(_.apply(context)).getOrElse {
        context.value.get(fieldName)
      }
      if (id == null) {
        Value[SangriaGraphQlContext, Any](null)
      } else {
        val parsedResourceName = ResourceName.parse(resourceName).getOrElse {
          throw SchemaExecutionException(s"Cannot parse resource name from $resourceName")
        }
        context.ctx.response.data.get(parsedResourceName)
          .flatMap { resourceSet =>
            resourceSet
              .find(resource => id == resource._1)
              .map(optionalElement => Value[SangriaGraphQlContext, Any](optionalElement._2))
          }.getOrElse {
            throw NotFoundException(s"Cannot find $resourceName/$id")
          }
      }
    }
  }

  /**
    * Converts a resource name to a GraphQL compatible name. (i.e. 'courses.v1' to 'CoursesV1')
    *
    * @param resource Naptime resource
    * @return GraphQL-safe resource name
    */
  private[this] def formatResourceName(resource: Resource): String = {
    s"${resource.name.capitalize}V${resource.version.getOrElse(0)}"
  }

}
