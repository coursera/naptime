package org.coursera.naptime.ari.graphql.middleware

import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.controllers.middleware.ResponseMetadataMiddleware
import org.coursera.naptime.ari.graphql.marshaller.NaptimeMarshaller._
import org.coursera.naptime.ari.graphql.resolvers.NaptimeError
import org.coursera.naptime.ari.graphql.schema.DataMapWithParent
import org.coursera.naptime.ari.graphql.schema.NaptimeResolveException
import org.coursera.naptime.ari.graphql.schema.ParentModel
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.Headers
import play.api.test.FakeRequest
import sangria.ast.Document
import sangria.execution.DeprecationTracker
import sangria.execution.ExecutionPath
import sangria.execution.MiddlewareQueryContext
import sangria.execution.ResultResolver
import sangria.execution.TimeMeasurement
import sangria.macros._
import sangria.marshalling.ResultMarshaller
import sangria.schema.Args
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.Schema

import scala.concurrent.ExecutionContext

class ResponseMetadataMiddlewareTest extends AssertionsForJUnit with MockitoSugar {

  private[this] val mockAst = graphql"""
    schema {
      query: Root
    }

    type Root {
      course: Course
    }

    type Course {
      slug: String
    }
  """

  private[this] val mockSchema: Schema[SangriaGraphQlContext, Any] =
    Schema.buildFromAst(mockAst).asInstanceOf[Schema[SangriaGraphQlContext, Any]]

  private[this] val rootType = mockSchema.query

  private[this] val courseField = Field[SangriaGraphQlContext, Any, Any, Any](
    name = "course",
    fieldType = mockSchema.outputTypes("Course"),
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
      field = courseField,
      parentType = mock[ObjectType[SangriaGraphQlContext, Any]],
      marshaller = mock[ResultMarshaller],
      query = Document.emptyStub,
      sourceMapper = None,
      deprecationTracker = DeprecationTracker.empty,
      astFields = Vector.empty,
      path = executionPath,
      deferredResolverState = None)

  private def buildMqCtx(
      ctx: SangriaGraphQlContext): MiddlewareQueryContext[SangriaGraphQlContext, _, _] =
    MiddlewareQueryContext[SangriaGraphQlContext, Any, Unit](
      ctx = ctx,
      executor = null,
      queryAst = Document(Vector.empty),
      operationName = None,
      variables = (),
      inputUnmarshaller = null,
      validationTiming = TimeMeasurement.empty,
      queryReducerTiming = TimeMeasurement.empty)

  private def makePath = {
    val astField = sangria.ast.Field(None, "course", Vector.empty, Vector.empty, Vector.empty)
    ExecutionPath.empty.add(astField, rootType)
  }

  // -------------------------------------------------------------------------
  // afterField – DataMapWithParent with sourceUrl (debug mode)
  // -------------------------------------------------------------------------

  @Test
  def afterField_dataMapWithParent_withSourceUrl_recordsMetadata(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    val dm = new com.linkedin.data.DataMap()
    dm.put("id", "123")
    val parentModel = mock[ParentModel]
    val value = DataMapWithParent(dm, parentModel, sourceUrl = Some("http://example.com/api"))

    middleware.afterField((), (), value, mqCtx, context)
    val extensions = middleware.afterQueryExtensions((), mqCtx)
    assert(!extensions.isEmpty)
    val marshalled =
      ResultResolver.marshalExtensions(PlayJsonMarshallerForType.marshaller, extensions).get
    assert(marshalled.toString.contains("responseMetadata"))
    assert(marshalled.toString.contains("http://example.com/api"))
  }

  // -------------------------------------------------------------------------
  // afterField – Some(DataMapWithParent) with sourceUrl (debug mode)
  // -------------------------------------------------------------------------

  @Test
  def afterField_someDataMapWithParent_withSourceUrl_recordsMetadata(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    val dm = new com.linkedin.data.DataMap()
    val parentModel = mock[ParentModel]
    val inner = DataMapWithParent(dm, parentModel, sourceUrl = Some("http://example.com/some"))
    val value = Some(inner)

    middleware.afterField((), (), value, mqCtx, context)
    val extensions = middleware.afterQueryExtensions((), mqCtx)
    assert(!extensions.isEmpty)
    val marshalled =
      ResultResolver.marshalExtensions(PlayJsonMarshallerForType.marshaller, extensions).get
    assert(marshalled.toString.contains("http://example.com/some"))
  }

