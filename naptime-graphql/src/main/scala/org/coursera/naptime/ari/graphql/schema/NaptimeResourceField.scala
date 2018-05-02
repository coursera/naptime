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

import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.engine.Utilities
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.resolvers.DeferredNaptimeElement
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.RelationType
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ReverseRelationAnnotation
import sangria.schema.Context
import sangria.schema.DeferredValue
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.OptionType
import sangria.schema.Value

import scala.collection.JavaConverters._

object NaptimeResourceField extends StrictLogging {

  val COMPLEXITY_COST = 10.0D

  def build(
      schemaMetadata: SchemaMetadata,
      resourceName: ResourceName,
      fieldName: String,
      fieldRelation: Option[ReverseRelationAnnotation],
      currentPath: List[String])
    : Either[SchemaError, Field[SangriaGraphQlContext, DataMapWithParent]] = {

    (for {
      resource <- schemaMetadata.getResourceOpt(resourceName)
      resourceMergedType <- schemaMetadata.getSchema(resource)
    } yield {
      val arguments = resource.handlers
        .find(_.kind == HandlerKind.MULTI_GET)
        .map { handler =>
          NaptimeResourceUtils
            .generateHandlerArguments(handler)
            .filterNot(_.name == "ids")
        }
        .getOrElse(List.empty)

      getType(schemaMetadata, resourceName).right.map { fieldType =>
        Field.apply[SangriaGraphQlContext, DataMapWithParent, Any, Any](
          name = fieldName,
          fieldType = OptionType(fieldType),
          resolve = getResolver(resourceName, fieldName, fieldRelation, resourceMergedType),
          arguments = arguments,
          complexity = Some((_, _, childScore) => {
            COMPLEXITY_COST * childScore
          })
        )
      }
    }).getOrElse(Left(SchemaNotFound(resourceName)))
  }

  private[schema] def getType(
      schemaMetadata: SchemaMetadata,
      resourceName: ResourceName): Either[SchemaError, OptionType[DataMapWithParent]] = {
    val resource = schemaMetadata.getResource(resourceName)
    schemaMetadata
      .getSchema(resource)
      .map { schema =>
        val resourceObjectType = OptionType(
          ObjectType[SangriaGraphQlContext, DataMapWithParent](
            name = formatResourceName(resource),
            description = schema.getDoc,
            fieldsFn = () => {
              Option(schema.getFields)
                .map(_.asScala.map { field =>
                  FieldBuilder.buildField(
                    schemaMetadata,
                    field,
                    Option(schema.getNamespace),
                    resourceName = ResourceName.fromResource(resource),
                    currentPath = List(field.getName))
                }.toList)
                .getOrElse(List.empty)
            }
          ))
        resourceObjectType
      }
      .toRight(SchemaNotFound(resourceName))
  }

  private[this] def getResolver(
      resourceName: ResourceName,
      fieldName: String,
      fieldRelationOpt: Option[ReverseRelationAnnotation],
      resourceMergedType: RecordDataSchema): FieldBuilder.ResolverType = {
    (context: Context[SangriaGraphQlContext, DataMapWithParent]) =>
      {

        // TODO(bryan): refactor all of this
        val extraArguments = fieldRelationOpt
          .map { fieldRelation =>
            NaptimeResourceUtils.interpolateArguments(context.value, fieldRelation)
          }
          .getOrElse {
            Set("ids" -> NaptimeResourceUtils.parseToJson(context.arg("id")))
          }
        val args = context.args.raw
          .mapValues(NaptimeResourceUtils.parseToJson)
          .toSet ++ extraArguments
        val idArg = args.find(_._1 == "ids").map(_._2)
        val nonIdArgs = args.filter(_._1 != "ids")

        val isForwardRelationButMissingId =
          (fieldRelationOpt.exists(_.relationType == RelationType.GET) ||
            // `fieldRelationOpt` empty: this is possible if we are attempting to get the resolver
            // for the root request. If we put in an empty string for the id, we do not want to make
            // an API call to the resource as Naptime will translate this to a GETALL behind the
            // scenes, which results in a very slow API returning non data.
            fieldRelationOpt.isEmpty) &&
            idArg.forall(Utilities.jsValueIsEmpty)

        if (isForwardRelationButMissingId) {
          Value(null)
        } else {
          DeferredValue(DeferredNaptimeElement(resourceName, idArg, nonIdArgs, resourceMergedType))
            .map {
              case Left(error) =>
                throw NaptimeResolveException(error)
              case Right(response) =>
                response.elements.headOption
                  .map { dataMapWithParent =>
                    dataMapWithParent.copy(sourceUrl = Some(response.url))
                  }
                  .orNull[DataMapWithParent]
            }(context.ctx.executionContext)
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
