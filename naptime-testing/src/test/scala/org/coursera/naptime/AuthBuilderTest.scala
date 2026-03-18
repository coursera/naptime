package org.coursera.naptime

import akka.stream.Materializer
import org.coursera.naptime.access.HeaderAccessControl
import org.coursera.naptime.actions.RestActionTester
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AuthBuilderTest extends AssertionsForJUnit with ScalaFutures with RestActionTester {

  import AuthBuilderTest._

  val resource = new EngineersResource()

  def makeRequestContext(body: Engineer) =
    buildRestContext((), body, FakeRequest().withBody(body), RequestPagination(1, None, true))
  @Test
  def create_HeaderAuths_AuthWorks(): Unit = {
    assert(
      resource
        .createNoAuth()
        .testAction(makeRequestContext(Engineer("Anybody")))
        .isOk)
    assert(
      resource
        .createHeaderAuthAccept()
        .testAction(makeRequestContext(Engineer("Anybody")))
        .isOk)
    assert(
      resource
        .createHeaderAuthReject()
        .testAction(makeRequestContext(Engineer("Anybody")))
        .isError)
  }

  @Test
  def create_BodyAuthMatching_Ok(): Unit = {
    assert(
      resource
        .createBodyAuthNamedBrennan()
        .testAction(makeRequestContext(Engineer("Brennan")))
        .isOk)
    assert(
      resource
        .createBodyAuthNamedFrank()
        .testAction(makeRequestContext(Engineer("Frank")))
        .isOk)
  }

  @Test
  def create_BodyAuthNonMatching_Rejects(): Unit = {
    assert(
      resource
        .createBodyAuthNamedBrennan()
        .testAction(makeRequestContext(Engineer("Abbot")))
        .isError)
    assert(
      resource
        .createBodyAuthNamedFrank()
        .testAction(makeRequestContext(Engineer("Costello")))
        .isError)
  }

  // ─── testActionPassAuth bypasses auth checks entirely ────────────────────────

  @Test
  def testActionPassAuth_bypassesHeaderAuth(): Unit = {
    // AlwaysReject would fail testAction, but testActionPassAuth skips auth
    assert(
      resource
        .createHeaderAuthReject()
        .testActionPassAuth(makeRequestContext(Engineer("Anybody")))
        .isOk)
  }

  @Test
  def testActionPassAuth_bypassesBodyAuth(): Unit = {
    // Name doesn't match "Brennan", but auth is bypassed so it succeeds
    assert(
      resource
        .createBodyAuthNamedBrennan()
        .testActionPassAuth(makeRequestContext(Engineer("Abbot")))
        .isOk)
  }

  @Test
  def testActionPassAuth_noAuth_stillSucceeds(): Unit = {
    assert(
      resource
        .createNoAuth()
        .testActionPassAuth(makeRequestContext(Engineer("Anyone")))
        .isOk)
  }

  // ─── NaptimeActionException recovery inside testAction ───────────────────────

  @Test
  def testAction_actionThrowsNaptimeException_returnsRestError(): Unit = {
    val result = resource
      .createThrowsNaptimeException()
      .testAction(makeRequestContext(Engineer("Anyone")))
    assert(result.isError)
  }

  @Test
  def testActionPassAuth_actionThrowsNaptimeException_returnsRestError(): Unit = {
    val result = resource
      .createThrowsNaptimeException()
      .testActionPassAuth(makeRequestContext(Engineer("Anyone")))
    assert(result.isError)
  }

  // ─── TestFailedException recovery: uncaught RuntimeException propagates ────────

  @Test
  def testAction_actionThrowsRuntimeException_propagatesException(): Unit = {
    intercept[RuntimeException] {
      resource
        .createThrowsRuntimeException()
        .testAction(makeRequestContext(Engineer("Anyone")))
    }
  }

  @Test
  def testActionPassAuth_actionThrowsRuntimeException_propagatesException(): Unit = {
    intercept[RuntimeException] {
      resource
        .createThrowsRuntimeException()
        .testActionPassAuth(makeRequestContext(Engineer("Anyone")))
    }
  }

}

object AuthBuilderTest {

  case class Engineer(name: String)

  object Engineer {
    implicit val fmt: OFormat[Engineer] = Json.format[Engineer]
  }

  case object AlwaysAccept extends HeaderAccessControl[Unit] {
    override def run(requestHeader: RequestHeader)(
        implicit executionContext: ExecutionContext): Future[Either[NaptimeActionException, Unit]] =
      Future.successful(Right(()))

    override private[naptime] def check(authInfo: Unit) = Right(authInfo)
  }

  case object AlwaysReject extends HeaderAccessControl[Unit] {
    override def run(requestHeader: RequestHeader)(
        implicit executionContext: ExecutionContext): Future[Either[NaptimeActionException, Unit]] =
      Future.successful(Left(Errors.Forbidden()))

    override private[naptime] def check(authInfo: Unit) = Left(Errors.Forbidden())
  }

  def acceptIfEngineerHasName(thing: Engineer, name: String) = {
    if (thing.name == name) AlwaysAccept else AlwaysReject
  }

  class EngineersResource(implicit ec: ExecutionContext, mat: Materializer)
      extends TopLevelCollectionResource[Int, Engineer] {
    override val keyFormat: KeyFormat[Int] = KeyFormat.intKeyFormat
    override val resourceName: String = "tests"
    override val resourceFormat = Json.format[Engineer]
    implicit val fields = Fields
    override val executionContext = implicitly[ExecutionContext]
    override val materializer = implicitly[Materializer]

    def createNoAuth() =
      Nap
        .jsonBody[Engineer]
        .create { ctx =>
          Ok(Keyed(1, Some(ctx.body)))
        }

    def createHeaderAuthAccept() =
      Nap
        .jsonBody[Engineer]
        .auth(AlwaysAccept)
        .create { ctx =>
          Ok(Keyed(1, Some(ctx.body)))
        }

    def createHeaderAuthReject() =
      Nap
        .jsonBody[Engineer]
        .auth(AlwaysReject)
        .create { ctx =>
          Ok(Keyed(1, Some(ctx.body)))
        }

    def createBodyAuthNamedBrennan() =
      Nap
        .jsonBody[Engineer]
        .auth(acceptIfEngineerHasName(_, "Brennan"))
        .create { ctx =>
          Ok(Keyed(1, Some(ctx.body)))
        }

    def createBodyAuthNamedFrank() =
      Nap
        .jsonBody[Engineer]
        .auth(acceptIfEngineerHasName(_, "Frank"))
        .create { ctx =>
          Ok(Keyed(1, Some(ctx.body)))
        }

    def createThrowsNaptimeException() =
      Nap
        .jsonBody[Engineer]
        .create { ctx =>
          throw Errors.NotFound()
        }

    def createThrowsRuntimeException() =
      Nap
        .jsonBody[Engineer]
        .create { ctx =>
          throw new RuntimeException("Uncaught runtime error for coverage")
        }

  }

}
