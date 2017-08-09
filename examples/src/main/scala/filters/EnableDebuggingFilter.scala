package filters

import javax.inject.Singleton

import org.coursera.naptime.ari.graphql.controllers.filters.Filter

@Singleton
class EnableDebuggingFilter extends Filter {

  def apply(nextFilter: FilterFn): FilterFn = { incoming =>
    nextFilter.apply(incoming.copy(debugMode = true))
  }
}
