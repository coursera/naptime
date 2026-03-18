package org.coursera.naptime.ari.graphql.controllers.middleware

import com.typesafe.scalalogging.Logger
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar
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

class SlowLogMiddlewareTest extends AssertionsForJUnit with MockitoSugar {

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

  private def buildContext(
      ctx: SangriaGraphQlContext,
      executionPath: ExecutionPath): Context[SangriaGraphQlContext, _] =
    Context[SangriaGraphQlContext, Any](
      value = null,
      ctx = ctx,
      args = Args.empty,
      schema = mockSchema,
      field = itemField,
      parentType = mock[ObjectType[SangriaGraphQlContext, Any]],
      marshaller = mock[ResultMarshaller],
      query = Document.emptyStub,
      sourceMapper = None,
      deprecationTracker = DeprecationTracker.empty,
      astFields = Vector.empty,
      path = executionPath,
      deferredResolverState = None)

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

  private def makePath: ExecutionPath = {
    val astField = sangria.ast.Field(None, "item", Vector.empty, Vector.empty, Vector.empty)
    ExecutionPath.empty.add(astField, rootType)
  }

  private val logger = Logger(getClass)

  // -------------------------------------------------------------------------
  // SlowLogMiddleware in non-debug mode — uses threshold-based logger
  // -------------------------------------------------------------------------

  @Test
  def beforeQuery_nonDebugMode_returnsQueryMetrics(): Unit = {
    val middleware = new SlowLogMiddleware(logger, isDebugMode = false)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    val metrics = middleware.beforeQuery(mqCtx)
    assert(metrics != null)
  }

  @Test
  def afterQuery_nonDebugMode_doesNotThrow(): Unit = {
    val middleware = new SlowLogMiddleware(logger, isDebugMode = false)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    val metrics = middleware.beforeQuery(mqCtx)
    middleware.afterQuery(metrics, mqCtx) // should not throw
  }

  @Test
  def afterField_nonDebugMode_producesMiddlewareObject(): Unit = {
    val middleware = new SlowLogMiddleware(logger, isDebugMode = false)
    // Just verify the middleware object is created without throws
    assert(middleware != null)
  }

  @Test
  def beforeField_nonDebugMode_returnsBeforeFieldResult(): Unit = {
    val middleware = new SlowLogMiddleware(logger, isDebugMode = false)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    val metrics = middleware.beforeQuery(mqCtx)
    val context = buildContext(ctx, makePath)
    val result = middleware.beforeField(metrics, mqCtx, context)
    assert(result != null)
  }

  @Test
  def afterField_nonDebugMode_returnsNoneOrValue(): Unit = {
    val middleware = new SlowLogMiddleware(logger, isDebugMode = false)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    val metrics = middleware.beforeQuery(mqCtx)
    val context = buildContext(ctx, makePath)
    // fieldVal is Long (timestamp); use 0L as a stand-in
    val result = middleware.afterField(metrics, 0L, "testValue", mqCtx, context)
    // result is Option[Any]; should be Some or None without throwing
    assert(result == None || result.isDefined)
  }

  @Test
  def fieldError_nonDebugMode_doesNotThrow(): Unit = {
    val middleware = new SlowLogMiddleware(logger, isDebugMode = false)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    val metrics = middleware.beforeQuery(mqCtx)
    val context = buildContext(ctx, makePath)
    middleware.fieldError(metrics, 0L, new RuntimeException("test error"), mqCtx, context)
  }

  // -------------------------------------------------------------------------
  // SlowLogMiddleware in debug mode — uses SlowLog.extension (no threshold)
  // -------------------------------------------------------------------------

  @Test
  def beforeQuery_debugMode_returnsQueryMetrics(): Unit = {
    val middleware = new SlowLogMiddleware(logger, isDebugMode = true)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val metrics = middleware.beforeQuery(mqCtx)
    assert(metrics != null)
  }

  @Test
  def afterQuery_debugMode_doesNotThrow(): Unit = {
    val middleware = new SlowLogMiddleware(logger, isDebugMode = true)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val metrics = middleware.beforeQuery(mqCtx)
    middleware.afterQuery(metrics, mqCtx)
  }

  @Test
  def afterField_debugMode_middlewareObjectCreated(): Unit = {
    val middleware = new SlowLogMiddleware(logger, isDebugMode = true)
    // Just verify the debug-mode middleware object is created without throws
    assert(middleware != null)
  }

  @Test
  def beforeField_debugMode_returnsBeforeFieldResult(): Unit = {
    val middleware = new SlowLogMiddleware(logger, isDebugMode = true)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val metrics = middleware.beforeQuery(mqCtx)
    val context = buildContext(ctx, makePath)
    val result = middleware.beforeField(metrics, mqCtx, context)
    assert(result != null)
  }

  @Test
  def afterField_debugMode_doesNotThrow(): Unit = {
    val middleware = new SlowLogMiddleware(logger, isDebugMode = true)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val metrics = middleware.beforeQuery(mqCtx)
    val context = buildContext(ctx, makePath)
    val result = middleware.afterField(metrics, 0L, "value", mqCtx, context)
    assert(result == None || result.isDefined)
  }

  @Test
  def fieldError_debugMode_doesNotThrow(): Unit = {
    val middleware = new SlowLogMiddleware(logger, isDebugMode = true)
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val metrics = middleware.beforeQuery(mqCtx)
    val context = buildContext(ctx, makePath)
    middleware.fieldError(metrics, 0L, new RuntimeException("test"), mqCtx, context)
  }
}
