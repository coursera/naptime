package controllers

import javax.inject._

import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ari.EngineApi
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.SangriaGraphQlParser
import org.coursera.naptime.ari.graphql.SangriaGraphQlSchemaBuilder
import org.coursera.naptime.router2.ResourceRouterBuilder
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.mvc._
import sangria.execution.Executor
import sangria.parser.QueryParser
import sangria.renderer.SchemaRenderer
import sangria.schema.Schema
import sangria.marshalling.playJson._

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class GraphQlController @Inject() (
    routerBuilders: immutable.Set[ResourceRouterBuilder],
    fetcher: EngineApi)
  extends Controller {

  val resources = routerBuilders.map { routerBuilder =>
    routerBuilder.schema
  }

  val types = routerBuilders.foldLeft(Map[String, RecordDataSchema]()) { case (allTypes, routerBuilder) =>
    allTypes ++ routerBuilder.types.flatMap { keyedSchema =>
      keyedSchema.value match {
        case recordSchemaValue: RecordDataSchema => Some(keyedSchema.key -> recordSchemaValue)
        case _ => None
      }
    }
  }
  val builder = new SangriaGraphQlSchemaBuilder(resources, types)
  val schema = builder.generateSchema().asInstanceOf[Schema[org.coursera.naptime.ari.graphql.SangriaGraphQlContext,Any]]

  def index = Action {
    Ok("Your new application is ready.")
  }

  def graphiql = Action {
    Ok(views.html.graphiql())
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
      operation: Option[String]) =

    // TODO(bryan): Handle errors / failures properly here
    (for {
      request <- SangriaGraphQlParser.parse(query, requestHeader)
      document <- QueryParser.parse(query).toOption
    } yield {
      val fetcherExecution = if (query.contains("IntrospectionQuery")) {
        Future.successful(None)
      } else {
        fetcher.execute(request).map(Some(_))
      }
      fetcherExecution.flatMap { responseOpt =>
        val allData = responseOpt.map(_.output.map { case (resourceName, response) =>
          s"${resourceName.topLevelName}.v${resourceName.version}" -> response.models.toList
        }).getOrElse(Map.empty)
        val fakeContext = SangriaGraphQlContext(myField = "testField", data = allData)
        Executor.execute(schema, document, fakeContext).map(Ok(_))
      }
    }).getOrElse {
      Future.successful(BadRequest("Invalid request"))
    }


  def renderSchema = Action {
    Ok(SchemaRenderer.renderSchema(schema))
  }

}
