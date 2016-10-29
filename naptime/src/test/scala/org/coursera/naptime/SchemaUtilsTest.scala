package org.coursera.naptime

import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.DataSchemaUtil
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

}
