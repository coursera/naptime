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

package org.coursera.naptime.ari.graphql

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.schema.NaptimePaginatedResourceField
import org.coursera.naptime.ari.graphql.schema.NaptimePaginationField
import org.coursera.naptime.ari.graphql.schema.NaptimeResourceField
import org.coursera.naptime.ari.graphql.schema.SchemaMetadata
import org.coursera.naptime.schema.ArbitraryValue
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.Resource
import sangria.marshalling.FromInput
import sangria.schema.Argument
import sangria.schema.BigDecimalType
import sangria.schema.BooleanType
import sangria.schema.Context
import sangria.schema.FloatType
import sangria.schema.InputType
import sangria.schema.IntType
import sangria.schema.ListInputType
import sangria.schema.LongType
import sangria.schema.Schema
import sangria.schema.StringType
import sangria.schema.Value
import sangria.marshalling.FromInput._
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.OptionInputType
import sangria.schema.OptionType

class SangriaGraphQlSchemaBuilder(
    resources: Set[Resource],
    schemas: Map[String, RecordDataSchema])
  extends StrictLogging {

  val schemaMetadata = SchemaMetadata(resources, schemas)

  /**
    * Generates a GraphQL schema for the provided set of resources to this class
    * Returns a "root" object that has one field available for each Naptime Resource provided.*
    *
    * @return a Sangria GraphQL Schema with all resources defined
    */
  def generateSchema(): Schema[SangriaGraphQlContext, DataMap] = {
    val topLevelResourceObjects = for {
      resource <- resources
      resourceObject <- (try {
        val resourceName = ResourceName(
          resource.name, resource.version.getOrElse(0L).toInt).identifier
        generateLookupTypeForResource(resourceName)
      } catch {
        case e: Throwable => None
      }).toList if resourceObject.fields.nonEmpty
    } yield {
      Field.apply[SangriaGraphQlContext, DataMap, DataMap, Any](
        formatResourceTopLevelName(resource),
        resourceObject,
        resolve = (context: Context[SangriaGraphQlContext, DataMap]) => {
          Value(new DataMap())
        })
    }

    val dedupedResources = topLevelResourceObjects.groupBy(_.name).map(_._2.head).toList
    val rootObject = ObjectType[SangriaGraphQlContext, DataMap](
      name = "root",
      description = "Top-level accessor for Naptime resources",
      fields = dedupedResources)
    Schema(rootObject)
  }

  /**
    * Generates an object-type for a given resource name, with each field on the merged output
    * schema available on this object-type.
    *
    * @param resourceName String name of the resource (i.e. 'courses.v1')
    * @return ObjectType for the resource
    */
  def generateLookupTypeForResource(resourceName: String): Option[ObjectType[SangriaGraphQlContext, DataMap]] = {

    try {
      val resource = schemaMetadata.getResource(resourceName)
      val fields = resource.handlers.flatMap { handler =>
        handler.kind match {
          // We want to make sure that if a resource has a GET handler, it also has a MULTI_GET handler
          case HandlerKind.GET if resource.handlers.exists(_.kind == HandlerKind.MULTI_GET) =>
            generateGetHandler(resource, handler)
          case HandlerKind.GET_ALL | HandlerKind.MULTI_GET | HandlerKind.FINDER =>
            generateListHandler(resource, handler)
          case _ => None
        }
      }.toList
      if (fields.nonEmpty) {
        val resourceObjectType = ObjectType[SangriaGraphQlContext, DataMap](
          name = formatResourceTopLevelName(resource),
          fieldsFn = () => fields)
        Some(resourceObjectType)
      } else {
        logger.warn(s"No handlers available for resource $resourceName")
        None
      }
    } catch {
      case e: Throwable =>
        logger.error(s"Unknown error when generating resource: ${e.getMessage}")
        None
    }
  }

  def generateGetHandler(
      resource: Resource,
      handler: Handler): Option[Field[SangriaGraphQlContext, DataMap]] = {
    val arguments = SangriaGraphQlSchemaBuilder.generateHandlerArguments(handler)
    val resourceName = ResourceName(resource.name, resource.version.getOrElse(0L).toInt)

    val idExtractor = (context: Context[SangriaGraphQlContext, DataMap]) => {
      val id = context.arg[AnyRef]("id")
      id match {
        case idOpt: Option[Any] => idOpt.orNull
        case _ => id
      }
    }

    NaptimeResourceField.build(
      schemaMetadata = schemaMetadata,
      resourceName = resourceName.identifier,
      fieldName = "get",
      idExtractor = Some(idExtractor))
      .map { field =>
        field.copy(arguments = arguments ++ field.arguments)
      }
  }

  def generateListHandler(
      resource: Resource,
      handler: Handler): Option[Field[SangriaGraphQlContext, DataMap]] = {
    val resourceName = ResourceName(resource.name, resource.version.getOrElse(0L).toInt)
    val arguments = SangriaGraphQlSchemaBuilder.generateHandlerArguments(handler)


    val fieldName = handler.kind match {
      case HandlerKind.FINDER => handler.name
      case HandlerKind.GET_ALL => "getAll"
      case HandlerKind.MULTI_GET => "multiGet"
      case _ => "error"
    }

    NaptimePaginatedResourceField.build(
      schemaMetadata = schemaMetadata,
      resourceName = resourceName.identifier,
      fieldName = fieldName,
      handlerOverride = Some(handler),
      fieldRelation = None).map { field =>

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

object SangriaGraphQlSchemaBuilder extends StrictLogging {

  val PAGINATION_ARGUMENT_NAMES = NaptimePaginationField.paginationArguments.map(_.name)

  def generateHandlerArguments(handler: Handler, includePagination: Boolean = false): List[Argument[Any]] = {
    val baseParameters = handler.parameters
      .filterNot(parameter => PAGINATION_ARGUMENT_NAMES.contains(parameter.name))
      .map { parameter =>
        val tpe = parameter.`type`
        val inputType = scalaTypeToSangria(tpe)
        val fromInputType = scalaTypeToFromInput(tpe)
        val (optionalInputType, optionalFromInputType: FromInput[Any]) = (inputType, parameter.required) match {
          case (_: OptionInputType[Any], _) => (inputType, fromInputType)
          case (_, false) => (OptionInputType(inputType), FromInput.optionInput(fromInputType))
          case (_, true) => (inputType, fromInputType)
        }
        Argument(
          name = parameter.name,
          argumentType = optionalInputType)(optionalFromInputType, implicitly).asInstanceOf[Argument[Any]]
      }.toList
    val paginationParameters = if (includePagination) {
      NaptimePaginationField.paginationArguments
    } else {
      List.empty
    }
    (baseParameters ++ paginationParameters)
      .groupBy(_.name)
      .map(_._2.head.asInstanceOf[Argument[Any]])
      .toList
  }

  def scalaTypeToSangria(typeName: String): InputType[Any] = {
    import sangria.marshalling.FromInput.seqInput
    import sangria.marshalling.FromInput.coercedScalaInput

    val listPattern = "(Set|List|Seq|immutable.Seq)\\[(.*)\\]".r
    val optionPattern = "(Option)\\[(.*)\\]".r
    // TODO(bryan): Fill in the missing types here
    typeName match {
      case listPattern(_, innerType) => ListInputType(scalaTypeToSangria(innerType))
      case optionPattern(_, innerType) => OptionInputType(scalaTypeToSangria(innerType))
      case "string" | "String" => StringType
      case "int" | "Int" => IntType
      case "long" | "Long" => LongType
      case "float" | "Float" => FloatType
      case "decimal" | "Decimal" => BigDecimalType
      case "boolean" | "Boolean" => BooleanType
      case _ => {
        logger.warn(s"could not parse type from $typeName")
        StringType
      }
    }
  }

  def scalaTypeToFromInput(typeName: String): FromInput[Any] = {
    import sangria.marshalling.FromInput.seqInput
    import sangria.marshalling.FromInput.coercedScalaInput

    val listPattern = "(set|list|seq|immutable.Seq)\\[(.*)\\]".r
    val optionPattern = "(Option)\\[(.*)\\]".r

    // TODO(bryan): Fix all of this :)
    typeName.toLowerCase match {
      case listPattern(outerType, innerType) =>
        val listType = scalaTypeToFromInput(innerType)
        sangria.marshalling.FromInput.seqInput(listType).asInstanceOf[FromInput[Any]]
      case "string" | "int" | "long" | "float" | "decimal" | "boolean" =>
        sangria.marshalling.FromInput.coercedScalaInput.asInstanceOf[FromInput[Any]]
      case _ =>
        sangria.marshalling.FromInput.coercedScalaInput.asInstanceOf[FromInput[Any]]
    }
  }
}
