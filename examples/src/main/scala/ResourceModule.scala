import org.coursera.naptime.NaptimeModule
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.LocalSchemaProvider
import org.coursera.naptime.ari.SchemaProvider
import org.coursera.naptime.ari.engine.EngineMetricsCollector
import org.coursera.naptime.ari.engine.LoggingEngineMetricsCollector
import org.coursera.naptime.ari.fetcher.LocalFetcher
import org.coursera.naptime.ari.graphql.DefaultGraphqlSchemaProvider
import org.coursera.naptime.ari.graphql.GraphqlSchemaProvider
import org.coursera.naptime.ari.graphql.controllers.GraphQlControllerMetricsCollector
import org.coursera.naptime.ari.graphql.controllers.LoggingGraphQlControllerMetricsCollector
import org.coursera.naptime.ari.graphql.controllers.filters.ComplexityFilterConfiguration
import org.coursera.naptime.ari.graphql.controllers.filters.DefaultFilters
import org.coursera.naptime.ari.graphql.controllers.filters.FilterList
import resources.UserStore
import resources.UserStoreImpl
import resources.UsersResource
import resources.CoursesResource
import resources.InstructorsResource
import resources.PartnersResource


class ResourceModule extends NaptimeModule {
  override def configure(): Unit = {
    bindResource[UsersResource]
    bindResource[CoursesResource]
    bindResource[InstructorsResource]
    bindResource[PartnersResource]
    bind[UserStore].to[UserStoreImpl]
    bind[FetcherApi].to[LocalFetcher]
    bind[EngineMetricsCollector].to[LoggingEngineMetricsCollector]
    bind[SchemaProvider].to[LocalSchemaProvider]
    bind[GraphqlSchemaProvider].to[DefaultGraphqlSchemaProvider]
    bind[FilterList].to[DefaultFilters]
    bind[ComplexityFilterConfiguration].toInstance(ComplexityFilterConfiguration.DEFAULT)
    bind[GraphQlControllerMetricsCollector].to[LoggingGraphQlControllerMetricsCollector]
  }
}
