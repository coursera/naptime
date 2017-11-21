package filters

import javax.inject.Inject
import javax.inject.Singleton

import org.coursera.naptime.ari.graphql.controllers.filters.FilterList

@Singleton
class ExampleProjectFilters @Inject() (enableDebuggingFilter: EnableDebuggingFilter)
  extends FilterList(List(enableDebuggingFilter))
