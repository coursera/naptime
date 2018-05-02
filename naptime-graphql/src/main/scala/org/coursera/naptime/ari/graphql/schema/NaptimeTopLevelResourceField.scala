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
import com.typesafe.scalalogging.StrictLogging
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.JsValue
import org.coursera.naptime.schema.Resource
import sangria.schema.Argument
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.ObjectType

import collection.JavaConverters._

object NaptimeTopLevelResourceField extends StrictLogging {

  private[this] val EMPTY_JS_VALUE =
    JsValue.build(new DataMap(), DataConversion.SetReadOnly)

  val MUTATION_HANDLERS: Set[HandlerKind] = Set(
    HandlerKind.ACTION,
    HandlerKind.CREATE,
    HandlerKind.DELETE,
    HandlerKind.PATCH,
    HandlerKind.UPSERT)

  /**
   * Generates an object-type for a given resource name, with each field on the merged output
   * schema available on this object-type.
   *
   * @param resource Resource to generate lookup type for
   * @return WithSchemaErrors[ObjectType] including the ObjectType for the resource,
   *         if we were able to generate it, and any errors generated while creating the type.
   */
  def generateLookupTypeForResource(resource: Resource, schemaMetadata: SchemaMetadata)
    : WithSchemaErrors[Option[ObjectType[SangriaGraphQlContext, DataMapWithParent]]] = {

    val resourceName = ResourceName.fromResource(resource)

    val fieldsAndErrors = resource.handlers
      .filterNot(handler => MUTATION_HANDLERS.contains(handler.kind))
      .map { handler =>
        handler.kind match {
          case HandlerKind.GET =>
            generateGetHandler(resource, handler, schemaMetadata)
          case HandlerKind.GET_ALL | HandlerKind.MULTI_GET | HandlerKind.FINDER =>
            generateListHandler(resource, handler, schemaMetadata)
          case unknownHandler: HandlerKind =>
            Left(UnknownHandlerType(resourceName, unknownHandler.name))
        }
      }
      .toList

    val fields = fieldsAndErrors.flatMap(_.right.toOption)
    val errors = SchemaErrors(fieldsAndErrors.flatMap(_.left.toOption))

    val description = resource.attributes
      .find(_.name == "doc")
      .map { attribute =>
        val data = attribute.value.getOrElse(EMPTY_JS_VALUE).data()
        data.keySet.asScala
          .map { key =>
            val valueStr =
              Option(data.get(key)).map(_.toString).getOrElse("???")
            s"$key -> $valueStr"
          }
          .mkString("Attributes:\n", "\n", "")
      }
      .getOrElse("???")

    if (fields.nonEmpty) {
      val resourceObjectType =
        ObjectType[SangriaGraphQlContext, DataMapWithParent](
          name = formatResourceTopLevelName(resource),
          fieldsFn = () => fields,
          description = description)
      WithSchemaErrors(Some(resourceObjectType), errors)
    } else {
      WithSchemaErrors(None, errors + NoHandlersAvailable(resourceName))
    }
  }

  private[this] def generateGetHandler(
      resource: Resource,
      handler: Handler,
      schemaMetadata: SchemaMetadata)
    : Either[SchemaError, Field[SangriaGraphQlContext, DataMapWithParent]] = {

    // We use MultiGets under the hood for all Gets,
    // so only add a Get handler if there's also a MultiGet available
    if (resource.handlers.exists(_.kind == HandlerKind.MULTI_GET)) {
      val arguments = NaptimeResourceUtils.generateHandlerArguments(handler)
      val resourceName = ResourceName.fromResource(resource)

      NaptimeResourceField
        .build(
          schemaMetadata = schemaMetadata,
          resourceName = resourceName,
          fieldName = "get",
          fieldRelation = None,
          currentPath = List.empty)
        .right
        .map { field =>
          val newArguments =
            field.arguments.filterNot(newArg => arguments.map(_.name).contains(newArg.name))
          logger.debug(s"existing arguments: ${arguments
            .map(_.name)}\tnewArguments: ${newArguments.map(_.name)}")
          field.copy(arguments = arguments ++ newArguments)
        }
    } else {
      Left(HasGetButMissingMultiGet(ResourceName.fromResource(resource)))
    }
  }

  private[this] def generateListHandler(
      resource: Resource,
      handler: Handler,
      schemaMetadata: SchemaMetadata)
    : Either[SchemaError, Field[SangriaGraphQlContext, DataMapWithParent]] = {

    val resourceName =
      ResourceName(resource.name, resource.version.getOrElse(0L).toInt)
    val arguments = NaptimeResourceUtils.generateHandlerArguments(handler)

    val fieldName = handler.kind match {
      case HandlerKind.FINDER    => handler.name
      case HandlerKind.GET_ALL   => "getAll"
      case HandlerKind.MULTI_GET => "multiGet"
      case _                     => "error"
    }

    NaptimePaginatedResourceField
      .build(
        schemaMetadata = schemaMetadata,
        resourceName = resourceName,
        fieldName = fieldName,
        handlerOverride = Some(handler),
        fieldRelationOpt = None,
        currentPath = List.empty
      )
      .right
      .map { field =>
        val mergedArguments = (field.arguments ++ arguments)
          .groupBy(_.name)
          .map(_._2.head)
          .map(_.asInstanceOf[Argument[Any]])
          .toList
        field.copy(arguments = mergedArguments)
      }
  }

  /**
   * Converts a resource name to a GraphQL compatible name. (i.e. 'courses.v1' to 'CoursesV1')
   *
   * @param resource Naptime resource
   * @return GraphQL-safe resource name
   */
  def formatResourceName(resource: Resource): String = {
    s"${resource.name.capitalize}V${resource.version.getOrElse(0)}"
  }

  /**
   * Converts a resource to a GraphQL top-level name. (i.e. 'courses.v1' to 'CoursesV1Resource')
   *
   * @param resource Naptime resource
   * @return GraphQL-safe top-level resource name
   */
  def formatResourceTopLevelName(resource: Resource): String = {
    s"${formatResourceName(resource)}Resource"
  }
}
