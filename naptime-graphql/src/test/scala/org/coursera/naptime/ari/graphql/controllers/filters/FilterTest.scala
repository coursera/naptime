package org.coursera.naptime.ari.graphql.controllers.filters

import org.coursera.naptime.ari.graphql.GraphqlSchemaProvider
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.SangriaGraphQlSchemaBuilder
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.mockito.Mockito.when
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import sangria.parser.QueryParser
import sangria.schema.Schema

import scala.concurrent.Future

trait FilterTest
    extends AssertionsForJUnit
    with MockitoSugar
    with ScalaFutures
    with IntegrationPatience {

  val baseOutgoingQuery = OutgoingQuery(Json.obj(), None)

  def noopFilter(incomingQuery: IncomingQuery) = {
    Future.successful(baseOutgoingQuery)
  }

  def exceptionThrowingFilter(incomingQuery: IncomingQuery): Future[OutgoingQuery] = {
    assert(false, "This filter should not be run")
    Future.successful(baseOutgoingQuery)
  }

  val filter: Filter

  val defaultQuery =
    """
      |query {
      |  __schema {
      |    queryType {
      |      name
      |    }
      |  }
      |}
    """.stripMargin

  val graphqlSchemaProvider = mock[GraphqlSchemaProvider]

  val allResources = Set(Models.courseResource, Models.instructorResource, Models.partnersResource)

  val schemaTypes = Map(
    "org.coursera.naptime.ari.graphql.models.MergedCourse" -> MergedCourse.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedPartner" -> MergedPartner.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedInstructor" -> MergedInstructor.SCHEMA)
  val builder = new SangriaGraphQlSchemaBuilder(allResources, schemaTypes)

  val schema = builder.generateSchema().data.asInstanceOf[Schema[SangriaGraphQlContext, Any]]
  when(graphqlSchemaProvider.schema).thenReturn(schema)

  def generateIncomingQuery(query: String = defaultQuery) = {
    val document = QueryParser.parse(query).get
    val header = FakeRequest("POST", s"/graphql").withBody(query)
    val variables = Json.obj()
    val operation = None
    IncomingQuery(document, header, variables, operation, debugMode = false)
  }

  def run(incomingQuery: IncomingQuery): Future[OutgoingQuery] = {
    filter.apply(noopFilter)(incomingQuery)
  }

  def ensureNotPropagated(incomingQuery: IncomingQuery): Future[OutgoingQuery] = {
    filter.apply(exceptionThrowingFilter)(incomingQuery)
  }
}
