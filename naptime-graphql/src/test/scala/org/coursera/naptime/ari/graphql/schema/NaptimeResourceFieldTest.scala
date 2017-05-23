package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.models.MergedMultigetFreeEntity
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.coursera.naptime.ari.graphql.models.MergedPointerEntity
import org.junit.Test
import org.mockito.Mockito.when
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar

import scala.collection.JavaConverters._

class NaptimeResourceFieldTest extends AssertionsForJUnit with MockitoSugar {

  val schemaMetadata = mock[SchemaMetadata]
  val partnerResource = Models.partnersResource
  val partnerSchema = MergedPartner.SCHEMA
  val pointerResource = Models.pointerEntity
  val pointerSchema = MergedPointerEntity.SCHEMA
  val filteredResource = Models.multigetFreeEntity
  val filteredSchema = MergedMultigetFreeEntity.SCHEMA
  val resourceName = "myTestResource"
  val field = mock[RecordDataSchema.Field]


  @Test
  def canGenerateField(): Unit = {
    val testFieldName = "testFieldName"

    when(schemaMetadata.getResource(resourceName)).thenReturn(partnerResource)
    when(schemaMetadata.getSchema(partnerResource)).thenReturn(Some(partnerSchema))
    when(field.getDoc).thenReturn("")
    when(field.getName).thenReturn(testFieldName)
    val generatedFieldOpt = NaptimeResourceField.generateField(field, schemaMetadata, partnerResource, partnerSchema)
    assert(generatedFieldOpt.isDefined)
    val generatedField = generatedFieldOpt.get
    assert(generatedField.name === testFieldName)
 }

  @Test
  def skipsGeneratingNonMultigetField(): Unit = {
    val testFieldName = "testFieldName"

    when(field.getDoc).thenReturn("")
    when(field.getName).thenReturn("testName")
    when(field.getProperties).thenReturn(Map("related" -> testFieldName .asInstanceOf[AnyRef]).asJava)
    when(schemaMetadata.getResource(testFieldName)).thenReturn(filteredResource)
    val generatedField = NaptimeResourceField.generateField(field, schemaMetadata, pointerResource, pointerSchema)
    assert(generatedField.isEmpty)
  }
}
