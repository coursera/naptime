package org.coursera.naptime.ari.graphql.controllers.filters

import org.coursera.naptime.ResourceTestImplicits
import org.junit.Test

/**
 * Additional tests for QueryComplexityFilter covering recover paths and
 * configuration helper objects.
 */
class QueryComplexityFilterMoreTest extends FilterTest with ResourceTestImplicits {

  val config = ComplexityFilterConfiguration.DEFAULT
  val filter = new QueryComplexityFilter(graphqlSchemaProvider, config)

  // -------------------------------------------------------------------------
  // ComplexityFilterConfiguration
  // -------------------------------------------------------------------------

  @Test
  def complexityFilterConfiguration_defaultMaxComplexity_is100000(): Unit = {
    assertResult(100000)(ComplexityFilterConfiguration.DEFAULT.maxComplexity)
  }

  @Test
  def complexityFilterConfiguration_customValue(): Unit = {
    val cfg = ComplexityFilterConfiguration(5000)
    assertResult(5000)(cfg.maxComplexity)
  }

  // -------------------------------------------------------------------------
  // computeComplexity – valid query returns non-negative complexity
  // -------------------------------------------------------------------------

  @Test
  def computeComplexity_validQuery_returnsNonNegative(): Unit = {
    val query = generateIncomingQuery(defaultQuery)
    val complexityFut = filter.computeComplexity(query.document, query.variables)
    val complexity = complexityFut.futureValue
    assert(complexity >= 0.0)
  }

  // -------------------------------------------------------------------------
  // apply – query that triggers recover via QueryAnalysisError
  //
  // An invalid query (references unknown fields/types) causes sangria to
  // throw a QueryAnalysisError, which is caught in the recover block.
  // -------------------------------------------------------------------------

  @Test
  def apply_unknownFieldQuery_recoversWithError(): Unit = {
    // This query references a field that doesn't exist → QueryAnalysisError
    val badQuery =
      """
        |query {
        |  NonExistentType {
        |    nonExistentField
        |  }
        |}
      """.stripMargin
    val incoming = generateIncomingQuery(badQuery)
    // We use ensureNotPropagated to confirm the next filter is NOT called
    val outgoing = ensureNotPropagated(incoming).futureValue
    // The filter should recover and return an error response, not propagate
    assert(outgoing.response != null)
  }

  // -------------------------------------------------------------------------
  // apply – next filter is called when complexity is within limits
  // -------------------------------------------------------------------------

  @Test
  def apply_lowComplexityQuery_callsNextFilter(): Unit = {
    val incomingQuery = generateIncomingQuery()
    val outgoingQuery = run(incomingQuery).futureValue
    // noopFilter returns baseOutgoingQuery
    assert(outgoingQuery === baseOutgoingQuery)
  }
}
