package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.schema.Resource
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.ListType
import sangria.schema.ObjectType
import sangria.schema.Value

import scala.collection.JavaConverters._

object NaptimePaginatedResourceField {

  def build(
      schemaMetadata: SchemaMetadata,
      resourceName: String,
      fieldName: String): Field[SangriaGraphQlContext, DataMap] = {
    Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
      name = fieldName,
      fieldType = getType(schemaMetadata, resourceName, fieldName),
      resolve = getResolver(resourceName, fieldName))
  }

  //TODO(bryan): add arguments for pagination in here
  private[this] def getType(
      schemaMetadata: SchemaMetadata,
      resourceName: String,
      fieldName: String): ObjectType[SangriaGraphQlContext, DataMap] = {

    val resource = schemaMetadata.getResource(resourceName)
    schemaMetadata.getSchema(resource).getOrElse {
      throw new SchemaGenerationException(s"Cannot find schema for $resourceName")
    }

    ObjectType[SangriaGraphQlContext, DataMap](
      name = formatPaginatedResourceName(resource),
      fieldsFn = () => {
        val elementType = NaptimeResourceField.getType(schemaMetadata, resourceName)
        val listType = ListType(elementType)
        List(Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = "elements",
          fieldType = listType,
          resolve = context => context.value))
      })

  }

  private[this] def getResolver(
      resourceName: String,
      fieldName: String): FieldBuilder.ResolverType = {
    (context: Context[SangriaGraphQlContext, DataMap]) => {
      // TODO(bryan): Figure out pagination here
      val parsedResourceName = ResourceName.parse(resourceName).getOrElse {
        throw new SchemaExecutionException(s"Cannot parse resource name from $resourceName")
      }
      println(context.ctx.response)
      val connection = context.ctx.response.data.get(parsedResourceName).map { objects =>
        val ids = Option(context.value).map { parentElement =>
          // Nested Request
          parentElement.getDataList(fieldName).asScala
        }.getOrElse {
          // Top-Level Request
          context.ctx.response.topLevelResponses.find { case (topLevelRequest, _) =>
            topLevelRequest.resource.identifier == resourceName &&
              topLevelRequest.selection.alias == context.astFields.headOption.flatMap(_.alias)
          }.map(_._2.ids.asScala).getOrElse(List.empty)
        }
        objects.collect {
          case (id, element) if ids.contains(id) => element
        }.toSeq
      }.getOrElse(List.empty)

      Value[SangriaGraphQlContext, Any](connection)
    }
  }

  /**
    * Converts a resource name to a GraphQL compatible name. (i.e. 'courses.v1' to 'CoursesV1')
    *
    * @param resource Naptime resource
    * @return GraphQL-safe resource name
    */
  private[this] def formatPaginatedResourceName(resource: Resource): String = {
    s"${resource.name.capitalize}V${resource.version.getOrElse(0)}Connection"
  }

}
