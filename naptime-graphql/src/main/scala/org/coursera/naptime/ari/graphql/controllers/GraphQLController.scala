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
    filterList: FilterList,
    metricsCollector: GraphQlControllerMetricsCollector)
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

    executeQuery(query, request, variables, operation).map { res =>
      Ok(res.response)
    }
  }

  def graphqlBatch = Action.async(parse.json) { request =>

    val queries = request.body.as[List[JsObject]]
    val resultsFut = Future.traverse(queries) { queryObj =>
      val query = (queryObj \ "query").as[String]
      val operation = (queryObj \ "operationName").asOpt[String]

      val variables = (queryObj \ "variables").toOption.flatMap {
        case JsString(vars) => Some(parseVariables(vars))
        case obj: JsObject => Some(obj)
        case _ => None
      }.getOrElse(Json.obj())

      executeQuery(query, request, variables, operation)
    }
    resultsFut.map { results =>
      Ok(Json.toJson(results.map(_.response)))
    }
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
      operation: Option[String]): Future[OutgoingQuery] = {
    Future {
      val startSangriaQueryParser = System.nanoTime()
      QueryParser.parse(query) match {
        case Success(queryAst) =>
          metricsCollector.markSangriaQueryParserTime(System.nanoTime() - startSangriaQueryParser)
          val baseFilter: IncomingQuery => Future[OutgoingQuery] = (incoming: IncomingQuery) => {
            val startNaptimeQueryParser = System.nanoTime()
            SangriaGraphQlParser.parse(query, variables, requestHeader).map { request =>
              metricsCollector
                .markNaptimeQueryParserTime(System.nanoTime() - startNaptimeQueryParser)
              val startEngineExecution = System.nanoTime()
              val fetcherExecution: Future[Option[Response]] =
                if (query.contains("IntrospectionQuery")) {
                  Future.successful(None)
                } else {
                  engine.execute(request).map(Some(_))
                }
              fetcherExecution.flatMap { responseOpt =>
                metricsCollector.markEngineExecutionTime(System.nanoTime() - startEngineExecution)
                val response = responseOpt.getOrElse(Response.empty)
                val context = SangriaGraphQlContext(response)
                val startSangriaHydration = System.nanoTime()
                Executor.execute(
                  graphqlSchemaProvider.schema,
                  queryAst,
                  context,
                  variables = variables,
                  exceptionHandler = GraphQLController.exceptionHandler(logger))
                  .map { executionResponse =>
                    metricsCollector.markSangriaHydrationTime(
                      System.nanoTime() - startSangriaHydration)
                    OutgoingQuery(executionResponse.as[JsObject], Some(response))
                  }
              }.recover {
                case error: QueryAnalysisError =>
                  OutgoingQuery(error.resolveError.as[JsObject], None)
                case error: ErrorWithResolver =>
                  OutgoingQuery(error.resolveError.as[JsObject], None)
                case error: Exception =>
                  logger.error("GraphQL execution error", error)
                  OutgoingQuery(Json.obj("errors" -> Json.arr(error.getMessage)), None)
              }
            }.getOrElse {
              val result = Json.obj("syntaxError" -> "Could not parse document")
              Future.successful(OutgoingQuery(result, None))
            }
          }

          val incomingQuery = IncomingQuery(queryAst, requestHeader, variables, operation)

          val filterFn = filterList.filters.reverse.foldLeft(baseFilter) {
            case (accumulatedFilters, filter) =>
              filter.apply(accumulatedFilters)
          }

          filterFn(incomingQuery)

        case Failure(error: SyntaxError) =>
          Future.successful(
            OutgoingQuery(
              Json.obj(
                "syntaxError" -> error.getMessage,
                "locations" -> Json.arr(
                  Json.obj(
                    "line" -> error.originalError.position.line,
                    "column" -> error.originalError.position.column))), None))

        case Failure(error) =>
          throw error
      }
    }.flatMap(identity)
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
