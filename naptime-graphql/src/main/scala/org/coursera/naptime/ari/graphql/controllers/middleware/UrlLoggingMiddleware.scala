package org.coursera.naptime.ari.graphql.controllers.middleware

import java.util.concurrent.ConcurrentHashMap

import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.resolvers.NaptimeResponse
import sangria.execution.Extension
import sangria.execution.MiddlewareAfterField
import sangria.execution.MiddlewareExtension
import sangria.execution.MiddlewareQueryContext
import sangria.schema.Action
import sangria.schema.Context
import org.coursera.naptime.ari.graphql.schema.DataMapWithParent
import org.coursera.naptime.ari.graphql.schema.NaptimeResolveException
import sangria.execution.MiddlewareErrorField
import scala.collection.JavaConverters._

class UrlLoggingMiddleware
  extends MiddlewareExtension[SangriaGraphQlContext]
  with MiddlewareAfterField[SangriaGraphQlContext]
  with MiddlewareErrorField[SangriaGraphQlContext] {

  override type QueryVal = Unit
  override type FieldVal = Unit

  private[this] val urlsLoaded = new ConcurrentHashMap[String, String]().asScala

  override def beforeQuery(
      context: MiddlewareQueryContext[SangriaGraphQlContext, _, _]): QueryVal = ()

  override def afterQuery(
      queryVal: QueryVal,
      context: MiddlewareQueryContext[SangriaGraphQlContext, _, _]): Unit = ()

  override def beforeField(
      queryVal: Unit,
      mctx: MiddlewareQueryContext[SangriaGraphQlContext, _, _],
      ctx: Context[SangriaGraphQlContext, _]): (Unit, Option[Action[SangriaGraphQlContext, _]]) =
    (Unit, None)

  override def afterField(
      queryVal: Unit,
      fieldVal: FieldVal,
      value: Any,
      mctx: MiddlewareQueryContext[SangriaGraphQlContext, _, _],
      ctx: Context[SangriaGraphQlContext, _]): Option[Any] = {
    value match {
      case NaptimeResponse(_, _, url) =>
        urlsLoaded.putIfAbsent(ctx.path.toString(), url)
        None
      case Some(DataMapWithParent(_, _, sourceUrl)) =>
        sourceUrl.foreach { url =>
          urlsLoaded.putIfAbsent(ctx.path.toString(), url)
        }
        None
      case DataMapWithParent(_, _, sourceUrl) =>
        sourceUrl.foreach { url =>
          urlsLoaded.putIfAbsent(ctx.path.toString(), url)
        }
        None
      case _ =>
        None
    }
  }

  override def fieldError(
      queryVal: QueryVal,
      fieldVal: FieldVal,
      error: Throwable,
      mctx: MiddlewareQueryContext[SangriaGraphQlContext, _, _],
      ctx: Context[SangriaGraphQlContext, _]): Unit = {
    error match {
      case NaptimeResolveException(naptimeError) =>
        urlsLoaded.putIfAbsent(ctx.path.toString(), naptimeError.url)
      case _ =>
        ()
    }
  }

  override def afterQueryExtensions(
      queryVal: QueryVal,
      context: MiddlewareQueryContext[SangriaGraphQlContext, _, _]): Vector[Extension[_]] = {

    import sangria.marshalling.queryAst._

    // TODO(bryan): handle auths here
    val objectFields = urlsLoaded.map { case (path, url) =>
      sangria.ast.ObjectField(path, sangria.ast.StringValue(url))
    }.toVector
    Vector(
      Extension(sangria.ast.ObjectValue(
        Vector(sangria.ast.ObjectField(
          "sourceUrls",
          sangria.ast.ObjectValue(objectFields)))).asInstanceOf[sangria.ast.Value])
    )
  }

}
