package org.coursera.naptime

import org.coursera.naptime.actions.RestActionTester
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.test.FakeRequest

/**
 * Tests for RestActionTester trait to exercise uncovered code paths.
 */
class RestActionTesterTest extends AssertionsForJUnit with ScalaFutures with RestActionTester {

  import AuthBuilderTest._

  val resource = new EngineersResource()

  def makeRequestContext(body: Engineer) =
    buildRestContext((), body, FakeRequest().withBody(body), RequestPagination(1, None, true))

  // Exercise requestEvidence implicit — needed to reach that statement in coverage
  @Test
  def requestEvidence_isAccessible(): Unit = {
    val evidence: RequestEvidence = requestEvidence
    assert(evidence != null)
  }

  // Exercise buildRestContext with fields and includes parameters
  @Test
  def buildRestContext_withFieldsAndIncludes_buildsContext(): Unit = {
    val body = Engineer("TestEngineer")
    val ctx = buildRestContext(
      auth = (),
      body = body,
      request = FakeRequest().withBody(body),
      paging = RequestPagination(10, None, false),
      fields = "name",
      includes = "")
    assert(ctx != null)
  }
}
