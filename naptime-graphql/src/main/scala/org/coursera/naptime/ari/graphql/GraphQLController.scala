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
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.mvc._
import sangria.execution.ErrorWithResolver
import sangria.execution.Executor
import sangria.execution.QueryAnalysisError
import sangria.parser.QueryParser
import sangria.renderer.SchemaRenderer
import sangria.marshalling.playJson._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future


@Singleton
class GraphQLController @Inject() (
    graphqlSchemaProvider: GraphqlSchemaProvider,
    schemaProvider: GraphqlSchemaProvider,
    engine: EngineApi)
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
    }

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
      variables: Option[JsObject],
      operation: Option[String]) = {

    // TODO(bryan): Handle errors / failures properly here
    (for {
      request <- SangriaGraphQlParser.parse(query, requestHeader)
      document <- QueryParser.parse(query).toOption
    } yield {
      val fetcherExecution: Future[Option[Response]] = if (query.contains("IntrospectionQuery")) {
        Future.successful(None)
      } else {
        engine.execute(request).map(Some(_))
      }
      fetcherExecution.flatMap { responseOpt =>
        val allData = responseOpt.map(
          _.data.map { case (resourceName, response) =>
            resourceName.identifier -> response.values.toList
          }).getOrElse(Map.empty)
        val context = SangriaGraphQlContext(data = allData)
        Executor.execute(graphqlSchemaProvider.schema, document, context)
          .map(Ok(_))
          .recover {
            case error: QueryAnalysisError => BadRequest(error.resolveError)
            case error: ErrorWithResolver => InternalServerError(error.resolveError)
          }
      }
    }).getOrElse {
      Future.successful(BadRequest("Invalid request"))
    }
  }
}
