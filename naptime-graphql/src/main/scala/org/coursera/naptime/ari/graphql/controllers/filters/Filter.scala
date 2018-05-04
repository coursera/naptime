package org.coursera.naptime.ari.graphql.controllers.filters

import scala.concurrent.Future

trait Filter {

  type FilterFn = IncomingQuery => Future[OutgoingQuery]

  def apply(nextFilter: FilterFn): FilterFn

}
