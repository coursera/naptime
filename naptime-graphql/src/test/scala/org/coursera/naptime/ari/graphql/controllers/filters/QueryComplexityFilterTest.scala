package org.coursera.naptime.ari.graphql.controllers.filters

import org.junit.Test

import scala.concurrent.ExecutionContext.Implicits.global

class QueryComplexityFilterTest extends FilterTest {

  val config = ComplexityFilterConfiguration.DEFAULT
  val filter = new QueryComplexityFilter(graphqlSchemaProvider, config)

  @Test
  def emptyQuery(): Unit = {
    val incomingQuery = generateIncomingQuery()
    val outgoingQuery = run(incomingQuery).futureValue
    assert(outgoingQuery === baseOutgoingQuery)
  }

  @Test
  def complexQuery(): Unit = {
    val query =
      """
        |query {
        |  CoursesV1Resource {
        |    getAll(limit: 100000) {
        |      elements {
        |        id
        |      }
        |    }
        |  }
        |}
      """.stripMargin
    val incomingQuery = generateIncomingQuery(query)
    val outgoingQuery = ensureNotPropagated(incomingQuery).futureValue

    assert(outgoingQuery.response.value("error").as[String] === "Query is too complex.")
    assert(outgoingQuery.response.value("complexity").as[Int] === 200001)
  }
}
