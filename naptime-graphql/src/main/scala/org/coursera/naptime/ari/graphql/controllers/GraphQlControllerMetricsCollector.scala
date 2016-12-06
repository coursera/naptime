package org.coursera.naptime.ari.graphql.controllers

import com.typesafe.scalalogging.StrictLogging
import javax.inject.Singleton

trait GraphQlControllerMetricsCollector {

  def markSangriaQueryParserTime(nanos: Long): Unit
  def markNaptimeQueryParserTime(nanos: Long): Unit
  def markEngineExecutionTime(nanos: Long): Unit
  def markSangriaHydrationTime(nanos: Long): Unit

}

@Singleton
class LoggingGraphQlControllerMetricsCollector
  extends GraphQlControllerMetricsCollector
  with StrictLogging {

  def markSangriaQueryParserTime(nanos: Long): Unit = {
    logger.info(s"Sangria query parser took: ${nanos / 1000000} ms")
  }

  def markNaptimeQueryParserTime(nanos: Long): Unit = {
    logger.info(s"Naptime query parser took: ${nanos / 1000000} ms")
  }

  def markEngineExecutionTime(nanos: Long): Unit = {
    logger.info(s"Naptime engine execution took: ${nanos / 1000000} ms")
  }

  def markSangriaHydrationTime(nanos: Long): Unit = {
    logger.info(s"Sangria hydration took: ${nanos / 1000000} ms")
  }

}


@Singleton
class NoopGraphQlControllerMetricsCollector
  extends GraphQlControllerMetricsCollector
  with StrictLogging {

  def markSangriaQueryParserTime(nanos: Long): Unit = ()
  def markNaptimeQueryParserTime(nanos: Long): Unit = ()
  def markEngineExecutionTime(nanos: Long): Unit = ()
  def markSangriaHydrationTime(nanos: Long): Unit = ()
}
