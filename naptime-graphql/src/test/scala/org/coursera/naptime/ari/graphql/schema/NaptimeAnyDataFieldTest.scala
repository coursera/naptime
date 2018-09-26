package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.RecordDataSchema.{Field => RecordDataSchemaField}
import com.linkedin.data.template.DataTemplateUtil
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.helpers.ArgumentBuilder
import org.coursera.naptime.ari.graphql.types.NaptimeTypes
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar
import sangria.ast.Document
import sangria.execution.DeprecationTracker
import sangria.execution.ExecutionPath
import sangria.marshalling.ResultMarshaller
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.Schema
import sangria.schema.Value

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class NaptimeAnyDataFieldTest extends AssertionsForJUnit with MockitoSugar {

  private[this] val fieldName = "anyDataField"

  private[this] def createContext[Val](value: Val)(
      implicit ctxManifest: Manifest[SangriaGraphQlContext],
      valManifest: Manifest[Val]): Context[SangriaGraphQlContext, Val] = {
    Context[SangriaGraphQlContext, Val](
      value = value,
      ctx = SangriaGraphQlContext(null, null, ExecutionContext.global, debugMode = true),
      args =
        ArgumentBuilder.buildArgs(NaptimePaginationField.paginationArguments, Map("limit" -> 100)),
      schema = mock[Schema[SangriaGraphQlContext, Val]],
      field = mock[Field[SangriaGraphQlContext, Val]],
      parentType = mock[ObjectType[SangriaGraphQlContext, Any]],
      marshaller = mock[ResultMarshaller],
      query = Document.emptyStub,
      sourceMapper = None,
      deprecationTracker = DeprecationTracker.empty,
      astFields = Vector.empty,
      path = ExecutionPath.empty,
      deferredResolverState = None)
  }

  @Test
  def basic(): Unit = {
    val passthroughExemptJson =
      """
      |{
      |  "name": "PassthroughExempt",
      |  "type": "record",
      |  "passthroughExempt": true,
      |  "fields": [
      |  ]
      |}
    """.stripMargin
    val recordDataSchema =
      DataTemplateUtil.parseSchema(passthroughExemptJson).asInstanceOf[RecordDataSchema]
    val field = FieldBuilder.buildField(
      mock[SchemaMetadata],
      new RecordDataSchemaField(recordDataSchema),
      namespace = None,
      fieldNameOverride = Some(fieldName),
      resourceName = ResourceName("courses", 1))
    assert(field.fieldType === NaptimeTypes.DataMapType)

    val dataMap = new DataMap(Map("foo" -> 1).asJava)
    val context = createContext(
      DataMapWithParent(new DataMap(Map(fieldName -> dataMap).asJava), null))
    val result =
      field.resolve(context).asInstanceOf[Value[SangriaGraphQlContext, DataMap]]
    assert(result.value === dataMap)
  }

  @Test
  def parseApiResponse(): Unit = {
    val responseData = new TestExecutorHelper().executeQuery(
      """
        |query {
        |  CoursesV1Resource {
        |    get(id: "courseAId") {
        |      id
        |      arbitraryData
        |    }
        |  }
        |}
      """.stripMargin,
      Map(
        "courses" -> Map(
          "courseAId" -> List(
            new DataMap(Map(
              "arbitraryData" ->
                new DataMap(Map("moduleOne" -> "abc", "moduleTwo" -> "defg").asJava),
              "id" -> "courseAId").asJava)))))

    assert(
      (responseData \ "data" \ "CoursesV1Resource" \ "get" \ "arbitraryData").get
        .as[Map[String, String]] ===
        Map("moduleOne" -> "abc", "moduleTwo" -> "defg"))
    assert(
      (responseData \ "data" \ "CoursesV1Resource" \ "get" \ "id").get
        .as[String] === "courseAId")
  }

}
