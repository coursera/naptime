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
import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.BooleanDataSchema
import com.linkedin.data.schema.BytesDataSchema
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.DoubleDataSchema
import com.linkedin.data.schema.EnumDataSchema
import com.linkedin.data.schema.FloatDataSchema
import com.linkedin.data.schema.IntegerDataSchema
import com.linkedin.data.schema.LongDataSchema
import com.linkedin.data.schema.MapDataSchema
import com.linkedin.data.schema.NullDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.StringDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.PaginationConfiguration
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.Resource
import sangria.marshalling.FromInput
import sangria.schema.Argument
import sangria.schema.BigDecimalType
import sangria.schema.BooleanType
import sangria.schema.Context
import sangria.schema.EnumType
import sangria.schema.EnumValue
import sangria.schema.FloatType
import sangria.schema.InputType
import sangria.schema.IntType
import sangria.schema.ListInputType
import sangria.schema.ListType
import sangria.schema.LongType
import sangria.schema.OptionType
import sangria.schema.OutputType
import sangria.schema.Schema
import sangria.schema.StringType
import sangria.schema.UnionType
import sangria.schema.Value
import sangria.marshalling.FromInput._
import sangria.relay.Connection
import sangria.relay.ConnectionArgs
import sangria.relay.GlobalId
import sangria.relay.Node
import sangria.schema.Field
import sangria.schema.IDType
import sangria.schema.ObjectType
import sangria.schema.OptionInputType
import schema.ResourceCompanion

import scala.collection.JavaConverters._

