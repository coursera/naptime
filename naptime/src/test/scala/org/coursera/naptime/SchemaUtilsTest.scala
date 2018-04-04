package org.coursera.naptime

import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.DataSchemaUtil
import com.linkedin.data.schema.MapDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.UnionDataSchema
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.junit._
import org.scalatest.junit.AssertionsForJUnit

import scala.collection.immutable

class SchemaUtilsTest extends AssertionsForJUnit {

  @Test
  def testFixupSchema_EmptyOverrides(): Unit = {
    val schemaToFix = MergedCourse.SCHEMA
    val overrides = immutable.Map[String, DataSchema]()
    SchemaUtils.fixupInferredSchemas(schemaToFix, overrides)
    assert(schemaToFix === MergedCourse.SCHEMA)
  }

  @Test
  def testFixupSchema_RecursiveSchema_EmptyOverrides(): Unit = {
    val schemaToFix = RecursiveModelBase.SCHEMA
    val overrides = Map[String, DataSchema]()
    SchemaUtils.fixupInferredSchemas(schemaToFix, overrides)
    assert(schemaToFix === RecursiveModelBase.SCHEMA)
  }

  @Test
  def testFixupSchema_RecursiveSchema_WithOverrides(): Unit = {
    val schemaToFix = RecursiveModelBase.SCHEMA
    val longDataSchema = DataSchemaUtil.dataSchemaTypeToPrimitiveDataSchema(DataSchema.Type.LONG)
    val overrides = Map("org.coursera.naptime.RecursiveChild" -> longDataSchema)
    SchemaUtils.fixupInferredSchemas(schemaToFix, overrides)
    assert(schemaToFix.getField("recursiveChild").getType === longDataSchema)
  }

  @Test
  def testFixupSchema_ListSchema_WithOverrides(): Unit = {
    val schemaToFix = RecursiveModelBase.SCHEMA
    val longDataSchema = DataSchemaUtil.dataSchemaTypeToPrimitiveDataSchema(DataSchema.Type.LONG)
    val overrides = Map("org.coursera.naptime.RecursiveChild" -> longDataSchema)
    SchemaUtils.fixupInferredSchemas(schemaToFix, overrides)
    assert(
      schemaToFix.getField("recursiveChildren").getType ===
        new ArrayDataSchema(longDataSchema))
  }

  @Test
  def testFixupSchema_MapSchema_WithOverrides(): Unit = {
    val schemaToFix = RecursiveModelBase.SCHEMA
    val longDataSchema = DataSchemaUtil.dataSchemaTypeToPrimitiveDataSchema(DataSchema.Type.LONG)
    val overrides = Map("org.coursera.naptime.RecursiveChild" -> longDataSchema)
    SchemaUtils.fixupInferredSchemas(schemaToFix, overrides)
    assert(
      schemaToFix.getField("recursiveChildMap").getType ===
        new MapDataSchema(longDataSchema))
  }

  @Test
  def testFixupSchema_RecursiveListSchema_WithOverrides(): Unit = {
    val schemaToFix = RecursiveModelBase.SCHEMA
    val stringDataSchema =
      DataSchemaUtil.dataSchemaTypeToPrimitiveDataSchema(DataSchema.Type.STRING)
    val overrides = Map("org.coursera.naptime.StringLikeField" -> stringDataSchema)
    SchemaUtils.fixupInferredSchemas(schemaToFix, overrides)
    assert(
      schemaToFix
        .getField("nestedChild")
        .getType
        .asInstanceOf[ArrayDataSchema]
        .getItems
        .asInstanceOf[RecordDataSchema]
        .getField("stringLikeField")
        .getType === stringDataSchema)
  }

  @Test
  def testFixupSchema_RecursiveUnionSchema_WithOverrides(): Unit = {
    val schemaToFix = RecursiveModelBase.SCHEMA
    val stringDataSchema =
      DataSchemaUtil.dataSchemaTypeToPrimitiveDataSchema(DataSchema.Type.STRING)
    val overrides = Map("org.coursera.naptime.StringLikeField" -> stringDataSchema)
    SchemaUtils.fixupInferredSchemas(schemaToFix, overrides)
    assert(
      schemaToFix
        .getField("unionChild")
        .getType
        .asInstanceOf[UnionDataSchema]
        .getTypeByName("NestedChild")
        .asInstanceOf[RecordDataSchema]
        .getField("stringLikeField")
        .getType === stringDataSchema)
  }

}
