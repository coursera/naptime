package org.coursera.naptime.ari.graphql.resolvers

import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.execution.deferred.Deferred
import sangria.execution.deferred.DeferredResolver

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class NoopResolver extends DeferredResolver[SangriaGraphQlContext] with StrictLogging {
  def resolve(deferred: Vector[Deferred[Any]], ctx: SangriaGraphQlContext, queryState: Any)(
      implicit ec: ExecutionContext): Vector[Future[Any]] = {
    deferred.map(_ =>
      Future.successful(Right(NaptimeResponse(List.empty, Some(ResponsePagination.empty), ""))))
  }
}
