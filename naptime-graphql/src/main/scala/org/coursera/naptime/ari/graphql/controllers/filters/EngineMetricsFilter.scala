package org.coursera.naptime.ari.graphql.controllers.filters

import javax.inject.Inject
import javax.inject.Singleton

import org.coursera.naptime.ari.engine.EngineMetricsCollector
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

@Singleton
class EngineMetricsFilter @Inject() (
    metricsCollector: EngineMetricsCollector)
    (implicit executionContext: ExecutionContext)
  extends Filter {

  def apply(nextFilter: FilterFn): FilterFn = { incoming =>
    nextFilter.apply(incoming).map { outgoingQuery =>
      outgoingQuery.ariResponse.map { ariResponse =>
        metricsCollector.markExecutionCompletion(ariResponse.metrics)
        val meta = Json.obj("__meta" ->
          Json.obj("downstreamRequests" -> ariResponse.metrics.numRequests))
        val responseWithMetrics = outgoingQuery.response ++ meta
        outgoingQuery.copy(response = responseWithMetrics)
      }.getOrElse {
        outgoingQuery
      }
    }
  }
}
