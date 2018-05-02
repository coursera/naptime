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
import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import sangria.execution.BeforeFieldResult
import sangria.execution.MiddlewareErrorField

import scala.collection.JavaConverters._
import scala.util.Try

class ResponseMetadataMiddleware
    extends MiddlewareExtension[SangriaGraphQlContext]
    with MiddlewareAfterField[SangriaGraphQlContext]
    with MiddlewareErrorField[SangriaGraphQlContext] {

  override type QueryVal = Unit
  override type FieldVal = Unit

  case class ResponseMetadata(
      sourceUrl: String,
      status: Int = 200,
      errorMessage: Option[JsValue] = None)

  private[this] val responseMetadata =
    new ConcurrentHashMap[String, ResponseMetadata]().asScala

  override def beforeQuery(context: MiddlewareQueryContext[SangriaGraphQlContext, _, _]): QueryVal =
    ()

  override def afterQuery(
      queryVal: QueryVal,
      context: MiddlewareQueryContext[SangriaGraphQlContext, _, _]): Unit = ()

  override def beforeField(
      queryVal: Unit,
      mctx: MiddlewareQueryContext[SangriaGraphQlContext, _, _],
      ctx: Context[SangriaGraphQlContext, _]): BeforeFieldResult[SangriaGraphQlContext, FieldVal] =
    BeforeFieldResult(Unit, None)

  override def afterField(
      queryVal: Unit,
      fieldVal: FieldVal,
      value: Any,
      mctx: MiddlewareQueryContext[SangriaGraphQlContext, _, _],
      ctx: Context[SangriaGraphQlContext, _]): Option[Any] = {
    if (ctx.ctx.debugMode) {
      value match {
        case NaptimeResponse(_, _, url, status, errorMessage) =>
          val errorJson = errorMessage.map(Json.parse)
          responseMetadata.putIfAbsent(
            ctx.path.toString(),
            ResponseMetadata(url, status, errorJson))
          None
        case Some(DataMapWithParent(_, _, sourceUrl)) =>
          sourceUrl.foreach { url =>
            responseMetadata.putIfAbsent(ctx.path.toString(), ResponseMetadata(url))
          }
          None
        case DataMapWithParent(_, _, sourceUrl) =>
          sourceUrl.foreach { url =>
            responseMetadata.putIfAbsent(ctx.path.toString(), ResponseMetadata(url))
          }
          None
        case _ =>
          None
      }
    } else {
      None
    }
  }

  override def fieldError(
      queryVal: QueryVal,
      fieldVal: FieldVal,
      error: Throwable,
      mctx: MiddlewareQueryContext[SangriaGraphQlContext, _, _],
      ctx: Context[SangriaGraphQlContext, _]): Unit = {
    if (ctx.ctx.debugMode) {
      error match {
        case NaptimeResolveException(naptimeError) =>
          val errorJson = Try(Json.parse(naptimeError.errorMessage)).toOption
            .getOrElse(Json.obj("error" -> naptimeError.errorMessage))
          responseMetadata.putIfAbsent(
            ctx.path.toString(),
            ResponseMetadata(naptimeError.url, naptimeError.status, Some(errorJson)))
        case _ =>
          ()
      }
    }
  }

  override def afterQueryExtensions(
      queryVal: QueryVal,
      context: MiddlewareQueryContext[SangriaGraphQlContext, _, _]): Vector[Extension[_]] = {

    import sangria.marshalling.queryAst._

    if (context.ctx.debugMode) {
      val objectFields = responseMetadata.map {
        case (path, metadata) =>
          val errorMessageFieldOpt = metadata.errorMessage.map { error =>
            sangria.ast.ObjectField("errorMessage", parseToAst(error))
          }

          sangria.ast.ObjectField(
            path,
            sangria.ast.ObjectValue(
              Vector(
                sangria.ast.ObjectField("sourceUrl", sangria.ast.StringValue(metadata.sourceUrl)),
                sangria.ast.ObjectField("statusCode", sangria.ast.IntValue(metadata.status))
              ) ++
                errorMessageFieldOpt)
          )
      }.toVector

      Vector(
        Extension(
          sangria.ast
            .ObjectValue(Vector(
              sangria.ast.ObjectField("responseMetadata", sangria.ast.ObjectValue(objectFields))))
            .asInstanceOf[sangria.ast.Value])
      )
    } else {
      Vector.empty
    }
  }

  private[this] def parseToAst(jsValue: JsValue): sangria.ast.Value = {
    jsValue match {
      case obj: JsObject =>
        val subFields = obj.fields.map {
          case (key, value) =>
            sangria.ast.ObjectField(key, parseToAst(value))
        }
        sangria.ast.ObjectValue(subFields.toVector)
      case arr: JsArray =>
        sangria.ast.ListValue(arr.value.map(parseToAst).toVector)
      case str: JsString    => sangria.ast.StringValue(str.value)
      case number: JsNumber => sangria.ast.BigDecimalValue(number.value)
      case _                => sangria.ast.StringValue(Json.stringify(jsValue))

    }
  }

}