  // -------------------------------------------------------------------------
  // afterField – DataMapWithParent with no sourceUrl (debug mode)
  // -------------------------------------------------------------------------

  @Test
  def afterField_dataMapWithParent_noSourceUrl_doesNotRecord(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    val dm = new com.linkedin.data.DataMap()
    val parentModel = mock[ParentModel]
    val value = DataMapWithParent(dm, parentModel, sourceUrl = None)

    middleware.afterField((), (), value, mqCtx, context)
    // no urls recorded → extensions may be present but no field entries
    val extensions = middleware.afterQueryExtensions((), mqCtx)
    val marshalled =
      ResultResolver.marshalExtensions(PlayJsonMarshallerForType.marshaller, extensions).get
    // responseMetadata object should be empty
    val rm = marshalled.as[play.api.libs.json.JsObject].value("responseMetadata")
    assert(rm === play.api.libs.json.Json.obj())
  }

  // -------------------------------------------------------------------------
  // afterField – non-debug mode does nothing
  // -------------------------------------------------------------------------

  @Test
  def afterField_notDebugMode_noExtensions(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    val dm = new com.linkedin.data.DataMap()
    val parentModel = mock[ParentModel]
    val value = DataMapWithParent(dm, parentModel, sourceUrl = Some("http://example.com"))

    middleware.afterField((), (), value, mqCtx, context)
    val extensions = middleware.afterQueryExtensions((), mqCtx)
    assert(extensions.isEmpty)
  }

  // -------------------------------------------------------------------------
  // afterField – unrecognised value (debug mode) → None returned
  // -------------------------------------------------------------------------

  @Test
  def afterField_unknownValue_returnsNone(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    val result = middleware.afterField((), (), "random string value", mqCtx, context)
    assert(result === None)
  }

  // -------------------------------------------------------------------------
  // fieldError – NaptimeResolveException (debug mode)
  // -------------------------------------------------------------------------

  @Test
  def fieldError_naptimeResolveException_debugMode_recordsError(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    val error = NaptimeResolveException(NaptimeError("http://err.com", 500, "server error"))
    middleware.fieldError((), (), error, mqCtx, context)

    val extensions = middleware.afterQueryExtensions((), mqCtx)
    assert(!extensions.isEmpty)
    val marshalled =
      ResultResolver.marshalExtensions(PlayJsonMarshallerForType.marshaller, extensions).get
    assert(marshalled.toString.contains("responseMetadata"))
  }

  // -------------------------------------------------------------------------
  // fieldError – NaptimeResolveException with JSON error message (debug mode)
  // -------------------------------------------------------------------------

  @Test
  def fieldError_naptimeResolveExceptionWithJsonErrorMessage_debugMode(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    val jsonErrorMessage = """{"code":"NOT_FOUND","message":"resource not found"}"""
    val error = NaptimeResolveException(NaptimeError("http://err.com", 404, jsonErrorMessage))
    middleware.fieldError((), (), error, mqCtx, context)

    val extensions = middleware.afterQueryExtensions((), mqCtx)
    assert(!extensions.isEmpty)
  }

  // -------------------------------------------------------------------------
  // fieldError – non-NaptimeResolveException (debug mode) → does nothing
  // -------------------------------------------------------------------------

  @Test
  def fieldError_otherException_debugMode_doesNothing(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    val error = new RuntimeException("some other error")
    // should not throw
    middleware.fieldError((), (), error, mqCtx, context)
  }

  // -------------------------------------------------------------------------
  // fieldError – non-debug mode does nothing
  // -------------------------------------------------------------------------

  @Test
  def fieldError_notDebugMode_doesNothing(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    val error = NaptimeResolveException(NaptimeError("http://err.com", 500, "err"))
    // should not throw and extensions should be empty
    middleware.fieldError((), (), error, mqCtx, context)
    val extensions = middleware.afterQueryExtensions((), mqCtx)
    assert(extensions.isEmpty)
  }

  // -------------------------------------------------------------------------
  // afterQueryExtensions – debug mode with NaptimeResponse (covers parseToAst)
  // -------------------------------------------------------------------------

