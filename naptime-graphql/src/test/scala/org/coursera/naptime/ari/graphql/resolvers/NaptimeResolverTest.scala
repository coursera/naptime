package org.coursera.naptime.ari.graphql.resolvers

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.template.DataTemplateUtil
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.schema.DataMapWithParent
import org.coursera.naptime.ari.graphql.schema.ParentModel
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.test.FakeRequest

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class NaptimeResolverTest extends AssertionsForJUnit with MockitoSugar {

  import scala.concurrent.ExecutionContext.Implicits.global

  private val resolver = new NaptimeResolver()

  private val resourceName = ResourceName("courses", 1)
  // RecordDataSchema is final — use DataTemplateUtil to parse a minimal schema
  private val realSchema =
    DataTemplateUtil
      .parseSchema("""{"name":"TestRecord","type":"record","fields":[]}""")
      .asInstanceOf[RecordDataSchema]

  private def makeRequest(idx: Int, args: Set[(String, JsValue)] = Set.empty): NaptimeRequest =
    NaptimeRequest(
      idx = RequestId(idx),
      resourceName = resourceName,
      arguments = args,
      resourceSchema = realSchema)

  // -------------------------------------------------------------------------
  // getResourceName
  // -------------------------------------------------------------------------

  @Test
  def getResourceName_emptyRequests_returnsNone(): Unit = {
    val result = resolver.getResourceName(Vector.empty)
    assert(result === None)
  }

  @Test
  def getResourceName_singleResource_returnsThatResource(): Unit = {
    val requests = Vector(makeRequest(0), makeRequest(1))
    val result = resolver.getResourceName(requests)
    assert(result === Some(resourceName))
  }

  @Test
  def getResourceName_multipleResources_returnsNone(): Unit = {
    val r1 = NaptimeRequest(RequestId(0), ResourceName("courses", 1), Set.empty, realSchema)
    val r2 = NaptimeRequest(RequestId(1), ResourceName("instructors", 1), Set.empty, realSchema)
    val result = resolver.getResourceName(Vector(r1, r2))
    assert(result === None)
  }

  // -------------------------------------------------------------------------
  // parseElements
  // -------------------------------------------------------------------------

  @Test
  def parseElements_emptyResponse_returnsEmptyList(): Unit = {
    val request = Request(FakeRequest(), resourceName, Set.empty, None)
    val response = Response(List.empty, ResponsePagination(None), None)
    val elements = resolver.parseElements(request, response, realSchema)
    assert(elements.isEmpty)
  }

  @Test
  def parseElements_withData_returnsDataMapWithParent(): Unit = {
    val dm = new DataMap()
    dm.put("id", "abc")
    val request = Request(FakeRequest(), resourceName, Set.empty, None)
    val response = Response(List(dm), ResponsePagination(None), Some("http://test.com"))
    val elements = resolver.parseElements(request, response, realSchema)
    assert(elements.size === 1)
    assert(elements.head.element === dm)
    assert(elements.head.parentModel.resourceName === resourceName)
  }

  // -------------------------------------------------------------------------
  // fetchNonMultiGetRelations - success
  // -------------------------------------------------------------------------

  @Test
  def fetchNonMultiGetRelations_successfulResponse_mapsToRight(): Unit = {
    val dm = new DataMap()
    dm.put("id", "x")
    val responsePagination = ResponsePagination(None)

    val fetcher = new FetcherApi {
      override def data(request: Request, isDebugMode: Boolean)(
          implicit executionContext: ExecutionContext): Future[FetcherApi#FetcherResponse] = {
        Future.successful(Right(Response(List(dm), responsePagination, Some("http://ok.com"))))
      }
    }

    val ctx =
      SangriaGraphQlContext(fetcher, FakeRequest(), ExecutionContext.global, debugMode = false)
    val requests = Vector(makeRequest(0))

    val resultFut = resolver.fetchNonMultiGetRelations(requests, resourceName, ctx)
    val result = Await.result(resultFut, Duration("5 seconds"))

    assert(result.size === 1)
    result(RequestId(0)) match {
      case Right(NaptimeResponse(elements, _, url, 200, None)) =>
        assert(elements.size === 1)
        assert(url === "http://ok.com")
      case other => fail(s"Unexpected: $other")
    }
  }

  // -------------------------------------------------------------------------
  // fetchNonMultiGetRelations - error response
  // -------------------------------------------------------------------------

  @Test
  def fetchNonMultiGetRelations_errorResponse_mapsToLeft(): Unit = {
    import org.coursera.naptime.ari.FetcherError

    val fetcher = new FetcherApi {
      override def data(request: Request, isDebugMode: Boolean)(
          implicit executionContext: ExecutionContext): Future[FetcherApi#FetcherResponse] = {
        Future.successful(Left(FetcherError(404, "not found", Some("http://fail.com"))))
      }
    }

    val ctx =
      SangriaGraphQlContext(fetcher, FakeRequest(), ExecutionContext.global, debugMode = false)
    val requests = Vector(makeRequest(0))

    val resultFut = resolver.fetchNonMultiGetRelations(requests, resourceName, ctx)
    val result = Await.result(resultFut, Duration("5 seconds"))

    assert(result.size === 1)
    result(RequestId(0)) match {
      case Left(NaptimeError(url, 404, msg)) =>
        assert(url === "http://fail.com")
        assert(msg === "not found")
      case other => fail(s"Unexpected: $other")
    }
  }

  // -------------------------------------------------------------------------
  // DeferredNaptimeElement.toNaptimeRequest – with idOpt
  // -------------------------------------------------------------------------

  @Test
  def deferredNaptimeElement_withId_includesIdInArguments(): Unit = {
    val elem = DeferredNaptimeElement(
      resourceName = resourceName,
      idOpt = Some(JsString("abc")),
      arguments = Set.empty,
      resourceSchema = realSchema)
    val req = elem.toNaptimeRequest(5)
    assert(req.arguments.exists { case (k, v) => k == "ids" })
  }

  @Test
  def deferredNaptimeElement_withoutId_hasNoIdsArgument(): Unit = {
    val elem = DeferredNaptimeElement(
      resourceName = resourceName,
      idOpt = None,
      arguments = Set.empty,
      resourceSchema = realSchema)
    val req = elem.toNaptimeRequest(3)
    assert(!req.arguments.exists(_._1 == "ids"))
  }

  // -------------------------------------------------------------------------
  // DeferredNaptimeRequest.toNaptimeRequest
  // -------------------------------------------------------------------------

  @Test
  def deferredNaptimeRequest_toNaptimeRequest_preservesFields(): Unit = {
    val deferred = DeferredNaptimeRequest(
      resourceName = resourceName,
      arguments = Set("q" -> JsString("test")),
      resourceSchema = realSchema)
    val req = deferred.toNaptimeRequest(7)
    assert(req.idx === RequestId(7))
    assert(req.resourceName === resourceName)
    assert(req.arguments.exists { case (k, v) => k == "q" && v == JsString("test") })
  }
}
