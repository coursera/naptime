package org.coursera.naptime.ari.engine

import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ari.ResponseMetrics

import javax.inject.Singleton

trait EngineMetricsCollector {

  def markExecutionCompletion(responseMetrics: ResponseMetrics): Unit

  def markExecutionFailure(): Unit

}

@Singleton
class LoggingEngineMetricsCollector extends EngineMetricsCollector with StrictLogging {
  def markExecutionCompletion(responseMetrics: ResponseMetrics): Unit = {
    logger.info(s"Completed engine execution in ${responseMetrics.duration.toMillis} ms with ${responseMetrics.numRequests} requests")
  }

  def markExecutionFailure(): Unit = {
    logger.info(s"Failed engine execution")
  }
}


@Singleton
class NoopEngineMetricsCollector extends EngineMetricsCollector with StrictLogging {
  def markExecutionCompletion(responseMetrics: ResponseMetrics): Unit = ()

  def markExecutionFailure(): Unit = ()
}
