package org.coursera.naptime

import akka.stream.Materializer
import org.coursera.naptime.actions.RestActionTester
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.time.Milliseconds
import org.scalatest.time.Span
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext
import scala.concurrent.Promise

/**
 * Exercises the getOrElse(throw e) path in RestActionTester.
 *
 * When a future never completes, ScalaFutures.futureValue times out and throws
 * TestFailedException with cause = None. The recover block in testAction /
 * testActionPassAuth hits getOrElse(throw e), rethrowing the TestFailedException.
 *
 * A 1-millisecond patience config makes the timeout immediate so the test is fast.
 */
class RestActionTesterTimeoutTest
    extends AssertionsForJUnit
    with ScalaFutures
    with RestActionTester {

  import RestActionTesterTimeoutTest._

  // Override patienceConfig to use a 1ms timeout so futureValue times out immediately.
  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(1, Milliseconds))

  val resource = new NeverResource()

  def makeCtx() = {
    val req = FakeRequest()
    buildRestContext((), req.body, req, RequestPagination(1, None, true))
  }

  @Test
  def testAction_neverCompletingFuture_rethrowsTestFailedException(): Unit = {
    // futureValue times out → TestFailedException(cause=None) → getOrElse(throw e)
    intercept[TestFailedException] {
      resource.neverAction().testAction(makeCtx())
    }
  }

  @Test
  def testActionPassAuth_neverCompletingFuture_rethrowsTestFailedException(): Unit = {
    intercept[TestFailedException] {
      resource.neverAction().testActionPassAuth(makeCtx())
    }
  }
}

object RestActionTesterTimeoutTest {

  case class Stub(id: String)

  object Stub {
    implicit val fmt: OFormat[Stub] = Json.format[Stub]
  }

  class NeverResource(implicit val executionContext: ExecutionContext, val materializer: Materializer)
      extends TopLevelCollectionResource[Int, Stub] {

    override def keyFormat: KeyFormat[Int] = KeyFormat.intKeyFormat
    override implicit def resourceFormat: OFormat[Stub] = Stub.fmt
    override def resourceName: String = "never"

    implicit val fields = Fields

    def neverAction() =
      Nap.create.async {
        // This future never completes, so futureValue will time out.
        Promise[RestResponse[Keyed[Int, Option[Stub]]]]().future
      }
  }
}
