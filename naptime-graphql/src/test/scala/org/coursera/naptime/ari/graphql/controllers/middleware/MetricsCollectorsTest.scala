package org.coursera.naptime.ari.graphql.controllers.middleware

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

class MetricsCollectorsTest extends AssertionsForJUnit {

  // ---------------------------------------------------------------------------
  // NoopMetricsCollector — verifies the no-op implementations don't throw
  // ---------------------------------------------------------------------------

  @Test
  def noopMarkFieldError_doesNotThrow(): Unit = {
    new NoopMetricsCollector().markFieldError("SomeType:someField")
  }

  @Test
  def noopTimeQueryParsing_returnsBlockResult(): Unit = {
    val result = new NoopMetricsCollector().timeQueryParsing("op") { 42 }
    assertResult(42)(result)
  }

  @Test
  def noopTimeQueryParsing_propagatesException(): Unit = {
    intercept[RuntimeException] {
      new NoopMetricsCollector().timeQueryParsing("op") { throw new RuntimeException("boom") }
    }
  }

  // ---------------------------------------------------------------------------
  // LoggingMetricsCollector — verifies the logging implementations don't throw
  // ---------------------------------------------------------------------------

  @Test
  def loggingMarkFieldError_doesNotThrow(): Unit = {
    new LoggingMetricsCollector().markFieldError("SomeType:someField")
  }

  @Test
  def loggingTimeQueryParsing_returnsBlockResult(): Unit = {
    val result = new LoggingMetricsCollector().timeQueryParsing("op") { "hello" }
    assertResult("hello")(result)
  }

  @Test
  def loggingTimeQueryParsing_propagatesException(): Unit = {
    intercept[IllegalStateException] {
      new LoggingMetricsCollector().timeQueryParsing("op") {
        throw new IllegalStateException("boom")
      }
    }
  }

  // ---------------------------------------------------------------------------
  // SlowLogMiddleware constant
  // ---------------------------------------------------------------------------

  @Test
  def slowLogMiddleware_thresholdIs6Seconds(): Unit = {
    import scala.concurrent.duration._
    assertResult(6.seconds)(SlowLogMiddleware.threshold)
  }
}
