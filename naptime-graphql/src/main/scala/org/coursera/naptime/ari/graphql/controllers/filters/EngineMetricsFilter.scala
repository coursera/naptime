package org.coursera.naptime.ari.graphql.controllers.filters

import javax.inject.Inject
import javax.inject.Singleton

import org.coursera.naptime.ari.engine.EngineMetricsCollector

import scala.concurrent.ExecutionContext

@Singleton
class EngineMetricsFilter @Inject() (
    metricsCollector: EngineMetricsCollector)
    (implicit executionContext: ExecutionContext)
  extends Filter {

  def apply(nextFilter: FilterFn): FilterFn = { incoming =>
    nextFilter.apply(incoming).map { outgoingQuery =>
      outgoingQuery.ariResponse.map { response =>
        metricsCollector.markExecutionCompletion(response.metrics)
        val resultWithHeader = outgoingQuery.result.withHeaders(
          ("X-Naptime-Downstream-Requests", response.metrics.numRequests.toString))
        outgoingQuery.copy(result = resultWithHeader)
      }.getOrElse {
        outgoingQuery
      }
    }
  }
}
