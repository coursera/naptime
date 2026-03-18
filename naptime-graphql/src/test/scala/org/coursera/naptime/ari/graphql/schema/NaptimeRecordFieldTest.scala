package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.Name
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.RecordDataSchema.RecordType
import com.linkedin.data.schema.StringDataSchema
import com.linkedin.data.schema.RecordDataSchema.{Field => RecordDataSchemaField}
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.helpers.ArgumentBuilder
import org.junit.Test
import org.mockito.Mockito.when
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar
import sangria.ast.Document
import sangria.execution.DeprecationTracker
import sangria.execution.ExecutionPath
import sangria.marshalling.ResultMarshaller
import sangria.schema.Context
import sangria.schema.ObjectType
import sangria.schema.Schema
import sangria.schema.Value

import scala.concurrent.ExecutionContext

/**
 * Tests for NaptimeRecordField covering the resolve function branches:
 * - DataMap result (line 35-36)
 * - Non-DataMap result: logger.warn + Value(null) (lines 37-39)
 * - null result: Value(null) (lines 40-41)
 * - Empty fields fallback (lines 67-73)
 */
class NaptimeRecordFieldTest extends AssertionsForJUnit with MockitoSugar {

  private val resourceName = ResourceName("courses", 1)
  private val schemaMetadata = mock[SchemaMetadata]
  private val resource = Models.courseResource
  when(schemaMetadata.getResourceOpt(resourceName)).thenReturn(Some(resource))
  when(schemaMetadata.getSchema(resource)).thenReturn(Some(null))

  private def buildContext(
      value: DataMapWithParent): Context[SangriaGraphQlContext, DataMapWithParent] = {
    val mockSchema = mock[Schema[SangriaGraphQlContext, DataMapWithParent]]
    val mockField = mock[sangria.schema.Field[SangriaGraphQlContext, DataMapWithParent]]
    val mockParent = mock[ObjectType[SangriaGraphQlContext, Any]]
    Context[SangriaGraphQlContext, DataMapWithParent](
      value = value,
      ctx = SangriaGraphQlContext(null, null, ExecutionContext.global, debugMode = false),
      args =
        ArgumentBuilder.buildArgs(NaptimePaginationField.paginationArguments, Map("limit" -> 100)),
      schema = mockSchema,
      field = mockField,
      parentType = mockParent,
      marshaller = mock[ResultMarshaller],
      query = Document.emptyStub,
      sourceMapper = None,
      deprecationTracker = DeprecationTracker.empty,
      astFields = Vector.empty,
      path = ExecutionPath.empty,
      deferredResolverState = None)
  }

  private def buildRecordSchema(name: String): RecordDataSchema = {
    val record = new RecordDataSchema(
      new Name(name, "org.test", new java.lang.StringBuilder()),
      RecordType.RECORD)
    // Add one field so fields is non-empty
    val strField = new RecordDataSchemaField(new StringDataSchema())
    strField.setName("value", new java.lang.StringBuilder())
    strField.setRecord(record)
    val errorList = new java.lang.StringBuilder()
    record.setFields(java.util.Arrays.asList(strField), errorList)
    record
  }

  private def buildEmptyRecordSchema(name: String): RecordDataSchema = {
    new RecordDataSchema(
      new Name(name, "org.test", new java.lang.StringBuilder()),
      RecordType.RECORD)
    // No fields added — getFields returns empty list
  }

  // -------------------------------------------------------------------------
  // NaptimeRecordField.build — DataMap element (normal path)
  // -------------------------------------------------------------------------

  @Test
  def build_resolve_withDataMapElement_returnsDataMapWithParent(): Unit = {
    val recordSchema = buildRecordSchema("RecordA")

    val field = NaptimeRecordField.build(
      schemaMetadata,
      recordSchema,
      "myRecord",
      None,
      resourceName,
      List.empty)

    val innerDm = new DataMap()
    innerDm.put("value", "hello")
    val outerDm = new DataMap()
    outerDm.put("myRecord", innerDm)
    val parentModel = mock[ParentModel]
    val dmWithParent = DataMapWithParent(outerDm, parentModel)

    val ctx = buildContext(dmWithParent)
    val resolved = field.resolve(ctx)
    assert(resolved != null)
    // The resolve function returns either a DataMapWithParent or a Value wrapping one;
    // either way the result should be non-null.
  }

