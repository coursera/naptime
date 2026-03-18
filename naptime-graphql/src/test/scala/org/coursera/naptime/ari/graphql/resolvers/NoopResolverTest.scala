package org.coursera.naptime.ari.graphql.resolvers

import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.test.FakeRequest
import sangria.execution.deferred.Deferred

import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class NoopResolverTest extends AssertionsForJUnit {

  private val ctx =
    SangriaGraphQlContext(null, FakeRequest(), ExecutionContext.global, debugMode = false)
  private val resolver = new NoopResolver

  @Test def resolve_emptyDeferred_returnsEmptyVector(): Unit = {
    val results = resolver.resolve(Vector.empty, ctx, ())(ExecutionContext.global)
    assertResult(0)(results.size)
  }

  @Test def resolve_singleDeferred_returnsSingleFuture(): Unit = {
    val deferred = new Deferred[Any] {}
    val results = resolver.resolve(Vector(deferred), ctx, ())(ExecutionContext.global)
    assertResult(1)(results.size)
    val response = Await.result(results.head, Duration("5 seconds"))
    response match {
      case Right(NaptimeResponse(elements, _, _, _, _)) => assertResult(0)(elements.size)
      case other                                        => fail(s"Unexpected response: $other")
    }
  }

  @Test def resolve_multipleDeferred_returnsMatchingCount(): Unit = {
    val deferred1 = new Deferred[Any] {}
    val deferred2 = new Deferred[Any] {}
    val results = resolver.resolve(Vector(deferred1, deferred2), ctx, ())(ExecutionContext.global)
    assertResult(2)(results.size)
  }
}
