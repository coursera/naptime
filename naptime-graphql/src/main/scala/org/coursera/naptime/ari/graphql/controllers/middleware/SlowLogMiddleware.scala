package org.coursera.naptime.ari.graphql.controllers.middleware

import com.typesafe.scalalogging.Logger
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.execution.BeforeFieldResult
import sangria.execution.Extension
import sangria.execution.Middleware
import sangria.execution.MiddlewareAfterField
import sangria.execution.MiddlewareExtension
import sangria.execution.MiddlewareQueryContext
import sangria.schema.Context
import sangria.execution.MiddlewareErrorField
import sangria.slowlog.SlowLog
import sangria.slowlog.QueryMetrics

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * Why is this wrapper on sangria.slowlog.SlowLog necessary? It's type signature is
 * `Middleware[Any] with MiddlewareAfterField[Any] with MiddlewareErrorField[Any] with MiddlewareExtension[Any]`
 * where `Any` refers to the "userContext". This prevents more specific userContext types
 * such as `SangriaGraphQlContext` from directly working.
 *
 * Once the library is updated to support parametric types, we should get rid of this delegate
 * and instantiate the SlowLog directly to pass into `Executor.execute`
 */
class SlowLogMiddleware(logger: Logger, isDebugMode: Boolean)
    extends Middleware[SangriaGraphQlContext]
    with MiddlewareExtension[SangriaGraphQlContext]
    with MiddlewareAfterField[SangriaGraphQlContext]
    with MiddlewareErrorField[SangriaGraphQlContext] {

  type QueryVal = QueryMetrics
  type FieldVal = Long

  private[this] val underlying = {
    if (isDebugMode) {
      SlowLog.extension
    } else {
      SlowLog(logger.underlying, threshold = 6 seconds)
    }
  }

  override def beforeQuery(
      context: MiddlewareQueryContext[SangriaGraphQlContext, _, _]): QueryMetrics =
    underlying.beforeQuery(context)

  override def afterQuery(
      queryVal: QueryMetrics,
      context: MiddlewareQueryContext[SangriaGraphQlContext, _, _]): Unit =
    underlying.afterQuery(queryVal, context)

  override def afterQueryExtensions(
      queryVal: QueryMetrics,
      context: MiddlewareQueryContext[SangriaGraphQlContext, _, _]): Vector[Extension[_]] =
    underlying.afterQueryExtensions(queryVal, context)

  // The next 2 functions are parametrized on `SangriaGraphQlContext` which makes them not usable
  // when directly passed into the delegate. Instead, we have to do a (safe) typecast. It is
  // wrapped as a `Try` to avoid future runtime errors.
  override def afterField(
      queryVal: QueryMetrics,
      fieldVal: Long,
      value: Any,
      mctx: MiddlewareQueryContext[SangriaGraphQlContext, _, _],
      ctx: Context[SangriaGraphQlContext, _]): Option[Any] = {
    val safeContext = Try(ctx.asInstanceOf[Context[Any, _]])
    safeContext match {
      case Success(c) =>
        underlying.afterField(queryVal, fieldVal, value, mctx, c)
      case Failure(_) => None
    }
  }

  override def fieldError(
      queryVal: QueryMetrics,
      fieldVal: Long,
      error: Throwable,
      mctx: MiddlewareQueryContext[SangriaGraphQlContext, _, _],
      ctx: Context[SangriaGraphQlContext, _]): Unit = {
    val safeContext = Try(ctx.asInstanceOf[Context[Any, _]])
    safeContext match {
      case Success(c) =>
        underlying.fieldError(queryVal, fieldVal, error, mctx, c)
      case Failure(_) => ()
    }
  }

  // We cannot use the same type casting method as the above 2 classes because the return type
  // is parametrized, so we just copy the underlying impl. It is very straightforward [just
  // recording the timestamp] so we don't think it will change.
  override def beforeField(
      queryVal: QueryMetrics,
      mctx: MiddlewareQueryContext[SangriaGraphQlContext, _, _],
      ctx: Context[SangriaGraphQlContext, _]): BeforeFieldResult[SangriaGraphQlContext, Long] =
    continue(System.nanoTime())
}
