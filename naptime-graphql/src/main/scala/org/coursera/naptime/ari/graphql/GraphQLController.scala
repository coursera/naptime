/*
 * Copyright 2016 Coursera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.coursera.naptime.ari.graphql

import javax.inject._

import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ari.EngineApi
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.engine.EngineMetricsCollector
import org.coursera.naptime.ari.graphql.marshaller.NaptimeMarshaller._
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.mvc._
import sangria.ast.Document
import sangria.execution.ErrorWithResolver
import sangria.execution.Executor
import sangria.execution.HandledException
import sangria.execution.QueryAnalysisError
import sangria.execution.QueryReducer
import sangria.parser.QueryParser
import sangria.renderer.SchemaRenderer
import sangria.parser.SyntaxError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success


@Singleton
class GraphQLController @Inject() (
    graphqlSchemaProvider: GraphqlSchemaProvider,
    engine: EngineApi,
    metricsCollector: EngineMetricsCollector)
    (implicit ec: ExecutionContext)
  extends Controller
  with StrictLogging {


  def renderSchema = Action {
    Ok(SchemaRenderer.renderSchema(graphqlSchemaProvider.schema))
  }

  def graphqlBody = Action.async(parse.json) { request =>
    val query = (request.body \ "query").as[String]
    val operation = (request.body \ "operationName").asOpt[String]

    val variables = (request.body \ "variables").toOption.flatMap {
      case JsString(vars) => Some(parseVariables(vars))
      case obj: JsObject => Some(obj)
      case _ => None
    }.getOrElse(Json.obj())

    executeQuery(query, request, variables, operation)
  }

  private def parseVariables(variables: String) = {
    if (variables.trim == "" || variables.trim == "null") {
      Json.obj()
    } else {
      Json.parse(variables).as[JsObject]
    }
  }

  val exceptionHandler: Executor.ExceptionHandler = {
    // TODO(bryan): Check superuser status here before returning message
    case (m, e: Exception) =>
      logger.error("Uncaught query execution error", e)
      HandledException(e.getMessage)
  }

  val MAX_COMPLEXITY = 10000 // TODO(bryan): pull this out to config?

  private def executeQuery(
      query: String,
      requestHeader: RequestHeader,
      variables: JsObject,
      operation: Option[String]) = {

    QueryParser.parse(query) match {
      case Success(queryAst) =>
        SangriaGraphQlParser.parse(query, variables, requestHeader).map { request =>

          computeComplexity(queryAst, variables).flatMap { complexity =>
            if (complexity > MAX_COMPLEXITY) {
              Future.successful(BadRequest(Json.obj(
                "error" -> "Query is too complex.",
                "complexity" -> complexity)))
            } else {
              val fetcherExecution: Future[Option[Response]] =
                if (query.contains("IntrospectionQuery")) {
                  Future.successful(None)
                } else {
                  engine.execute(request).map(Some(_))
                }
              fetcherExecution.flatMap { responseOpt =>
                responseOpt.foreach(r => metricsCollector.markExecutionCompletion(r.metrics))
                val response = responseOpt.getOrElse(Response.empty)
                val context = SangriaGraphQlContext(response)
                Executor.execute(
                  graphqlSchemaProvider.schema,
                  queryAst,
                  context,
                  variables = variables,
                  exceptionHandler = exceptionHandler)
                  .map(Ok(_).withHeaders(
                    ("X-Naptime-Downstream-Requests", response.metrics.numRequests.toString)))

              }.recover {
                case error: QueryAnalysisError =>
                  BadRequest(Json.obj("error" -> error.resolveError))
                case error: ErrorWithResolver =>
                  InternalServerError(Json.obj("error" -> error.resolveError))
                case error: Exception =>
                  InternalServerError(Json.obj("error" -> error.getMessage))
              }
            }
          }.recover {
            case error: QueryAnalysisError =>
              BadRequest(Json.obj("error" -> error.resolveError))
            case error: ErrorWithResolver =>
              InternalServerError(Json.obj("error" -> error.resolveError))
            case error: Exception =>
              InternalServerError(Json.obj("error" -> error.getMessage))
          }
        }.getOrElse {
          Future.successful(
            BadRequest(
              Json.obj(
                "syntaxError" -> "Could not parse document")))
        }
      case Failure(error: SyntaxError) =>
        Future.successful(
          BadRequest(
            Json.obj(
              "syntaxError" -> error.getMessage,
              "locations" -> Json.arr(
                Json.obj(
                  "line" -> error.originalError.position.line,
                  "column" -> error.originalError.position.column)))))

      case Failure(error) =>
        throw error
    }
  }
  private[graphql] def computeComplexity(
      queryAst: Document,
      variables: JsObject): Future[Double] = {
    // TODO(bryan): is there a way around this var?
    var complexity = 0D
    val complReducer = QueryReducer.measureComplexity[SangriaGraphQlContext] { (c, ctx) ⇒
      complexity = c
      ctx
    }
    val executorFut = Executor.execute(
      graphqlSchemaProvider.schema,
      queryAst,
      SangriaGraphQlContext(Response.empty),
      variables = variables,
      exceptionHandler = exceptionHandler,
      queryReducers = List(complReducer))

    executorFut.map { _ =>
      complexity
    }

  }
}
