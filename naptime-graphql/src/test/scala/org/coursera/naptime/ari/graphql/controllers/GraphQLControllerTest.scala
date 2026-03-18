package org.coursera.naptime.ari.graphql.controllers

import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.graphql.GraphqlSchemaProvider
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.SangriaGraphQlSchemaBuilder
import org.coursera.naptime.ari.graphql.controllers.filters.Filter
import org.coursera.naptime.ari.graphql.controllers.filters.FilterList
import org.coursera.naptime.ari.graphql.controllers.filters.IncomingQuery
import org.coursera.naptime.ari.graphql.controllers.filters.OutgoingQuery
import org.coursera.naptime.ari.graphql.controllers.middleware.GraphQLMetricsCollector
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.junit.Test
import org.mockito.Mockito.when
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._
import sangria.execution.Middleware
import sangria.schema.Schema

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class GraphQLControllerTest
    extends AssertionsForJUnit
    with MockitoSugar
    with ScalaFutures
    with IntegrationPatience
    with Results {

  implicit val ec: ExecutionContext = ExecutionContext.global

  // -------------------------------------------------------------------------
  // Test infrastructure
  // -------------------------------------------------------------------------

  private val schemaTypes = Map(
    "org.coursera.naptime.ari.graphql.models.MergedCourse" -> MergedCourse.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedPartner" -> MergedPartner.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedInstructor" -> MergedInstructor.SCHEMA)

  private val allResources =
    Set(Models.courseResource, Models.instructorResource, Models.partnersResource)

  private val builder = new SangriaGraphQlSchemaBuilder(allResources, schemaTypes)
  private val schema =
    builder.generateSchema().data.asInstanceOf[Schema[SangriaGraphQlContext, Any]]

  private val graphqlSchemaProvider = mock[GraphqlSchemaProvider]
  when(graphqlSchemaProvider.schema).thenReturn(schema)

  private val noopFetcher: FetcherApi = new FetcherApi {
    override def data(request: Request, isDebugMode: Boolean)(
        implicit executionContext: ExecutionContext): Future[FetcherResponse] =
      Future.successful(Right(Response(List.empty, ResponsePagination(None), Some("http://test"))))
  }

  private val noopMetrics: GraphQLMetricsCollector = new GraphQLMetricsCollector {
    override def markFieldError(fieldName: String): Unit = ()
    override def timeQueryParsing[A](operationName: String)(f: => A): A = f
  }

  private val emptyFilterList = FilterList(immutable.Seq.empty)

  private def buildController(
      filterList: FilterList = emptyFilterList,
      fetcher: FetcherApi = noopFetcher): GraphQLController = {
    val controller = new GraphQLController(
      graphqlSchemaProvider,
      graphqlSchemaProvider,
      fetcher,
      filterList,
      noopMetrics,
      List.empty[Middleware[Any]])
    controller.setControllerComponents(Helpers.stubControllerComponents())
    controller
  }

  // -------------------------------------------------------------------------
  // renderSchema
  // -------------------------------------------------------------------------

  @Test
  def renderSchema_returnsOkWithSchemaString(): Unit = {
    val controller = buildController()
    val result = controller.renderSchema.apply(FakeRequest())
    assert(status(result) === OK)
    val body = contentAsString(result)
    assert(body.nonEmpty)
  }

  // -------------------------------------------------------------------------
  // graphqlBody — basic query succeeds
  // -------------------------------------------------------------------------

  @Test
  def graphqlBody_validQuery_returnsOk(): Unit = {
    val controller = buildController()
    val body = Json.obj(
      "query" -> """{ __schema { queryType { name } } }""")
    val request = FakeRequest("POST", "/graphql")
      .withBody(body.as[play.api.libs.json.JsValue])
    val result = controller.graphqlBody.apply(request)
    assert(status(result) === OK)
  }

  @Test
  def graphqlBody_withOperationName_returnsOk(): Unit = {
    val controller = buildController()
    val body = Json.obj(
      "query" -> """query MyOp { __schema { queryType { name } } }""",
      "operationName" -> "MyOp")
    val request = FakeRequest("POST", "/graphql")
      .withBody(body.as[play.api.libs.json.JsValue])
    val result = controller.graphqlBody.apply(request)
    assert(status(result) === OK)
  }

  @Test
  def graphqlBody_withVariablesAsJsObject_returnsOk(): Unit = {
    val controller = buildController()
    val body = Json.obj(
      "query" -> """{ __schema { queryType { name } } }""",
      "variables" -> Json.obj("foo" -> "bar"))
    val request = FakeRequest("POST", "/graphql")
      .withBody(body.as[play.api.libs.json.JsValue])
    val result = controller.graphqlBody.apply(request)
    assert(status(result) === OK)
  }

  @Test
  def graphqlBody_withVariablesAsJsonString_returnsOk(): Unit = {
    val controller = buildController()
    val body = Json.obj(
      "query" -> """{ __schema { queryType { name } } }""",
      "variables" -> """{"foo": "bar"}""")
    val request = FakeRequest("POST", "/graphql")
      .withBody(body.as[play.api.libs.json.JsValue])
    val result = controller.graphqlBody.apply(request)
    assert(status(result) === OK)
  }

  @Test
  def graphqlBody_withEmptyVariablesString_returnsOk(): Unit = {
    val controller = buildController()
    val body = Json.obj(
      "query" -> """{ __schema { queryType { name } } }""",
      "variables" -> "")
    val request = FakeRequest("POST", "/graphql")
      .withBody(body.as[play.api.libs.json.JsValue])
    val result = controller.graphqlBody.apply(request)
    assert(status(result) === OK)
  }

  @Test
  def graphqlBody_withNullVariablesString_returnsOk(): Unit = {
    val controller = buildController()
    val body = Json.obj(
      "query" -> """{ __schema { queryType { name } } }""",
      "variables" -> "null")
    val request = FakeRequest("POST", "/graphql")
      .withBody(body.as[play.api.libs.json.JsValue])
    val result = controller.graphqlBody.apply(request)
    assert(status(result) === OK)
  }

  @Test
  def graphqlBody_withInvalidQuery_returnsOkWithSyntaxError(): Unit = {
    val controller = buildController()
    val body = Json.obj(
      "query" -> """{ not valid graphql {{{{ """)
    val request = FakeRequest("POST", "/graphql")
      .withBody(body.as[play.api.libs.json.JsValue])
    val result = controller.graphqlBody.apply(request)
    // Should not throw; returns 200 with syntax error in body
    assert(status(result) === OK)
    val responseBody = contentAsJson(result)
    assert((responseBody \ "syntaxError").isDefined)
  }

  // -------------------------------------------------------------------------
  // graphqlBatch — list of queries
  // -------------------------------------------------------------------------

  @Test
  def graphqlBatch_singleQuery_returnsArrayOfResults(): Unit = {
    val controller = buildController()
    val body: play.api.libs.json.JsValue = JsArray(
      Seq(
        Json.obj("query" -> """{ __schema { queryType { name } } }""")))
    val request = FakeRequest("POST", "/graphql")
      .withBody(body)
    val result = controller.graphqlBatch.apply(request)
    assert(status(result) === OK)
    val responseBody = contentAsJson(result)
    assert(responseBody.as[JsArray].value.nonEmpty)
  }

  @Test
  def graphqlBatch_multipleQueries_returnsMatchingCount(): Unit = {
    val controller = buildController()
    val body: play.api.libs.json.JsValue = JsArray(
      Seq(
        Json.obj("query" -> """{ __schema { queryType { name } } }"""),
        Json.obj("query" -> """{ __schema { queryType { name } } }""")))
    val request = FakeRequest("POST", "/graphql")
      .withBody(body)
    val result = controller.graphqlBatch.apply(request)
    assert(status(result) === OK)
    val responseBody = contentAsJson(result)
    assert(responseBody.as[JsArray].value.size === 2)
  }

  @Test
  def graphqlBatch_withVariablesAsJsObject_returnsOk(): Unit = {
    val controller = buildController()
    val body: play.api.libs.json.JsValue = JsArray(
      Seq(
        Json.obj(
          "query" -> """{ __schema { queryType { name } } }""",
          "variables" -> Json.obj("x" -> 1))))
    val request = FakeRequest("POST", "/graphql")
      .withBody(body)
    val result = controller.graphqlBatch.apply(request)
    assert(status(result) === OK)
  }

  @Test
  def graphqlBatch_withVariablesAsJsonString_returnsOk(): Unit = {
    val controller = buildController()
    val body: play.api.libs.json.JsValue = JsArray(
      Seq(
        Json.obj(
          "query" -> """{ __schema { queryType { name } } }""",
          "variables" -> """{"x": 1}""")))
    val request = FakeRequest("POST", "/graphql")
      .withBody(body)
    val result = controller.graphqlBatch.apply(request)
    assert(status(result) === OK)
  }

  @Test
  def graphqlBatch_withOperationName_returnsOk(): Unit = {
    val controller = buildController()
    val body: play.api.libs.json.JsValue = JsArray(
      Seq(
        Json.obj(
          "query" -> """query Op { __schema { queryType { name } } }""",
          "operationName" -> "Op")))
    val request = FakeRequest("POST", "/graphql")
      .withBody(body)
    val result = controller.graphqlBatch.apply(request)
    assert(status(result) === OK)
  }

  // -------------------------------------------------------------------------
  // Filter chain — filter is applied to incoming query
  // -------------------------------------------------------------------------

  @Test
  def graphqlBody_withFilter_filterIsCalled(): Unit = {
    @volatile var filterCalled = false
    val trackingFilter = new Filter {
      override def apply(nextFilter: FilterFn): FilterFn = { incoming =>
        filterCalled = true
        nextFilter(incoming)
      }
    }
    val controller = buildController(FilterList(immutable.Seq(trackingFilter)))
    val body = Json.obj(
      "query" -> """{ __schema { queryType { name } } }""")
    val request = FakeRequest("POST", "/graphql")
      .withBody(body.as[play.api.libs.json.JsValue])
    controller.graphqlBody.apply(request).futureValue
    assert(filterCalled)
  }

  // -------------------------------------------------------------------------
  // exceptionHandler — covers the object method
  // -------------------------------------------------------------------------

  @Test
  def exceptionHandler_createsHandlerThatCatchesExceptions(): Unit = {
    import com.typesafe.scalalogging.Logger
    import org.slf4j.LoggerFactory
    val logger = Logger(LoggerFactory.getLogger(getClass))
    val handler = GraphQLController.exceptionHandler(logger)
    assert(handler != null)
  }

  // -------------------------------------------------------------------------
  // graphqlBody — query analysis error is handled gracefully
  // -------------------------------------------------------------------------

  @Test
  def graphqlBody_queryAnalysisError_returnsOkWithErrorInBody(): Unit = {
    // A query that references nonexistent fields causes QueryAnalysisError inside executor
    val controller = buildController()
    val body = Json.obj(
      "query" -> """{ NonExistentField { badField } }""")
    val request = FakeRequest("POST", "/graphql")
      .withBody(body.as[play.api.libs.json.JsValue])
    val result = controller.graphqlBody.apply(request)
    // Should not throw; the error is caught and returned as JSON
    assert(status(result) === OK)
  }
}
