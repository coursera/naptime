package org.coursera.naptime.ari.graphql

import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.BooleanDataSchema
import com.linkedin.data.schema.BytesDataSchema
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.DoubleDataSchema
import com.linkedin.data.schema.EnumDataSchema
import com.linkedin.data.schema.FloatDataSchema
import com.linkedin.data.schema.IntegerDataSchema
import com.linkedin.data.schema.LongDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.StringDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import org.coursera.courier.templates.ScalaRecordTemplate
import org.coursera.naptime.schema.Resource
import sangria.schema.BooleanType
import sangria.schema.Context
import sangria.schema.EnumType
import sangria.schema.EnumValue
import sangria.schema.Field
import sangria.schema.FloatType
import sangria.schema.IntType
import sangria.schema.ListType
import sangria.schema.LongType
import sangria.schema.ObjectType
import sangria.schema.OptionType
import sangria.schema.OutputType
import sangria.schema.Schema
import sangria.schema.StringType
import sangria.schema.UnionType
import sangria.schema.Value

import scala.collection.JavaConverters._

class SangriaGraphQlSchemaBuilder(resources: Set[Resource], schemas: Map[String, RecordDataSchema]) {

  def generateSchema(): Schema[Unit, ScalaRecordTemplate] = {
    val topLevelResourceObjects = resources.map { resource =>
      val resourceObject = generateSchemaForResource(resource.name)
      Field.apply[Unit, ScalaRecordTemplate, Unit, Any](
        formatResourceName(resource.name),
        resourceObject,
        resolve = sangriaResolveFn)
    }

    val rootObject = ObjectType[Unit, ScalaRecordTemplate](
      name = "Root",
      description = "Top-level accessor for Naptime resources",
      fields = topLevelResourceObjects.toList)
    Schema(rootObject)
  }

  def generateSchemaForResource(resourceName: String): ObjectType[Unit, ScalaRecordTemplate] = {
    val resource = resources.find(_.name == resourceName).getOrElse {
      throw new RuntimeException(s"Cannot find resource with name $resourceName")
    }

    val schema = schemas.getOrElse(resource.mergedType, {
      throw new RuntimeException(s"Cannot find schema for ${resource.mergedType}")
    })

    ObjectType[Unit, ScalaRecordTemplate](
      name = formatResourceName(resource.name),
      description = "",
      fieldsFn = () => {
        schema.getFields.asScala.map { field =>
          generateField(field, schema.getNamespace)
        }.toList
      })
  }

  // NOTE: We don't currently use sangria's `resolve` functionality for now
  val sangriaResolveFn = (context: Context[Unit, ScalaRecordTemplate]) => Value[Unit, Unit](Unit)

  def generateField(field: RecordDataSchema.Field, namespace: String): Field[Unit, ScalaRecordTemplate] = {
    val fieldScalarType = (field.getProperties.asScala.get("related"), field.getType) match {
      case (Some(relatedResourceName), _: ArrayDataSchema) =>
        ListType(generateSchemaForResource(relatedResourceName.toString))
      case (Some(relatedResourceName), _) =>
        generateSchemaForResource(relatedResourceName.toString)
      case (None, _) =>
        getSangriaTypeForSchema(field.getType, field.getName, namespace)
    }
    val fieldScalarTypeWithOptionality = if (field.getOptional) {
      OptionType(fieldScalarType)
    } else {
      fieldScalarType
    }
    Field.apply[Unit, ScalaRecordTemplate, Unit, Any](
      field.getName,
      fieldScalarTypeWithOptionality,
      resolve = sangriaResolveFn)
  }

  def getSangriaTypeForSchema(schemaType: DataSchema, fieldName: String, namespace: String): OutputType[Any] = {
    schemaType match {
      case stringField: StringDataSchema => StringType
      case intField: IntegerDataSchema => IntType
      case longField: LongDataSchema => LongType
      case booleanField: BooleanDataSchema => BooleanType
      case bytesField: BytesDataSchema => StringType
      case doubleField: DoubleDataSchema => FloatType
      case floatField: FloatDataSchema => FloatType
      case enumField: EnumDataSchema =>
        EnumType(
          name = formatName(enumField.getFullName),
          values = enumField.getSymbols.asScala.map(symbol =>
            EnumValue(
              name = symbol,
              description = enumField.getSymbolDocs.asScala.get(symbol),
              value = symbol)).toList)
      case arrayField: ArrayDataSchema =>
        ListType(getSangriaTypeForSchema(arrayField.getItems, fieldName, namespace))
      case typeRefField: TyperefDataSchema =>
        getSangriaTypeForSchema(typeRefField.getRef, typeRefField.getName, namespace)
      case recordField: RecordDataSchema =>
        ObjectType[Unit, ScalaRecordTemplate](
          formatName(recordField.getFullName),
          recordField.getDoc,
          recordField.getFields.asScala.map(generateField(_, namespace)).toList)
      case unionField: UnionDataSchema =>
        val objects = unionField.getTypes.asScala.map { subType =>
          val field = Field.apply[Unit, ScalaRecordTemplate, Unit, Any](
            formatName(subType.getUnionMemberKey),
            getSangriaTypeForSchema(subType, fieldName, namespace),
            resolve = sangriaResolveFn)
          ObjectType[Unit, ScalaRecordTemplate](
            formatName(s"${subType.getUnionMemberKey}Member"),
            "",
            List(field))
        }.toList
        val unionName = formatName(s"$namespace.$fieldName")
        UnionType(unionName, None, objects)
      case _ =>
        println(schemaType.getType)
        StringType
    }
  }

  def formatName(name: String): String = {
    name.replaceAll("\\.", "_")
  }

  def formatResourceName(name: String): String = {
    name.replace(".v", "V").capitalize
  }



}
