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

package org.coursera.naptime.ari.graphql.controllers

import javax.inject._

import com.typesafe.scalalogging.Logger
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ari.EngineApi
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.graphql.GraphqlSchemaProvider
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.SangriaGraphQlParser
import org.coursera.naptime.ari.graphql.controllers.filters.FilterList
import org.coursera.naptime.ari.graphql.controllers.filters.IncomingQuery
import org.coursera.naptime.ari.graphql.controllers.filters.OutgoingQuery
import org.coursera.naptime.ari.graphql.marshaller.NaptimeMarshaller._
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.mvc._
import sangria.execution.ErrorWithResolver
import sangria.execution.Executor
import sangria.execution.HandledException
import sangria.execution.QueryAnalysisError
import sangria.parser.QueryParser
import sangria.parser.SyntaxError
import sangria.renderer.SchemaRenderer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success


@Singleton
class GraphQLController @Inject() (
    graphqlSchemaProvider: GraphqlSchemaProvider,
    schemaProvider: GraphqlSchemaProvider,
    engine: EngineApi,
    filterList: FilterList)
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

  private def executeQuery(
      query: String,
      requestHeader: RequestHeader,
      variables: JsObject,
      operation: Option[String]) = {

    QueryParser.parse(query) match {
      case Success(queryAst) =>

        val baseFilter: IncomingQuery => Future[OutgoingQuery] = (incoming: IncomingQuery) => {
          SangriaGraphQlParser.parse(query, variables, requestHeader).map { request =>
            val fetcherExecution: Future[Option[Response]] =
              if (query.contains("IntrospectionQuery")) {
                Future.successful(None)
              } else {
                engine.execute(request).map(Some(_))
              }
            fetcherExecution.flatMap { responseOpt =>
              val response = responseOpt.getOrElse(Response.empty)
              val context = SangriaGraphQlContext(response)
              Executor.execute(
                graphqlSchemaProvider.schema,
                queryAst,
                context,
                variables = variables,
                exceptionHandler = GraphQLController.exceptionHandler(logger))
                .map { executionResponse =>
                  OutgoingQuery(Ok(executionResponse), Some(response))
                }
            }.recover {
              case error: QueryAnalysisError =>
                OutgoingQuery(BadRequest(Json.obj("error" -> error.resolveError)), None)
              case error: ErrorWithResolver =>
                OutgoingQuery(InternalServerError(Json.obj("error" -> error.resolveError)), None)
              case error: Exception =>
                OutgoingQuery(InternalServerError(Json.obj("error" -> error.getMessage)), None)
            }
          }.getOrElse {
            val result = BadRequest(
              Json.obj(
                "syntaxError" -> "Could not parse document"))
            Future.successful(OutgoingQuery(result, None))
          }
        }

        val incomingQuery = IncomingQuery(queryAst, requestHeader, variables, operation)

        val filterFn = filterList.filters.reverse.foldLeft(baseFilter) {
          case (accumulatedFilters, filter) =>
            filter.apply(accumulatedFilters)
          }

        filterFn(incomingQuery).map(_.result)

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
}

object GraphQLController {
  def exceptionHandler(logger: Logger): Executor.ExceptionHandler = {
    // TODO(bryan): Check superuser status here before returning message
    case (m, e: Exception) =>
      logger.error("Uncaught query execution error", e)
      HandledException(e.getMessage)
  }
}
