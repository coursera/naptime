package org.coursera.naptime.ari.graphql.controllers.middleware

import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.execution.MiddlewareErrorField
import sangria.execution.MiddlewareQueryContext
import sangria.schema.Action
import sangria.schema.Context

class MetricsCollectionMiddleware(
    metricsCollector: GraphQLMetricsCollector = new LoggingEngineMetricsCollector())
  extends MiddlewareErrorField[SangriaGraphQlContext] {

  override type QueryVal = Unit
  override type FieldVal = Unit

  override def beforeQuery(context: MiddlewareQueryContext[SangriaGraphQlContext, _, _]): Unit = ()

  override def afterQuery(
      queryVal: Unit,
      context: MiddlewareQueryContext[SangriaGraphQlContext, _, _]): Unit = ()

  override def beforeField(
      queryVal: Unit,
      mctx: MiddlewareQueryContext[SangriaGraphQlContext, _, _],
      ctx: Context[SangriaGraphQlContext, _]): (Unit, Option[Action[SangriaGraphQlContext, _]]) =
    (Unit, None)

  override def fieldError(
      queryVal: QueryVal,
      fieldVal: FieldVal,
      error: Throwable,
      mctx: MiddlewareQueryContext[SangriaGraphQlContext, _, _],
      ctx: Context[SangriaGraphQlContext, _]): Unit = {
    val fieldAndParentName = ctx.parentType.name + ":" + ctx.field.name
    metricsCollector.markFieldError(fieldAndParentName)
  }
}

trait GraphQLMetricsCollector {
  def markFieldError(fieldName: String): Unit
}

class LoggingEngineMetricsCollector extends GraphQLMetricsCollector with StrictLogging {
  def markFieldError(fieldName: String): Unit = {
    logger.info(s"Error when loading field $fieldName")
  }
}

class NoopEngineMetricsCollector extends GraphQLMetricsCollector with StrictLogging {
  def markFieldError(fieldName: String): Unit = ()
}