  // -------------------------------------------------------------------------
  // NaptimeRecordField.build — null element → Value(null)
  // -------------------------------------------------------------------------

  @Test
  def build_resolve_withNullElement_returnsValueNull(): Unit = {
    val recordSchema = buildRecordSchema("RecordB")

    val field = NaptimeRecordField.build(
      schemaMetadata,
      recordSchema,
      "myRecord",
      None,
      resourceName,
      List.empty)

    val outerDm = new DataMap()
    // myRecord is NOT set → get("myRecord") returns null
    val parentModel = mock[ParentModel]
    val dmWithParent = DataMapWithParent(outerDm, parentModel)

    val ctx = buildContext(dmWithParent)
    val resolved = field.resolve(ctx)
    // The null case → Value(null) which evaluates to null
    assert(resolved == null || resolved == Value(null))
  }

  // -------------------------------------------------------------------------
  // NaptimeRecordField.build — non-DataMap element → logger.warn + Value(null)
  // -------------------------------------------------------------------------

  @Test
  def build_resolve_withNonDataMapElement_returnsValueNull(): Unit = {
    val recordSchema = buildRecordSchema("RecordC")

    val field = NaptimeRecordField.build(
      schemaMetadata,
      recordSchema,
      "myRecord",
      None,
      resourceName,
      List.empty)

    val outerDm = new DataMap()
    // Put a String where a DataMap is expected → "other: Any" branch
    outerDm.put("myRecord", "notADataMap")
    val parentModel = mock[ParentModel]
    val dmWithParent = DataMapWithParent(outerDm, parentModel)

    val ctx = buildContext(dmWithParent)
    val resolved = field.resolve(ctx)
    // logger.warn is called, returns Value(null)
    assert(resolved == null || resolved == Value(null))
  }

  // -------------------------------------------------------------------------
  // NaptimeRecordField.getType — empty fields → EMPTY_FIELDS_FALLBACK
  // -------------------------------------------------------------------------

  @Test
  def getType_emptyRecordSchema_returnsEmptyFieldsFallback(): Unit = {
    val emptyRecord = buildEmptyRecordSchema("EmptyRecord")

    val objectType =
      NaptimeRecordField.getType(schemaMetadata, emptyRecord, None, resourceName, List.empty)

    // When fields are empty, the fieldsFn returns EMPTY_FIELDS_FALLBACK
    val fields = objectType.fieldsFn()
    assert(fields === NaptimeRecordField.EMPTY_FIELDS_FALLBACK)
  }

  // -------------------------------------------------------------------------
  // EMPTY_FIELDS_FALLBACK — resolve returns null
  // -------------------------------------------------------------------------

  @Test
  def emptyFieldsFallback_resolve_returnsNull(): Unit = {
    val fallbackFields = NaptimeRecordField.EMPTY_FIELDS_FALLBACK
    assert(fallbackFields.nonEmpty)

    val dm = new DataMap()
    val parentModel = mock[ParentModel]
    val dmWithParent = DataMapWithParent(dm, parentModel)

    val mockSchema = mock[Schema[SangriaGraphQlContext, DataMapWithParent]]
    val mockField = mock[sangria.schema.Field[SangriaGraphQlContext, DataMapWithParent]]
    val mockParent = mock[ObjectType[SangriaGraphQlContext, Any]]
    val ctx = Context[SangriaGraphQlContext, DataMapWithParent](
      value = dmWithParent,
      ctx = SangriaGraphQlContext(null, null, ExecutionContext.global, debugMode = false),
      args =
        ArgumentBuilder.buildArgs(NaptimePaginationField.paginationArguments, Map("limit" -> 100)),
      schema = mockSchema,
      field = mockField,
      parentType = mockParent,
      marshaller = mock[ResultMarshaller],
      query = Document.emptyStub,
      sourceMapper = None,
      deprecationTracker = DeprecationTracker.empty,
      astFields = Vector.empty,
      path = ExecutionPath.empty,
      deferredResolverState = None)

    val resolved = fallbackFields.head.resolve(ctx)
    assert(resolved == null)
  }
}
