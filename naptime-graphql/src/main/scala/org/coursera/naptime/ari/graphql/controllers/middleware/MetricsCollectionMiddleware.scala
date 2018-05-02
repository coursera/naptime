package org.coursera.naptime.ari.graphql.controllers.middleware

import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.execution.BeforeFieldResult
import sangria.execution.MiddlewareErrorField
import sangria.execution.MiddlewareQueryContext
import sangria.schema.Context

class MetricsCollectionMiddleware(metricsCollector: GraphQLMetricsCollector)
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
      ctx: Context[SangriaGraphQlContext, _]): BeforeFieldResult[SangriaGraphQlContext, FieldVal] =
    BeforeFieldResult(Unit, None)

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
  def timeQueryParsing[A](operationName: String)(f: => A): A
}

class LoggingMetricsCollector extends GraphQLMetricsCollector with StrictLogging {
  def markFieldError(fieldName: String): Unit = {
    logger.info(s"Error when loading field $fieldName")
  }

  def timeQueryParsing[A](operationName: String)(f: => A): A = {
    val before = System.currentTimeMillis()
    val res = f
    val after = System.currentTimeMillis()
    logger.info(s"Parsed query $operationName in ${after - before}ms")
    res
  }
}

class NoopMetricsCollector extends GraphQLMetricsCollector with StrictLogging {
  def markFieldError(fieldName: String): Unit = ()

  def timeQueryParsing[A](operationName: String)(f: => A): A = {
    f
  }
}
