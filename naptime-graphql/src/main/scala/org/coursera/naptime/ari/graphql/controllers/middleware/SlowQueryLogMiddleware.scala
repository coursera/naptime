package org.coursera.naptime.ari.graphql.controllers.middleware

import com.typesafe.scalalogging.Logger
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.execution.Extension
import sangria.execution.Middleware
import sangria.execution.MiddlewareAfterField
import sangria.execution.MiddlewareExtension
import sangria.execution.MiddlewareQueryContext
import sangria.schema.Action
import sangria.schema.Context
import sangria.execution.MiddlewareErrorField

import sangria.slowlog.SlowLog
import sangria.slowlog.QueryMetrics

import scala.concurrent.duration._

class SlowQueryLogMiddleware(logger: Logger)
  extends Middleware[SangriaGraphQlContext]
  with MiddlewareExtension[SangriaGraphQlContext]
  with MiddlewareAfterField[SangriaGraphQlContext]
  with MiddlewareErrorField[SangriaGraphQlContext] {

  type QueryVal = QueryMetrics
  type FieldVal = Long

  private[this] val underlying = SlowLog(logger.underlying, threshold = 6 seconds)

  def beforeQuery(context: MiddlewareQueryContext[Any, _, _]) = underlying.beforeQuery(context)

  def afterQuery(queryVal: QueryVal, context: MiddlewareQueryContext[Any, _, _]) = underlying.afterQuery(queryVal, context)

  def afterQueryExtensions(queryVal: QueryMetrics, context: MiddlewareQueryContext[Any, _, _]): Vector[Extension[_]] =
    underlying.afterQueryExtensions(queryVal, context)

  def beforeField(queryVal: QueryVal, mctx: MiddlewareQueryContext[Any, _, _], ctx: Context[Any, _]) =
    underlying.beforeField(queryVal, mctx, ctx)

  def afterField(queryVal: QueryVal, fieldVal: FieldVal, value: Any, mctx: MiddlewareQueryContext[Any, _, _], ctx: Context[Any, _]) =
    underlying.afterField(queryVal, fieldVal, value, mctx, ctx)

  def fieldError(queryVal: QueryVal, fieldVal: FieldVal, error: Throwable, mctx: MiddlewareQueryContext[Any, _, _], ctx: Context[Any, _]) =
    underlying.fieldError(queryVal, fieldVal, error, mctx, ctx)

  def updateMetric(queryVal: QueryVal, fieldVal: FieldVal, ctx: Context[Any, _], success: Boolean): Unit =
    underlying.updateMetric(queryVal, fieldVal, ctx, success)
}
