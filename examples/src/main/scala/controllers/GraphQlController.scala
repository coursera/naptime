package controllers

import javax.inject._

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.courier.templates.ScalaRecordTemplate
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.SangriaGraphQlParser
import org.coursera.naptime.ari.graphql.SangriaGraphQlSchemaBuilder
import org.coursera.naptime.router2.ResourceRouterBuilder
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.mvc._
import resources.UserStore
import sangria.execution.Executor
import sangria.parser.QueryParser
import sangria.renderer.SchemaRenderer
import sangria.schema.Schema
import sangria.marshalling.playJson._
import stores.CourseStore
import stores.InstructorStore
import stores.PartnerStore

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class GraphQlController @Inject() (
    routerBuilders: immutable.Set[ResourceRouterBuilder],
    userStore: UserStore,
    courseStore: CourseStore,
    instructorStore: InstructorStore,
    partnerStore: PartnerStore) extends Controller {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */

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

  println(SchemaRenderer.renderSchema(schema))

  def index = Action { implicit request =>
//    request.body.asText.map { query =>
//      val schema = builder.generateSchema().asInstanceOf[Schema[SangriaGraphQlContext, Any]]
//    }
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

  private def parseVariables(variables: String) =
    if (variables.trim == "" || variables.trim == "null") Json.obj() else Json.parse(variables).as[JsObject]

  private def appendIds(map: Map[_ <: Object, _ <: ScalaRecordTemplate]): List[DataMap] = {
    map.map { case (key, value) =>
      val updatedDataMap = value.data().copy()
      updatedDataMap.put("id", key)
      updatedDataMap
    }.toList
  }

  private def executeQuery(query: String, requestHeader: RequestHeader, variables: Option[JsObject], operation: Option[String]) =
    (for {
      request <- SangriaGraphQlParser.parse(query, requestHeader)
      document <- QueryParser.parse(query).toOption
    } yield {
      val allData = Map(
        "users.v1" -> appendIds(userStore.all().map { case (userId, user) => new Integer(userId) -> user }),
        "courses.v1" -> appendIds(courseStore.all()),
        "instructors.v1" -> appendIds(instructorStore.all()),
        "partners.v1" -> appendIds(partnerStore.all())
      )
      val fakeContext = SangriaGraphQlContext(myField = "testField", data = allData)
      Executor.execute(schema, document, fakeContext).map(Ok(_))
    }).getOrElse {
      Future.successful(BadRequest("Invalid request"))
    }

//  map { request =>
//      // TODO(bryan): run request through engine here
//      Executor.execute(schema, parsedDocumentOption.get, fakeContext)
//      Executor.execute
//    }
//    QueryParser.parse(query) match {
//
//      // query parsed successfully, time to execute it!
//      case Success(queryAst) =>
//        Executor.execute(SchemaDefinition.StarWarsSchema, queryAst, new CharacterRepo,
//          operationName = operation,
//          variables = variables getOrElse Json.obj(),
//          deferredResolver = new FriendsResolver,
//          maxQueryDepth = Some(10))
//          .map(Ok(_))
//          .recover {
//            case error: QueryAnalysisError ⇒ BadRequest(error.resolveError)
//            case error: ErrorWithResolver ⇒ InternalServerError(error.resolveError)
//          }
//
//      // can't parse GraphQL query, return error
//      case Failure(error: SyntaxError) =>
//        Future.successful(BadRequest(Json.obj(
//          "syntaxError" -> error.getMessage,
//          "locations" -> Json.arr(Json.obj(
//            "line" -> error.originalError.position.line,
//            "column" -> error.originalError.position.column)))))
//
//      case Failure(error) =>
//        throw error
//    }

  def renderSchema = Action {
    Ok(SchemaRenderer.renderSchema(schema))
  }

}
