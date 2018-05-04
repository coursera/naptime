package org.coursera.naptime.ari.graphql.controllers.filters

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultFilters @Inject()(queryComplexityFilter: QueryComplexityFilter)
    extends FilterList(
      List(
        queryComplexityFilter
      ))
