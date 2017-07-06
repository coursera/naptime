package org.coursera.naptime.ari.graphql.controllers.filters

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultFilters @Inject() (
    queryComplexityFilter: QueryComplexityFilter,
    engineMetricsFilter: EngineMetricsFilter)
  extends FilterList(List(
//    queryComplexityFilter,
    engineMetricsFilter))
