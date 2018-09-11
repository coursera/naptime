package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.RecordDataSchema.{Field => RecordDataSchemaField}
import com.linkedin.data.template.DataTemplateUtil
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.SangriaGraphQlSchemaBuilder
import org.coursera.naptime.ari.graphql.helpers.ArgumentBuilder
import org.coursera.naptime.ari.graphql.marshaller.NaptimeMarshaller._
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.coursera.naptime.ari.graphql.resolvers.NaptimeResolver
import org.coursera.naptime.ari.graphql.types.NaptimeTypes
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import play.api.libs.json.JsObject
import sangria.ast.Document
import sangria.execution.DeprecationTracker
import sangria.execution.ExecutionPath
import sangria.execution.Executor
import sangria.marshalling.ResultMarshaller
import sangria.parser.QueryParser
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.Schema
import sangria.schema.Value

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration

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
  def parseDataMapTypes(): Unit = {
    val schemaTypes = Map(
      "org.coursera.naptime.ari.graphql.models.MergedCourse" -> MergedCourse.SCHEMA,
      "org.coursera.naptime.ari.graphql.models.MergedPartner" -> MergedPartner.SCHEMA,
      "org.coursera.naptime.ari.graphql.models.MergedInstructor" -> MergedInstructor.SCHEMA)
    val allResources =
      Set(Models.courseResource, Models.instructorResource, Models.partnersResource)
    val builder = new SangriaGraphQlSchemaBuilder(allResources, schemaTypes)
    val schema = builder.generateSchema().data.asInstanceOf[Schema[SangriaGraphQlContext, Any]]

    val query =
      """
        |query {
        |  CoursesV1Resource {
        |    get(id: "courseAId") {
        |      id
        |      arbitraryData
        |    }
        |  }
        |}
    """.stripMargin
    val queryAst = QueryParser.parse(query).get

    val fetcher = mock[FetcherApi]
    val data = List(
      new DataMap(
        Map(
          "arbitraryData" ->
            new DataMap(Map("moduleOne" -> "abc", "moduleTwo" -> "defg").asJava),
          "id" -> "courseAId").asJava))
    when(fetcher.data(any(), any())(any()))
      .thenReturn(
        Future.successful(Right(Response(data, ResponsePagination(None, None, None), url = None))))

    val context = SangriaGraphQlContext(fetcher, null, ExecutionContext.global, debugMode = true)
    val responseData = Await
      .result(
        Executor
          .execute(
            schema,
            queryAst,
            context,
            variables = JsObject(Map.empty[String, JsObject]),
            deferredResolver = new NaptimeResolver()),
        Duration.Inf)
      .asInstanceOf[JsObject]

    assert(
      (responseData \ "data" \ "CoursesV1Resource" \ "get" \ "arbitraryData").get
        .as[Map[String, String]] ===
        Map("moduleOne" -> "abc", "moduleTwo" -> "defg"))
    assert(
      (responseData \ "data" \ "CoursesV1Resource" \ "get" \ "id").get
        .as[String] === "courseAId")
  }

}