class SangriaGraphQlSchemaBuilder(
    resources: Set[Resource],
    schemas: Map[String, RecordDataSchema])
  extends StrictLogging {

  import SangriaGraphQlSchemaBuilder._

  /**
    * Generates a GraphQL schema for the provided set of resources to this class
    * Returns a "root" object that has one field available for each Naptime Resource provided.*
    *
    * @return a Sangria GraphQL Schema with all resources defined
    */
  def generateSchema(): Schema[SangriaGraphQlContext, DataMap] = {
    val topLevelResourceObjects = for {
      resource <- resources
      resourceObject <- generateLookupTypeForResource(ResourceCompanion.versionedName(resource)).toList
        if resourceObject.fields.nonEmpty
    } yield {
      Field.apply[SangriaGraphQlContext, DataMap, DataMap, Any](
        formatResourceTopLevelName(resource),
        resourceObject,
        resolve = (context: Context[SangriaGraphQlContext, DataMap]) => {
          Value(null)
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
  def generateObjectTypeForResource(resourceName: String): Option[ObjectType[SangriaGraphQlContext, DataMap]] = {
    try {
      val resource = getResource(resourceName)

      val schema = schemas.get(resource.mergedType).flatMap(Option(_)).getOrElse {
        throw new RuntimeException(s"Cannot find schema for ${resource.mergedType}")
      }

      val resourceObjectType = ObjectType[SangriaGraphQlContext, DataMap](
        name = formatResourceName(resource),
        fieldsFn = () => {
          val resourceFields = Option(schema.getFields).map(_.asScala.map { field =>
            generateField(field, Option(schema.getNamespace).getOrElse(""))
          }.toList).getOrElse(List.empty)
          List(ResourceNode.globalIdField) ++ resourceFields
        })
      Some(resourceObjectType)

    } catch {
      case e: Throwable =>
        logger.warn(s"Could not generate object type for resource $resourceName: ${e.getMessage}")
        None
    }
  }

  def generateObjectConnectionTypeForResource(
      resourceName: String): Option[ObjectType[SangriaGraphQlContext, Connection[DataMap]]] = {
    generateObjectTypeForResource(resourceName).map { resourceObjectType =>
      Connection.definition[SangriaGraphQlContext, Connection, DataMap](
        name = resourceObjectType.name,
        nodeType = resourceObjectType).connectionType
    }
  }

  def scalaTypeToSangria(typeName: String): InputType[Any] = {
    import sangria.marshalling.FromInput.seqInput
    import sangria.marshalling.FromInput.coercedScalaInput

    val listPattern = "(Set|List|Seq|immutable.Seq)\\[(.*)\\]".r
    val optionPattern = "(Option)\\[(.*)\\]".r
    // TODO(bryan): Fill in the missing types here
    typeName match {
      case listPattern(outerType, innerType) => ListInputType(scalaTypeToSangria(innerType))
      case optionPattern(outerType, innerType) => OptionInputType(scalaTypeToSangria(innerType))
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
        sangria.marshalling.FromInput.seqInput.asInstanceOf[FromInput[Any]]
      case "string" | "int" | "long" | "float" | "decimal" | "boolean" =>
        sangria.marshalling.FromInput.coercedScalaInput.asInstanceOf[FromInput[Any]]
      case _ =>
        sangria.marshalling.FromInput.coercedScalaInput.asInstanceOf[FromInput[Any]]
    }
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
      val resource = getResource(resourceName)
      val fields = resource.handlers.flatMap { handler =>
        handler.kind match {
          case HandlerKind.GET =>
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

  def generateGetHandler(resource: Resource, handler: Handler) = {
    val arguments = generateHandlerArguments(handler)
    val resourceName = ResourceName(resource.name, resource.version.getOrElse(0L).toInt)

    val resolver = (context: Context[SangriaGraphQlContext, DataMap]) => {
      context.ctx.response.data.get(resourceName)
        .flatMap { resourceSet =>
          val idArgument = arguments.find(_.name == "id").getOrElse {
            throw new RuntimeException("No id argument for a get")
          }
          resourceSet.find { resource =>
            context.arg(idArgument) == resource._1
          }.map(optionalElement => Value(optionalElement._2))
        }.getOrElse {
          throw new RuntimeException(s"Cannot find ${ResourceCompanion.versionedName(resource)}")
        }
    }
    generateObjectTypeForResource(resourceName.identifier).map { resourceObjectType =>
      Field.apply[SangriaGraphQlContext, DataMap, DataMap, Any](
        "get",
        resourceObjectType,
        resolve = resolver,
        arguments = arguments)
    }
  }

  def generateListHandler(resource: Resource, handler: Handler) = {
    val arguments = generateHandlerArguments(handler)
    val resourceName = ResourceName(resource.name, resource.version.getOrElse(0L).toInt)

    val resolver = (context: Context[SangriaGraphQlContext, DataMap]) => {

      // TODO(bryan): FIX THIS!
      val selectionSet = SangriaGraphQlParser.parseField(context.astFields.head)
      val topLevelRequest = TopLevelRequest(
        resourceName,
        selectionSet,
        context.astFields.head.alias)

      val connection = (for {
        topLevelIds <- context.ctx.response.topLevelIds.get(topLevelRequest)
        objects <- context.ctx.response.data.get(resourceName)
      } yield {
        objects.collect {
          case (id, element) if topLevelIds.contains(id) => element
        }
      }).map(objects => Connection.connectionFromSeq(objects.toSeq, ConnectionArgs(context)))
        .getOrElse(Connection.empty[DataMap])

      Value[SangriaGraphQlContext, Connection[DataMap]](connection)

    }

    val fieldName = handler.kind match {
      case HandlerKind.FINDER => handler.name
      case HandlerKind.GET_ALL => "getAll"
      case HandlerKind.MULTI_GET => "multiGet"
      case _ => "error"
    }

    generateObjectConnectionTypeForResource(resourceName.identifier).map { resourceObjectConnectionType =>
      Field.apply[SangriaGraphQlContext, DataMap, Connection[DataMap], Any](
        fieldName,
        resourceObjectConnectionType,
        resolve = resolver,
        arguments = arguments)
    }
  }


  def generateHandlerArguments(handler: Handler): List[Argument[Any]] = {
    val explicitArguments: List[Argument[Any]] = handler.parameters.map { parameter =>
      val tpe = parameter.`type`
      // TODO(bryan): Use argument defaults here
      Argument(
        name = parameter.name,
        argumentType = scalaTypeToSangria(tpe))(scalaTypeToFromInput(tpe), implicitly).asInstanceOf[Argument[Any]]
    }.toList

    val paginationArguments: List[Argument[Any]] = handler.kind match {
      case HandlerKind.FINDER | HandlerKind.GET_ALL | HandlerKind.MULTI_GET =>
        List(
          Argument(
            name = "limit",
            argumentType = OptionInputType(IntType),
            defaultValue = PaginationConfiguration().defaultLimit).asInstanceOf[Argument[Any]],
          Argument(
            name = "start",
            argumentType = OptionInputType(StringType),
            description = "Cursor to start pagination at").asInstanceOf[Argument[Any]])
      case _ =>
        List.empty
    }

    explicitArguments ++ paginationArguments
  }

  /**
    * Generates a single GraphQL schema field for a RecordDataSchema field type.
    * If the field is marked as a related resource, generates the field as a relationship to the
    * associated resource. Otherwise, generates a generic schema for the model definition.
    *
    * @param field RecordDataSchema.Field for the field, pulled off the Courier schema for the model
    * @param namespace The namespace for the source model, used to prevent name collisions
    * @return GraphQL schema Field with nested schema information
    */
  def generateField(
      field: RecordDataSchema.Field,
      namespace: String): Field[SangriaGraphQlContext, DataMap] = {

    type ResolverType = Context[SangriaGraphQlContext, DataMap] => Value[SangriaGraphQlContext, Any]
    val originalField = (
      getSangriaTypeForSchema(field.getType, field.getName, namespace),
      getSangriaResolverForSchema(field.getType, field.getName))
    val (fieldScalarType, resolver): (OutputType[Any], ResolverType) = (field.getProperties.asScala.get("related"), field.getType) match {

      case (Some(relatedResourceName), _: ArrayDataSchema) =>
        generateObjectConnectionTypeForResource(relatedResourceName.toString).map { resourceObjectConnectionType =>
          (resourceObjectConnectionType,
            (context: Context[SangriaGraphQlContext, DataMap]) => {
              val resourceName = ResourceName.parse(relatedResourceName.toString).getOrElse {
                throw new RuntimeException(s"Cannot parse ${relatedResourceName.toString}")
              }
              val resourceObjects = context.ctx.response.data.getOrElse(
                resourceName,
                throw new RuntimeException(s"Cannot find objects for ${relatedResourceName.toString}"))
              val filteredObjects = resourceObjects.filter(
                obj => context.value.getDataList(field.getName).asScala.contains(obj._1))
              Value[SangriaGraphQlContext, Any](filteredObjects)
            })
        }.getOrElse(originalField)

      case (Some(relatedResourceName), _) =>
        generateObjectTypeForResource(relatedResourceName.toString).map { resourceObjectType =>
          (resourceObjectType, (context: Context[SangriaGraphQlContext, DataMap]) => {
            val resourceName = ResourceName.parse(relatedResourceName.toString).getOrElse {
              throw new RuntimeException(s"Cannot parse ${relatedResourceName.toString}")
            }
            val resourceObjects = context.ctx.response.data.getOrElse(
              resourceName,
              throw new RuntimeException(s"Cannot find objects for ${relatedResourceName.toString}"))

            val filteredObjects = resourceObjects
              .find(_._1 == context.value.get(field.getName)).getOrElse {
              throw new RuntimeException(
                s"Cannot find ${relatedResourceName.toString} with id " +
                  context.value.get(field.getName))
            }
            Value[SangriaGraphQlContext, Any](filteredObjects)
          })
        }.getOrElse(originalField)

      case (None, _) => originalField

    }
    val fieldScalarTypeWithOptionality = if (field.getOptional) {
      OptionType(fieldScalarType)
    } else {
      fieldScalarType
    }
    Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
      field.getName,
      fieldScalarTypeWithOptionality,
      resolve = resolver)
  }

  /**
    * Provides the resolver for a schema type, which implements how to retrieve a value from the raw
    * data type. For instance, for the Integer type, it pulls an integer out of a DataMap and
    * converts the types as appropriate.
    *
    * @param schemaType Pegasus data schema type for the field
    * @param fieldName name of the field (to pull the value out of the data map)
    * @return Sangria `Value` with the value and Sangria context on it
    */
  def getSangriaResolverForSchema(
      schemaType: DataSchema,
      fieldName: String):
    Context[SangriaGraphQlContext, DataMap] => Value[SangriaGraphQlContext, Any] = {

    schemaType match {
      // We don't want to wrap TypeRef fields in a Value class here or it'll be double wrapped
      case typerefField: TyperefDataSchema =>
        getSangriaResolverForSchema(typerefField.getDereferencedDataSchema, fieldName)
      case _ =>
        val baseResolver: (Context[SangriaGraphQlContext, DataMap]) => Any = {
          schemaType match {
            case stringField: StringDataSchema => context => context.value.getString(fieldName)
            case intField: IntegerDataSchema => context => context.value.getInteger(fieldName)
            case longField: LongDataSchema => context => context.value.getLong(fieldName)
            case booleanField: BooleanDataSchema => context => context.value.getBoolean(fieldName)
            case bytesField: BytesDataSchema => context => context.value.getByteString(fieldName)
            case doubleField: DoubleDataSchema => context => context.value.getDouble(fieldName)
            case floatField: FloatDataSchema => context => context.value.getFloat(fieldName)
            case nullField: NullDataSchema => context => null
            case enumField: EnumDataSchema => context => context.value.getString(fieldName)
            case unionField: UnionDataSchema => context => context.value.getDataMap(fieldName)
            case arrayField: ArrayDataSchema => context =>
              context.value.getDataList(fieldName).asScala
            case recordField: RecordDataSchema => context => context.value.getDataMap(fieldName)
            case _ =>
              logger.warn(s"Could not match schema type $schemaType")
              context => null
          }
        }
        baseResolver
          .andThen(res => Value[SangriaGraphQlContext, Any](res))
    }
  }

  /**
    * Converts a Pegasus DataSchema to Sangria GraphQL Schema type for use when generating a schema.
    *
    * Nested objects schemas are computed recursively.
    * Union types generate child ObjectTypes for their member classes
    *
    * @param schemaType DataSchema from the field, which specifies the source field type
    * @param fieldName The field's name, which is used to generate union field member types
    * @param namespace The field's namespace, which is used to prevent name colissions.
    * @return Sangria GraphQL OutputType, which represents the structure of the field in the schema
    */
  def getSangriaTypeForSchema(
      schemaType: DataSchema,
      fieldName: String,
      namespace: String): OutputType[Any] = {

    schemaType match {
      case stringField: StringDataSchema => StringType
      case intField: IntegerDataSchema => IntType
      case longField: LongDataSchema => LongType
      case booleanField: BooleanDataSchema => BooleanType
      case bytesField: BytesDataSchema => StringType
      case doubleField: DoubleDataSchema => FloatType
      case floatField: FloatDataSchema => FloatType
      case arrayField: ArrayDataSchema =>
        ListType(getSangriaTypeForSchema(arrayField.getItems, fieldName, namespace))
      case mapField: MapDataSchema =>
        // TODO(bryan): Figure out maps
        StringType
      case typeRefField: TyperefDataSchema =>
        getSangriaTypeForSchema(typeRefField.getRef, typeRefField.getName, namespace)
      case enumDataSchema: EnumDataSchema =>
        buildEnumType(enumDataSchema)
      case nullDataSchema: NullDataSchema =>
        // TODO(bryan): Figure out nulls
        StringType
      case recordDataSchema: RecordDataSchema =>
        buildRecordType(recordDataSchema, namespace)
      case unionDataSchema: UnionDataSchema =>
        buildUnionType(unionDataSchema, fieldName, namespace)
      case _ =>
        throw new Exception(s"Cannot find type for $schemaType")
    }
  }

  /**
    * Builds a Sangria enum from a Pegasus enum schema
    *
    * @param pegasusEnumSchema Pegasus representation of the enum schema
    * @return Sangria EnumType schema
    */
  def buildEnumType(pegasusEnumSchema: EnumDataSchema): EnumType[String] = {
    EnumType(
      name = formatName(pegasusEnumSchema.getFullName),
      values = pegasusEnumSchema.getSymbols.asScala.toList.map(symbol =>
        EnumValue(
          name = symbol,
          description = pegasusEnumSchema.getSymbolDocs.asScala.get(symbol),
          value = symbol)))
  }

  /**
    * Builds a Sangria record from a Pegasus record schema
    *
    * @param pegasusRecordSchema Pegasus representation of the record schema
    * @param namespace namespace for the record (potentially used for child types)
    * @return Sangria ObjectType schema
    */
  def buildRecordType(
      pegasusRecordSchema: RecordDataSchema,
      namespace: String): ObjectType[SangriaGraphQlContext, DataMap] = {

    ObjectType[SangriaGraphQlContext, DataMap](
      formatName(pegasusRecordSchema.getFullName),
      pegasusRecordSchema.getDoc,
      fieldsFn = () => {
        val fields = pegasusRecordSchema.getFields.asScala.map(generateField(_, namespace)).toList
        if (fields.isEmpty) {
          EMPTY_FIELDS_FALLBACK
        } else {
          fields
        }
      })
  }

  /**
    * Builds a Sangria union from a Pegasus union schema
    *
    * @param pegasusUnionSchema Pegasus representation of the union schema
    * @param fieldName field name for the union (used for creating member type names)
    * @param namespace namespace for the union (used for creating member type names)
    * @return Sangria UnionType with member object representations
    */
  def buildUnionType(
      pegasusUnionSchema: UnionDataSchema,
      fieldName: String,
      namespace: String): UnionType[SangriaGraphQlContext] = {

    val objects = pegasusUnionSchema.getTypes.asScala.map { subType =>
      val fieldName = formatName(subType.getUnionMemberKey)
      val field = Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
        formatName(subType.getUnionMemberKey),
        getSangriaTypeForSchema(subType, fieldName, namespace),
        resolve = getSangriaResolverForSchema(subType, fieldName))
      ObjectType[SangriaGraphQlContext, DataMap](
        name = formatName(s"${subType.getUnionMemberKey}Member"),
        fields = List(field))
    }.toList
    val unionName = buildFullyQualifiedName(namespace, fieldName)
    new UnionType(unionName, None, objects) {
      // write a custom type mapper to use field names to determine the union member type
      override def typeOf[Ctx](value: Any, schema: Schema[Ctx, _]): Option[ObjectType[Ctx, _]] =
      {
        val typedValue = value.asInstanceOf[DataMap]
        objects.find { obj =>
          obj.fieldsByName.keySet.intersect(typedValue.keySet().asScala).nonEmpty
        }.map(_.asInstanceOf[ObjectType[Ctx, DataMap]])
      }
    }
  }

  /**
    * Finds a resource with a given name from the provided list of resources, or throws an exception
    * if the resource cannot be found.
    *
    * @param resourceName string name, in the format courses.v1 or CoursesV1
    * @return Resource object
    */
  def getResource(resourceName: String): Resource = {
    resources.find(
      resource => {
        ResourceCompanion.versionedName(resource) == resourceName ||
          formatResourceName(resource) == resourceName
      }).getOrElse {
      throw new RuntimeException(s"Cannot find resource with name $resourceName")
    }
  }

  def buildFullyQualifiedName(namespace: String, fieldName: String): String = {
    formatName(s"$namespace.$fieldName")
  }

  /**
    * Converts a field or namespace name to a GraphQL compatible name, replacing '.' with '_'
    *
    * @param name Original field name
    * @return GraphQL-safe field name
    */
  def formatName(name: String): String = {
    name.replaceAll("\\.", "_")
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

object SangriaGraphQlSchemaBuilder {
  val EMPTY_FIELDS_FALLBACK = List(
    Field.apply[SangriaGraphQlContext, DataMap, Any, Any](
      "ArbitraryField",
      StringType,
      resolve = context => null))

  object ResourceNode {

    val FIELD_NAME = "__id"

    def globalIdField =
      Field(FIELD_NAME, IDType, Some(Node.GlobalIdFieldDescription),
        resolve = (ctx: Context[SangriaGraphQlContext, DataMap]) => {
          GlobalId.toGlobalId(ctx.parentType.name, ctx.value.getString("id"))
        })
  }

}
