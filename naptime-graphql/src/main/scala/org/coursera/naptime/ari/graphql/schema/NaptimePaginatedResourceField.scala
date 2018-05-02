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

import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.engine.Utilities
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.resolvers.DeferredNaptimeRequest
import org.coursera.naptime.ari.graphql.resolvers.NaptimeResponse
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.RelationType
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ReverseRelationAnnotation
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import sangria.schema.DeferredValue
import sangria.schema.Field
import sangria.schema.LeafAction
import sangria.schema.ListType
import sangria.schema.ObjectType
import sangria.schema.OptionType
import sangria.schema.PartialValue
import sangria.schema.Value

object NaptimePaginatedResourceField extends StrictLogging {

  val COMPLEXITY_COST = 10.0D

  def build(
      schemaMetadata: SchemaMetadata,
      resourceName: ResourceName,
      fieldName: String,
      handlerOverride: Option[Handler] = None,
      fieldRelationOpt: Option[ReverseRelationAnnotation],
      currentPath: List[String])
    : Either[SchemaError, Field[SangriaGraphQlContext, DataMapWithParent]] = {
    (for {
      resource <- schemaMetadata.getResourceOpt(resourceName)
      resourceMergedType <- schemaMetadata.getSchema(resource)
    } yield {
      val handlerOpt = (handlerOverride, fieldRelationOpt) match {
        case (Some(handler), _) =>
          Right(handler)
        case (None, Some(annotation)) =>
          annotation.relationType match {
            case RelationType.FINDER =>
              annotation.arguments
                .get("q")
                .flatMap { finderName =>
                  resource.handlers.find(_.name == finderName)
                }
                .toRight {
                  MissingQParameterOnFinderRelation(resourceName, fieldName)
                }

            case RelationType.MULTI_GET =>
              resource.handlers.find(_.kind == HandlerKind.MULTI_GET).toRight {
                HasForwardRelationButMissingMultiGet(resourceName, fieldName)
              }

            case RelationType.GET | RelationType.SINGLE_ELEMENT_FINDER | RelationType.$UNKNOWN =>
              Left(
                UnhandledSchemaError(
                  resourceName,
                  s"Cannot use a paginated field for a single-element relationship: $fieldName"))
          }
        case _ =>
          resource.handlers.find(_.kind == HandlerKind.MULTI_GET).toRight {
            HasForwardRelationButMissingMultiGet(resourceName, fieldName)
          }
      }

      handlerOpt.right.map { handler =>
        val providedArguments =
          fieldRelationOpt.map(_.arguments.keySet).getOrElse(Set[String]())

        val arguments = NaptimeResourceUtils
          .generateHandlerArguments(handler, includePagination = true)
          .filterNot(_.name == "ids")
          .filterNot(arg => providedArguments.contains(arg.name))
        Field.apply[SangriaGraphQlContext, DataMapWithParent, NaptimeResponse, Any](
          name = fieldName,
          fieldType = OptionType(getType(schemaMetadata, resourceName, fieldName)),
          resolve = context => {

            val extraArguments = fieldRelationOpt
              .map { fieldRelation =>
                NaptimeResourceUtils.interpolateArguments(context.value, fieldRelation)
              }
              .getOrElse {
                handler.kind match {
                  case HandlerKind.FINDER => Set(("q", JsString(handler.name)))
                  case _                  => Set.empty
                }
              }

            val args = context.args.raw
              .mapValues(NaptimeResourceUtils.parseToJson)
              .toSet ++
              extraArguments

            val hasIds = fieldRelationOpt.exists(_.relationType == RelationType.MULTI_GET) ||
              fieldRelationOpt.exists(_.relationType == RelationType.GET)
            val (updatedArgs, paginationOverride, isEmpty) = if (hasIds) {
              val startOption =
                context.arg(NaptimePaginationField.startArgument)
              val limit = context.arg(NaptimePaginationField.limitArgument)
              val ids = args
                .find(_._1 == "ids")
                .map(_._2)
                .collect {
                  case JsArray(i)     => i
                  case value: JsValue => List(value)
                }
                .getOrElse(List.empty)

              val paginatedIds = JsArray {
                startOption
                  .map(s => ids.dropWhile(_ != NaptimeResourceUtils.parseToJson(s)))
                  .getOrElse(ids)
                  .take(limit)
              }

              val idsAfterStart = startOption
                .map(s => ids.dropWhile(_ != NaptimeResourceUtils.parseToJson(s)))
                .getOrElse(ids)
              val next =
                idsAfterStart.drop(limit).headOption.map(Utilities.stringifyArg)

              val paginationResponse =
                ResponsePagination(next, Some(ids.size.toLong))

              if (paginatedIds.value.isEmpty || Utilities.jsValueIsEmpty(paginatedIds)) {
                (args, Some(paginationResponse), true)
              } else {
                (
                  args.filterNot(_._1 == "ids") + ("ids" -> paginatedIds),
                  Some(paginationResponse),
                  false)
              }
            } else {
              (args, None, false)
            }

            if (isEmpty) {
              Value(NaptimeResponse(List.empty, None, "No ids found."))
            } else {
              DeferredValue(
                DeferredNaptimeRequest(
                  resourceName,
                  updatedArgs,
                  resourceMergedType,
                  paginationOverride))
                .map {
                  case Left(error) =>
                    NaptimeResponse(
                      List.empty,
                      None,
                      error.url,
                      error.status,
                      Some(error.errorMessage))
                  case Right(response) =>
                    val limit =
                      context.arg(NaptimePaginationField.limitArgument)
                    response.copy(elements = response.elements.take(limit))
                }(context.ctx.executionContext)
            }
          },
          complexity = Some((ctx, args, childScore) => {
            // API calls should count 10x, and we take limit into account because there could be
            // N child API calls for each response here
            val limit = args.arg(NaptimePaginationField.limitArgument)
            Math.max(limit / 10, 1) * COMPLEXITY_COST * childScore
          }),
          arguments = arguments
        )
      }
    }).getOrElse(Left(SchemaNotFound(resourceName)))
  }

  private[this] def getType(
      schemaMetadata: SchemaMetadata,
      resourceName: ResourceName,
      fieldName: String): ObjectType[SangriaGraphQlContext, NaptimeResponse] = {

    val resource = schemaMetadata.getResourceOpt(resourceName).getOrElse {
      throw SchemaGenerationException(s"Cannot find schema for $resourceName")
    }
    val schema = schemaMetadata.getSchema(resource).getOrElse {
      throw SchemaGenerationException(s"Cannot find schema for $resourceName")
    }

    ObjectType[SangriaGraphQlContext, NaptimeResponse](
      name = formatPaginatedResourceName(resource),
      fieldsFn = () => {
        NaptimeResourceField
          .getType(schemaMetadata, resourceName)
          .right
          .toOption
          .map {
            elementType =>
              val listType = ListType(elementType)
              List(
                Field.apply[SangriaGraphQlContext, NaptimeResponse, Any, Any](
                  name = "elements",
                  fieldType = listType,
                  resolve = _.value.elements),
                Field.apply[SangriaGraphQlContext, NaptimeResponse, Any, Any](
                  name = "paging",
                  fieldType = NaptimePaginationField.getField(resourceName, fieldName),
                  resolve = (ctx) => {
                    val pagination =
                      ctx.value.pagination.getOrElse(ResponsePagination.empty)
                    Value(pagination)
                  }
                )
              )
          }
          .getOrElse(List.empty)
      }
    )
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
