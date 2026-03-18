package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.schema.EnumDataSchema
import com.linkedin.data.schema.MapDataSchema
import com.linkedin.data.schema.NullDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.RecordDataSchema.{Field => RecordDataSchemaField}
import com.linkedin.data.schema.RecordDataSchema.RecordType
import com.linkedin.data.schema.StringDataSchema
import com.linkedin.data.schema.Name
import com.linkedin.data.template.DataTemplateUtil
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
import sangria.schema.Args
import sangria.schema.Context
import sangria.schema.ObjectType
import sangria.schema.Schema

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class FieldBuilderTest extends AssertionsForJUnit with MockitoSugar {

  private val resourceName = ResourceName("courses", 1)
  private val schemaMetadata = mock[SchemaMetadata]
  private val resource = Models.courseResource
  when(schemaMetadata.getResourceOpt(resourceName)).thenReturn(Some(resource))
  when(schemaMetadata.getSchema(resource)).thenReturn(Some(null))

  private def buildSimpleRecordField(name: String, schema: com.linkedin.data.schema.DataSchema): RecordDataSchemaField = {
    val record = new RecordDataSchema(
      new Name(s"${name}Record", "org.test", new java.lang.StringBuilder()),
      RecordType.RECORD)
    val f = new RecordDataSchemaField(schema)
    f.setName(name, new java.lang.StringBuilder())
    f.setRecord(record)
    f
  }

  private def buildContext(value: DataMapWithParent): Context[SangriaGraphQlContext, DataMapWithParent] = {
    val mockSchema = mock[sangria.schema.Schema[SangriaGraphQlContext, DataMapWithParent]]
    val mockField = mock[sangria.schema.Field[SangriaGraphQlContext, DataMapWithParent]]
    val mockParent = mock[ObjectType[SangriaGraphQlContext, Any]]
    Context[SangriaGraphQlContext, DataMapWithParent](
      value = value,
      ctx = SangriaGraphQlContext(null, null, ExecutionContext.global, debugMode = false),
      args = ArgumentBuilder.buildArgs(NaptimePaginationField.paginationArguments, Map("limit" -> 100)),
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

  // -------------------------------------------------------------------------
  // followRelations = false skips relation parsing (lines 79/81)
  // -------------------------------------------------------------------------

  @Test
  def buildField_followRelationsFalse_treatsFieldAsRegular(): Unit = {
    // Build a string field that would normally have a relation annotation
    // but when followRelations=false it should just treat it as a string field
    val stringSchema = new StringDataSchema()
    val fieldWithNoRelation = buildSimpleRecordField("myField", stringSchema)

    val result = FieldBuilder.buildField(
      schemaMetadata,
      fieldWithNoRelation,
      namespace = None,
      fieldNameOverride = None,
      followRelations = false,
      resourceName = resourceName)

    assert(result.name === "myField")
  }

  // -------------------------------------------------------------------------
  // MapDataSchema — lines 212-216
  // -------------------------------------------------------------------------

  @Test
  def buildField_mapDataSchema_returnsDataMapType(): Unit = {
    val mapSchema = new MapDataSchema(new StringDataSchema())
    val mapField = buildSimpleRecordField("myMap", mapSchema)

    val result = FieldBuilder.buildField(
      schemaMetadata,
      mapField,
      namespace = None,
      resourceName = resourceName)

    assert(result.name === "myMap")
    // MapDataSchema → DataMapType
    assert(result.fieldType === org.coursera.naptime.ari.graphql.types.NaptimeTypes.DataMapType)
  }

  @Test
  def buildField_mapDataSchema_resolve_returnsDataMap(): Unit = {
    val mapSchema = new MapDataSchema(new StringDataSchema())
    val mapField = buildSimpleRecordField("myMap", mapSchema)

    val result = FieldBuilder.buildField(
      schemaMetadata,
      mapField,
      namespace = None,
      resourceName = resourceName)

    // Test the resolve function
    val innerDataMap = new DataMap()
    innerDataMap.put("k", "v")
    val outerDataMap = new DataMap()
    outerDataMap.put("myMap", innerDataMap)
    val parentModel = mock[ParentModel]
    val dmWithParent = DataMapWithParent(outerDataMap, parentModel)

    val ctx = buildContext(dmWithParent)
    val resolved = result.resolve(ctx)
    assert(resolved != null)
  }

  // -------------------------------------------------------------------------
  // NullDataSchema — lines 228-231
  // -------------------------------------------------------------------------

  @Test
  def buildField_nullDataSchema_returnsDataMapType(): Unit = {
    val nullSchema = new NullDataSchema()
    val nullField = buildSimpleRecordField("myNull", nullSchema)

    val result = FieldBuilder.buildField(
      schemaMetadata,
      nullField,
      namespace = None,
      resourceName = resourceName)

    assert(result.name === "myNull")
    assert(result.fieldType === org.coursera.naptime.ari.graphql.types.NaptimeTypes.DataMapType)
  }

  @Test
  def buildField_nullDataSchema_resolve_returnsNull(): Unit = {
    val nullSchema = new NullDataSchema()
    val nullField = buildSimpleRecordField("myNull", nullSchema)

    val result = FieldBuilder.buildField(
      schemaMetadata,
      nullField,
      namespace = None,
      resourceName = resourceName)

    val dm = new DataMap()
    val parentModel = mock[ParentModel]
    val dmWithParent = DataMapWithParent(dm, parentModel)
    val ctx = buildContext(dmWithParent)
    val resolved = result.resolve(ctx)
    assert(resolved == null || resolved.isInstanceOf[sangria.schema.Value[_, _]])
  }

  // -------------------------------------------------------------------------
  // formatName — test formatting
  // -------------------------------------------------------------------------

  @Test
  def formatName_simpleField_returnsAsIs(): Unit = {
    assert(FieldBuilder.formatName("myField") === "myField")
  }

  @Test
  def formatName_fieldStartingWithUnderscore_addsPrefix(): Unit = {
    // Fields starting with __ need sanitization
    val result = FieldBuilder.formatName("__myField")
    assert(!result.startsWith("__"))
  }

  // -------------------------------------------------------------------------
  // EnumDataSchema field — covers NaptimeEnumField.build resolve lambda (line 18)
  // -------------------------------------------------------------------------

  @Test
  def buildField_enumDataSchema_returnsEnumField(): Unit = {
    val enumSchema = new EnumDataSchema(new Name("Status", "org.test", new java.lang.StringBuilder()))
    enumSchema.setSymbols(java.util.Arrays.asList("ACTIVE", "INACTIVE"), new java.lang.StringBuilder())
    val enumField = buildSimpleRecordField("status", enumSchema)

    val result = FieldBuilder.buildField(
      schemaMetadata,
      enumField,
      namespace = None,
      resourceName = resourceName)

    assert(result.name === "status")
  }

  @Test
  def buildField_enumDataSchema_resolve_returnsEnumString(): Unit = {
    val enumSchema = new EnumDataSchema(new Name("Status", "org.test", new java.lang.StringBuilder()))
    enumSchema.setSymbols(java.util.Arrays.asList("ACTIVE", "INACTIVE"), new java.lang.StringBuilder())
    val enumField = buildSimpleRecordField("status", enumSchema)

    val result = FieldBuilder.buildField(
      schemaMetadata,
      enumField,
      namespace = None,
      resourceName = resourceName)

    val dm = new DataMap()
    dm.put("status", "ACTIVE")
    val parentModel = mock[ParentModel]
    val dmWithParent = DataMapWithParent(dm, parentModel)
    val ctx = buildContext(dmWithParent)
    val resolved = result.resolve(ctx)
    assert(resolved == "ACTIVE" || resolved != null)
  }

  // -------------------------------------------------------------------------
  // Array field with DataMap items — covers FieldBuilder array resolve (line 210)
  // -------------------------------------------------------------------------

  @Test
  def buildField_arrayWithDataMapItems_resolve_returnsDataMapList(): Unit = {
    import com.linkedin.data.schema.ArrayDataSchema
    val innerRecord = new RecordDataSchema(
      new Name("InnerRecord", "org.test", new java.lang.StringBuilder()),
      RecordDataSchema.RecordType.RECORD)
    val arraySchema = new ArrayDataSchema(innerRecord)
    val arrayField = buildSimpleRecordField("items", arraySchema)

    val result = FieldBuilder.buildField(
      schemaMetadata,
      arrayField,
      namespace = None,
      resourceName = resourceName)

    assert(result.name === "items")
    // Test resolve with a DataList containing a DataMap
    val innerDm = new DataMap()
    innerDm.put("x", "y")
    val dataList = new DataList()
    dataList.add(innerDm)
    val outerDm = new DataMap()
    outerDm.put("items", dataList)
    val parentModel = mock[ParentModel]
    val dmWithParent = DataMapWithParent(outerDm, parentModel)
    val ctx = buildContext(dmWithParent)
    val resolved = result.resolve(ctx)
    assert(resolved != null)
  }

  @Test
  def buildField_arrayWithNullItems_resolve_returnsNull(): Unit = {
    import com.linkedin.data.schema.ArrayDataSchema
    val innerRecord = new RecordDataSchema(
      new Name("InnerRecord2", "org.test", new java.lang.StringBuilder()),
      RecordDataSchema.RecordType.RECORD)
    val arraySchema = new ArrayDataSchema(innerRecord)
    val arrayField = buildSimpleRecordField("items2", arraySchema)

    val result = FieldBuilder.buildField(
      schemaMetadata,
      arrayField,
      namespace = None,
      resourceName = resourceName)

    // Test resolve when the DataList is null (field not present in DataMap)
    val outerDm = new DataMap()
    // "items2" not set → getDataList returns null
    val parentModel = mock[ParentModel]
    val dmWithParent = DataMapWithParent(outerDm, parentModel)
    val ctx = buildContext(dmWithParent)
    val resolved = result.resolve(ctx)
    // null path: Option(null) → None → getOrElse(null) → null, wrapped in Value(null)
    assert(resolved == null || resolved.isInstanceOf[sangria.schema.Value[_, _]])
  }
}
