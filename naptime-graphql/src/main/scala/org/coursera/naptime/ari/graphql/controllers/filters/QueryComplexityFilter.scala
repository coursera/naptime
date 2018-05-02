package org.coursera.naptime.ari.graphql.controllers.filters

import javax.inject.Inject
import javax.inject.Singleton

import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.graphql.GraphqlSchemaProvider
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.controllers.GraphQLController
import org.coursera.naptime.ari.graphql.marshaller.NaptimeMarshaller._
import org.coursera.naptime.ari.graphql.resolvers.NaptimeResolver
import org.coursera.naptime.ari.graphql.resolvers.NoopResolver
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.Results
import sangria.ast.Document
import sangria.execution.ErrorWithResolver
import sangria.execution.Executor
import sangria.execution.QueryAnalysisError
import sangria.execution.QueryReducer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class QueryComplexityFilter @Inject()(
    graphqlSchemaProvider: GraphqlSchemaProvider,
    configuration: ComplexityFilterConfiguration)(implicit executionContext: ExecutionContext)
    extends Filter
    with Results
    with StrictLogging {

  val MAX_COMPLEXITY = configuration.maxComplexity

  def apply(nextFilter: FilterFn): FilterFn = { incoming =>
    computeComplexity(incoming.document, incoming.variables)
      .flatMap { complexity =>
        if (complexity > MAX_COMPLEXITY) {
          Future.successful(
            OutgoingQuery(
              response = Json.obj("error" -> "Query is too complex.", "complexity" -> complexity),
              ariResponse = None))
        } else {
          nextFilter.apply(incoming)
        }
      }
      .recover {
        case error: QueryAnalysisError =>
          OutgoingQuery(error.resolveError.as[JsObject], None)
        case error: ErrorWithResolver =>
          OutgoingQuery(error.resolveError.as[JsObject], None)
        case error: Exception =>
          OutgoingQuery(Json.obj("errors" -> Json.arr(error.getMessage)), None)
      }
  }

  private[graphql] def computeComplexity(queryAst: Document, variables: JsObject)(
      implicit executionContext: ExecutionContext): Future[Double] = {
    // TODO(bryan): is there a way around this var?
    var complexity = 0D
    val complReducer = QueryReducer.measureComplexity[SangriaGraphQlContext] { (c, ctx) =>
      complexity = c
      ctx
    }
    val executorFut = Executor.execute(
      graphqlSchemaProvider.schema,
      queryAst,
      SangriaGraphQlContext(null, null, executionContext, debugMode = false),
      variables = variables,
      exceptionHandler = GraphQLController.exceptionHandler(logger),
      queryReducers = List(complReducer),
      deferredResolver = new NoopResolver()
    )

    executorFut.map { _ =>
      complexity
    }

  }
}

case class ComplexityFilterConfiguration(maxComplexity: Int)

object ComplexityFilterConfiguration {
  val DEFAULT = ComplexityFilterConfiguration(100000)
}
