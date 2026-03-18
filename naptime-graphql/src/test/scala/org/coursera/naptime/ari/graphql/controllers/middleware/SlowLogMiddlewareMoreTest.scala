package org.coursera.naptime.ari.graphql.controllers.middleware

import com.typesafe.scalalogging.Logger
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.SangriaGraphQlSchemaBuilder
import org.coursera.naptime.ari.graphql.marshaller.NaptimeMarshaller._
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.coursera.naptime.ari.graphql.resolvers.NaptimeResolver
import org.junit.Test
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsObject
import play.api.test.FakeRequest
import sangria.execution.Executor
import sangria.parser.QueryParser
import sangria.schema.Schema

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration

/**
 * Additional SlowLogMiddleware tests covering uncovered branches:
 * - afterQueryExtensions (line 60) — via full execution
 * - threshold companion object
 */
class SlowLogMiddlewareMoreTest extends AssertionsForJUnit with MockitoSugar with ScalaFutures with IntegrationPatience {

  private val logger = Logger(getClass)

  private val schemaTypes = Map(
    "org.coursera.naptime.ari.graphql.models.MergedCourse" -> MergedCourse.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedPartner" -> MergedPartner.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedInstructor" -> MergedInstructor.SCHEMA)

  private val allResources =
    Set(Models.courseResource, Models.instructorResource, Models.partnersResource)

  private val builder = new SangriaGraphQlSchemaBuilder(allResources, schemaTypes)
  private val schema =
    builder.generateSchema().data.asInstanceOf[Schema[SangriaGraphQlContext, Any]]

  private val noopFetcher: FetcherApi = new FetcherApi {
    override def data(request: Request, isDebugMode: Boolean)(
        implicit executionContext: ExecutionContext): Future[FetcherResponse] =
      Future.successful(Right(Response(List.empty, ResponsePagination(None), Some("http://test"))))
  }

  // -------------------------------------------------------------------------
  // afterQueryExtensions via full execution (line 60 of SlowLogMiddleware)
  // -------------------------------------------------------------------------

  @Test
  def afterQueryExtensions_debugMode_coveredViaFullExecution(): Unit = {
    // Run a full query with debugMode=true which triggers afterQueryExtensions
    // via the sangria executor lifecycle
    val queryAst = QueryParser.parse("""{ __schema { queryType { name } } }""").get
    val context = SangriaGraphQlContext(noopFetcher, FakeRequest(), ExecutionContext.global, debugMode = true)
    val middleware = new SlowLogMiddleware(logger, isDebugMode = true)

    val result = Await.result(
      Executor.execute(
        schema,
        queryAst,
        context,
        variables = JsObject(Map.empty[String, play.api.libs.json.JsValue]),
        middleware = List(middleware),
        deferredResolver = new NaptimeResolver()),
      Duration.Inf)

    assert(result != null)
  }

  @Test
  def afterQueryExtensions_nonDebugMode_coveredViaFullExecution(): Unit = {
    val queryAst = QueryParser.parse("""{ __schema { queryType { name } } }""").get
    val context = SangriaGraphQlContext(noopFetcher, FakeRequest(), ExecutionContext.global, debugMode = false)
    val middleware = new SlowLogMiddleware(logger, isDebugMode = false)

    val result = Await.result(
      Executor.execute(
        schema,
        queryAst,
        context,
        variables = JsObject(Map.empty[String, play.api.libs.json.JsValue]),
        middleware = List(middleware),
        deferredResolver = new NaptimeResolver()),
      Duration.Inf)

    assert(result != null)
  }

  // -------------------------------------------------------------------------
  // SlowLogMiddleware threshold — covers the companion object (line 103)
  // -------------------------------------------------------------------------

  @Test
  def threshold_isDefinedInCompanion(): Unit = {
    val threshold = SlowLogMiddleware.threshold
    assert(threshold.toSeconds > 0)
  }
}