  @Test
  def afterQueryExtensions_withNaptimeResponse_withJsonErrorMessage(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    // Trigger afterField with a NaptimeResponse that includes a JSON error message
    import org.coursera.naptime.ari.graphql.resolvers.NaptimeResponse
    val value = NaptimeResponse(
      elements = List.empty,
      pagination = None,
      url = "http://test.com",
      status = 422,
      errorMessage = Some("""{"errors":[{"msg":"invalid"}]}"""))
    middleware.afterField((), (), value, mqCtx, context)

    val extensions = middleware.afterQueryExtensions((), mqCtx)
    assert(!extensions.isEmpty)
    val marshalled =
      ResultResolver.marshalExtensions(PlayJsonMarshallerForType.marshaller, extensions).get
    assert(marshalled.toString.contains("responseMetadata"))
    assert(marshalled.toString.contains("422"))
  }

  // -------------------------------------------------------------------------
  // afterQueryExtensions – parseToAst branches: JsArray, JsNumber, JsNull
  // -------------------------------------------------------------------------

  @Test
  def afterQueryExtensions_withNaptimeResponse_parseToAstJsArray(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    import org.coursera.naptime.ari.graphql.resolvers.NaptimeResponse
    import play.api.libs.json.{JsArray, JsNumber => PlayJsNumber}
    // errorMessage containing a JsArray (covers the JsArray parseToAst branch)
    val errorJson = play.api.libs.json.Json.arr("err1", "err2").toString()
    val value = NaptimeResponse(
      elements = List.empty,
      pagination = None,
      url = "http://test.com",
      status = 400,
      errorMessage = Some(errorJson))
    middleware.afterField((), (), value, mqCtx, context)

    val extensions = middleware.afterQueryExtensions((), mqCtx)
    assert(!extensions.isEmpty)
    // Should not throw during marshalling
    val marshalled =
      ResultResolver.marshalExtensions(PlayJsonMarshallerForType.marshaller, extensions).get
    assert(marshalled.toString.contains("responseMetadata"))
  }

  @Test
  def afterQueryExtensions_withNaptimeResponse_parseToAstJsNumber(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    import org.coursera.naptime.ari.graphql.resolvers.NaptimeResponse
    // errorMessage containing a JsNumber (covers the JsNumber parseToAst branch)
    val errorJson = play.api.libs.json.Json.obj("code" -> 42).toString()
    val value = NaptimeResponse(
      elements = List.empty,
      pagination = None,
      url = "http://test.com",
      status = 400,
      errorMessage = Some(errorJson))
    middleware.afterField((), (), value, mqCtx, context)

    val extensions = middleware.afterQueryExtensions((), mqCtx)
    assert(!extensions.isEmpty)
    val marshalled =
      ResultResolver.marshalExtensions(PlayJsonMarshallerForType.marshaller, extensions).get
    assert(marshalled.toString.contains("responseMetadata"))
  }

  @Test
  def afterQueryExtensions_withNaptimeResponse_parseToAstFallback(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = true)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)

    import org.coursera.naptime.ari.graphql.resolvers.NaptimeResponse
    // errorMessage containing JsBoolean which hits the fallback (JsString) parseToAst branch
    val errorJson = play.api.libs.json.Json.obj("flag" -> true).toString()
    val value = NaptimeResponse(
      elements = List.empty,
      pagination = None,
      url = "http://test.com",
      status = 400,
      errorMessage = Some(errorJson))
    middleware.afterField((), (), value, mqCtx, context)

    val extensions = middleware.afterQueryExtensions((), mqCtx)
    assert(!extensions.isEmpty)
    val marshalled =
      ResultResolver.marshalExtensions(PlayJsonMarshallerForType.marshaller, extensions).get
    assert(marshalled.toString.contains("responseMetadata"))
  }

  // -------------------------------------------------------------------------
  // beforeQuery, afterQuery, beforeField — cover trivial return paths
  // -------------------------------------------------------------------------

  @Test
  def beforeQuery_returnsUnit(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    val result = middleware.beforeQuery(mqCtx)
    assert(result == (()))
  }

  @Test
  def afterQuery_returnsUnit(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    middleware.afterQuery((), mqCtx) // just assert no throw
  }

  @Test
  def beforeField_returnsBeforeFieldResult(): Unit = {
    val middleware = new ResponseMetadataMiddleware()
    val ctx = SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
    val mqCtx = buildMqCtx(ctx)
    val context = buildContext(ctx, makePath)
    val result = middleware.beforeField((), mqCtx, context)
    assert(result != null)
  }
}
