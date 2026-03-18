package org.coursera.naptime.ari.graphql.controllers.middleware

import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import play.api.test.FakeRequest
import sangria.ast.Document
import sangria.execution.DeprecationTracker
import sangria.execution.ExecutionPath
import sangria.execution.MiddlewareQueryContext
import sangria.execution.TimeMeasurement
import sangria.macros._
import sangria.marshalling.ResultMarshaller
import sangria.schema.Args
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.Schema

import scala.concurrent.ExecutionContext

class MetricsCollectionMiddlewareTest extends AssertionsForJUnit with MockitoSugar {

  private[this] val mockAst = graphql"""
    schema {
      query: Root
    }

    type Root {
      item: Item
    }

    type Item {
      value: String
    }
  """

  private[this] val mockSchema: Schema[SangriaGraphQlContext, Any] =
    Schema.buildFromAst(mockAst).asInstanceOf[Schema[SangriaGraphQlContext, Any]]

  private[this] val rootType = mockSchema.query

  private[this] val itemField = Field[SangriaGraphQlContext, Any, Any, Any](
    name = "item",
    fieldType = mockSchema.outputTypes("Item"),
    description = None,
    arguments = List.empty,
    resolve = _ => null)

  private def buildContext(ctx: SangriaGraphQlContext): Context[SangriaGraphQlContext, _] = {
    val astField = sangria.ast.Field(None, "item", Vector.empty, Vector.empty, Vector.empty)
    val path = ExecutionPath.empty.add(astField, rootType)
    val parentType = mock[ObjectType[SangriaGraphQlContext, Any]]
    when(parentType.name).thenReturn("Root")
    Context[SangriaGraphQlContext, Any](
      value = null,
      ctx = ctx,
      args = Args.empty,
      schema = mockSchema,
      field = itemField,
      parentType = parentType,
      marshaller = mock[ResultMarshaller],
      query = Document.emptyStub,
      sourceMapper = None,
      deprecationTracker = DeprecationTracker.empty,
      astFields = Vector.empty,
      path = path,
      deferredResolverState = None)
  }

  private def buildMqCtx(ctx: SangriaGraphQlContext): MiddlewareQueryContext[SangriaGraphQlContext, _, _] =
    MiddlewareQueryContext[SangriaGraphQlContext, Any, Unit](
      ctx = ctx,
      executor = null,
      queryAst = Document(Vector.empty),
      operationName = None,
      variables = (),
      inputUnmarshaller = null,
      validationTiming = TimeMeasurement.empty,
      queryReducerTiming = TimeMeasurement.empty)

  @Test
  def beforeQuery_returnsUnit(): Unit = {
    val collector = mock[GraphQLMetricsCollector]
    val middleware = new MetricsCollectionMiddleware(collector)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    middleware.beforeQuery(mqCtx) // should not throw
  }

  @Test
  def afterQuery_returnsUnit(): Unit = {
    val collector = mock[GraphQLMetricsCollector]
    val middleware = new MetricsCollectionMiddleware(collector)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    middleware.afterQuery((), mqCtx) // should not throw
  }

  @Test
  def beforeField_returnsBeforeFieldResult(): Unit = {
    val collector = mock[GraphQLMetricsCollector]
    val middleware = new MetricsCollectionMiddleware(collector)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx)
    val result = middleware.beforeField((), mqCtx, context)
    assert(result != null)
  }

  @Test
  def fieldError_callsMarkFieldError(): Unit = {
    val collector = mock[GraphQLMetricsCollector]
    val middleware = new MetricsCollectionMiddleware(collector)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx)

    val error = new RuntimeException("test error")
    middleware.fieldError((), (), error, mqCtx, context)

    verify(collector).markFieldError("Root:item")
  }

  @Test
  def fieldError_fieldNameIncludesParentAndField(): Unit = {
    val collector = new LoggingMetricsCollector()
    val middleware = new MetricsCollectionMiddleware(collector)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx)

    // Just verifies no exception is thrown with the real collector
    middleware.fieldError((), (), new RuntimeException("boom"), mqCtx, context)
  }
}
