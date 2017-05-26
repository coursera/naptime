/*
 * Copyright 2016 Coursera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
      resourceName: ResourceName,
      fieldName: String,
      idExtractor: Option[IdExtractor] = None):
    Either[SchemaError, Field[SangriaGraphQlContext, DataMap]] = {

    schemaMetadata.getResourceOpt(resourceName).map { resource =>
      val arguments = resource.handlers.find(_.kind == HandlerKind.MULTI_GET).map { handler =>
        NaptimeResourceUtils
          .generateHandlerArguments(handler, includePagination = false)
          .filterNot(_.name == "ids")
      }.getOrElse(List.empty)

      getType(schemaMetadata, resourceName).right.map { fieldType =>
        Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
          name = fieldName,
          fieldType = fieldType,
          resolve = getResolver(resourceName, fieldName, idExtractor),
          arguments = arguments,
          complexity = Some(
            (ctx, args, childScore) => {
              COMPLEXITY_COST * childScore
            }))
      }
    }.getOrElse(Left(SchemaNotFound(resourceName)))
  }

  private[schema] def getType(
      schemaMetadata: SchemaMetadata,
      resourceName: ResourceName): Either[SchemaError, OptionType[DataMap]] = {
    val resource = schemaMetadata.getResource(resourceName)
    schemaMetadata.getSchema(resource).map { schema =>

      val resourceObjectType = OptionType(
        ObjectType[SangriaGraphQlContext, DataMap](
          name = formatResourceName(resource),
          description = schema.getDoc,
          fieldsFn = () => {
            Option(schema.getFields).map(
              _.asScala.map { field =>
                generateField(field, schemaMetadata, resource, schema)
              }.toList).getOrElse(List.empty).flatten
          }))
      resourceObjectType
    }.toRight(SchemaNotFound(resourceName))
  }

  private[schema] def generateField(
      field: RecordDataSchema.Field,
      schemaMetadata: SchemaMetadata,
      resource: Resource,
      schema: RecordDataSchema): Option[Field[SangriaGraphQlContext, DataMap]] = {

    val forwardRelationOption = field.getProperties.asScala
      .get(Relations.PROPERTY_NAME)
      .map(_.toString)

    if (forwardRelationOption.isDefined) {
      (for {
        resourceName <- ResourceName.parse(forwardRelationOption.get)
        relatedResource <- schemaMetadata.getResourceOpt(resourceName)
      } yield {
        if (relatedResource.handlers.exists(_.kind == HandlerKind.MULTI_GET)) {
          Some(FieldBuilder.buildField(schemaMetadata, field, Option(schema.getNamespace),
            resourceName = ResourceName.fromResource(resource)))
        } else {
          logger.warn(s"Unable to build field for forward relation from " +
            s"${resource.name} -> ${forwardRelationOption.get} due to the lack of a MULTI_GET handler")
          None
        }
      }).getOrElse(None)
    } else {
      Some(FieldBuilder.buildField(schemaMetadata, field, Option(schema.getNamespace),
        resourceName = ResourceName.fromResource(resource)))
    }
  }

  private[this] def getResolver(
      resourceName: ResourceName,
      fieldName: String,
      idExtractor: Option[IdExtractor]): FieldBuilder.ResolverType = {
    (context: Context[SangriaGraphQlContext, DataMap]) => {
      val id = idExtractor.map(_.apply(context)).getOrElse {
        context.value.get(fieldName)
      }
      if (id == null) {
        Value[SangriaGraphQlContext, Any](null)
      } else {
        context.ctx.response.data.get(resourceName)
          .flatMap { resourceSet =>
            resourceSet
              .find(resource => id == resource._1)
              .map(optionalElement => Value[SangriaGraphQlContext, Any](optionalElement._2))
          }.getOrElse {
            throw NotFoundException(s"Cannot find ${resourceName.identifier}/$id")
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
