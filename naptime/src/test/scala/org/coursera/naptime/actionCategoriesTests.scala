package org.coursera.naptime

import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.naptime.actions.RestAction
import org.coursera.naptime.actions.RestActionCategoryEngine
import org.coursera.naptime.actions.util.Validators
import org.coursera.naptime.resources.CollectionResource
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.junit.Test
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import play.api.http.HeaderNames
import play.api.http.Status
import play.api.http.Writeable
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import play.api.mvc.Result
import play.api.mvc.AnyContent

import scala.concurrent.duration._
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import play.api.test.Helpers.contentAsBytes
import play.api.test.Helpers.defaultAwaitTimeout

import scala.concurrent.Future

case class EngineTestResource(name: String, desc: String)

object EngineTestResource {
  implicit val format = Json.format[EngineTestResource]

  def mk(id: Int): EngineTestResource = apply(s"$id", s"EngineTestResource($id)")
}

object RestActionCategoryEngineTest {

  /**
   * A test resource to be used for testing the rest engines.
   *
   * Note: because we're not using routing, we can get away with having multiple get's / etc.
   * In general, it is a very bad idea to have multiple gets / etc. in a single resource.
   */
  object SampleResource
    extends TopLevelCollectionResource[Int, EngineTestResource] {
    override val keyFormat: KeyFormat[Int] = KeyFormat.intKeyFormat
    override val resourceName: String = "tests"
    override val resourceFormat = Json.format[EngineTestResource]
    implicit val fields = Fields.withDefaultFields("name", "desc")

    def get1(id: Int) = Nap.get { ctx =>
      Ok(Keyed(1, EngineTestResource.mk(1)))
    }

    def get2(id: Int) = Nap.get { ctx =>
      Errors.BadRequest("bad", "You lose!")
    }

    def create1 = Nap.create { ctx =>
      Ok(Keyed(1, Some(EngineTestResource.mk(1))))
    }

    def create2 = Nap.create { ctx =>
      Ok(Keyed(1, None))
    }

    def create3 = Nap.catching {
      case e: RuntimeException => RestError(NaptimeActionException(Status.BAD_REQUEST, Some("boom"), None))
    }.create { ctx =>
      throw new RuntimeException("Boom")
    }

    def delete1(id: Int) = Nap.delete {
      Ok(())
    }

    def testFinder1(q1: K1) = Nap.finder.async { ctx =>
      Future.successful(Ok(List(Keyed(1, EngineTestResource("finder", s"q1=$q1")))))
    }

    def testFinder2(q1: K1, q2: K2) = Nap.finder.async { ctx =>
      Future.successful(Ok(List(Keyed(1, EngineTestResource("finder", s"q1=$q1, q2=$q2")))))
    }

  }

  case class K1(key: Int)

  object K1 {
    implicit val stringKeyFormat: StringKeyFormat[K1] = StringKeyFormat.delegateFormat[K1, Int](k => Some(K1(k)), k => k.key)
  }

  case class K2(key: Int)

  object K2 {
    implicit val stringKeyFormat: StringKeyFormat[K2] = StringKeyFormat.delegateFormat[K2, Int](k => Some(K2(k)), k => k.key)
  }

}

class RestActionCategoryEngineTest extends AssertionsForJUnit with ScalaFutures {
  import RestActionCategoryEngineTest._

  @Test
  def get1(): Unit = {
    testEmptyBody(SampleResource.get1(1))
  }

  @Test
  def get2(): Unit = {
    testEmptyBody(SampleResource.get2(2))
  }

  @Test
  def create1(): Unit = {
    testEmptyBody(SampleResource.create1)
  }

  @Test
  def create2(): Unit = {
    testEmptyBody(SampleResource.create2)
  }

  @Test
  def create3(): Unit = {
    testEmptyBody(SampleResource.create3)
  }

  @Test
  def delete1(): Unit = {
    testEmptyBody(SampleResource.delete1(1))
  }

  @Test
  def testFinder1(): Unit = {
    val request = FakeRequest("GET", "/?q1=1")
    testEmptyBody(SampleResource.testFinder1(K1(1)), request = request)
  }

  @Test
  def testFinder2(): Unit = {
    val request = FakeRequest("GET", "/?q1=1&q2=2")
    testEmptyBody(SampleResource.testFinder2(K1(1), K2(2)), request = request)
  }

  // Helpers below.

  private[this] def test[BodyType](actionToTest: RestAction[_, _, BodyType, _, _, _],
      request: FakeRequest[BodyType],
      strictMode: Boolean = false)(
      implicit writeable: Writeable[BodyType]): Result = {
    val result = runTestRequest(actionToTest, request)
    Validators.assertValidResponse(result, strictMode = strictMode)
    result
  }

  private[this] def testEmptyBody(actionToTest: RestAction[_, _, AnyContent, _, _, _],
      request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(),
      strictMode: Boolean = false): Result = {
    val result = runTestRequest(actionToTest, request)
    Validators.assertValidResponse(result, strictMode = strictMode)
    result
  }

  private[this] def runTestRequestInternal[BodyType](
      restAction: RestAction[_, _, BodyType, _, _, _],
      request: RequestHeader,
      body: Enumerator[Array[Byte]] = Enumerator.empty): Result = {
    val iteratee = restAction.apply(request)
    val resultFut = body.run(iteratee)
    resultFut.futureValue
  }

  private[this] def runTestRequest[BodyType](restAction: RestAction[_, _, BodyType, _, _, _],
      fakeRequest: FakeRequest[BodyType])(
      implicit writeable: Writeable[BodyType]): Result = {
    val requestWithHeader = writeable.contentType.map { ct =>
      fakeRequest.withHeaders(HeaderNames.CONTENT_TYPE -> ct)
    }.getOrElse(fakeRequest)
    val b = Enumerator(fakeRequest.body).through(writeable.toEnumeratee)
    runTestRequestInternal(restAction, requestWithHeader, b)
  }

  private[this] def runTestRequest(restAction: RestAction[_, _, AnyContent, _, _, _],
      fakeRequest: FakeRequest[AnyContentAsEmpty.type]): Result = {
    runTestRequestInternal(restAction, fakeRequest)
  }
}

class ETagRestActionCategoryEngineTest extends AssertionsForJUnit with ScalaFutures {

  import ETagRestActionCategoryEngineTest._

  val testPagination = RequestPagination(limit = 20, start = None, isDefault = true)

  @Test
  def mkETagHeader(): Unit = {
    val result = "test result"
    val eTag = ETag.Strong("1")
    val okResponse = Ok(result).withETag(eTag)
    val expectedHeader = "ETag" -> "\"1\""

    val header = RestActionCategoryEngine.mkETagHeaderOpt(testPagination, okResponse, None)
    assert(Some(expectedHeader) === header)
    assertETagHeaderValueFormat(header.get._2)
  }

  @Test
  def mkETagHeaderNone(): Unit = {
    val result = "test result"
    val okResponse = Ok(result)

    assert(None === RestActionCategoryEngine.mkETagHeaderOpt(testPagination, okResponse, None))
  }

  @Test
  def fallbackETagHeader(): Unit = {
    val jsObject = Json.obj("hello" -> "world", "foo" -> Json.obj("bar" -> "baz"))
    val result = "test result"
    val okResponse = Ok(result)

    val etag = RestActionCategoryEngine.mkETagHeader(testPagination, okResponse, jsObject)
    assert(etag._1 === HeaderNames.ETAG)
    assertETagHeaderValueFormat(etag._2)
  }

  @Test
  def etagShouldShortCircuitResponse(): Unit = {
    implicit val intKeyFormat = KeyFormat.intKeyFormat
    val req = FakeRequest().withHeaders(HeaderNames.IF_NONE_MATCH -> "\"asdf\"")
    val fields = Fields[TestResponse](TestResponse.fmt)
    val pagination = RequestPagination(20, None, isDefault = true)
    val naptimeResponse = Ok(Keyed(1, TestResponse(foo = "bar"))).withETag(ETag.Strong("asdf"))

    val engine = RestActionCategoryEngine.getActionCategoryEngine[Int, TestResponse](
      TestResponse.writes, intKeyFormat)

    val response = engine.mkResponse(req, fields, QueryFields.empty, QueryIncludes.empty,
      pagination, naptimeResponse)
    assert(response.header.status === Status.NOT_MODIFIED)
    val bodyContent = contentAsBytes(Future.successful(response))
    assert(bodyContent.length === 0)
  }

  private[this] def assertETagHeaderValueFormat(etagHeaderValue: String) = {
    assert(etagHeaderValue.startsWith("\""), "ETag header values must start with a quote character")
    assert(etagHeaderValue.endsWith("\""), "ETag header values must end with a quote character.")

    assert(etagHeaderValueRegex.pattern.matcher(etagHeaderValue).matches)
  }
}

object ETagRestActionCategoryEngineTest {
  val etagHeaderValueRegex = "^\"[A-z0-9-]+\"".r
}

case class TestResponse(foo: String)

object TestResponse {
  implicit val writes = OWrites[TestResponse](tr => Json.obj("foo" -> tr.foo))
  val reads = Json.reads[TestResponse]
  implicit val fmt = OFormat(reads, writes)
}
