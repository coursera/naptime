package org.coursera.naptime.ari.graphql

import com.google.inject.Injector
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.FullSchema
import org.coursera.naptime.ari.LocalSchemaProvider
import org.coursera.naptime.ari.SchemaProvider
import org.coursera.naptime.ari.engine.CoursesResource
import org.coursera.naptime.ari.engine.InstructorsResource
import org.coursera.naptime.ari.engine.PartnersResource
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.router2.ResourceRouterBuilder
import org.coursera.naptime.schema.Resource
import org.junit.Test
import org.mockito.Mockito._
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar

class DefaultGraphqlSchemaProviderTest extends AssertionsForJUnit {
  import DefaultGraphqlSchemaProviderTest._

  @Test
  def checkEmptySchema(): Unit = {
    val emptySchema = new DefaultGraphqlSchemaProvider(emptySchemaProvider())

    val nonMetadataTypes = emptySchema.schema.allTypes.filterNot(_._1.startsWith("__"))
    assert(nonMetadataTypes.keySet === DEFAULT_TYPES, s"${nonMetadataTypes.keySet}")
  }

  @Test
  def checkBasicSchemaComputation(): Unit = {
    val simpleSchema = new DefaultGraphqlSchemaProvider(simpleSchemaProvider())

    val nonMetadataTypes = simpleSchema.schema.allTypes.filterNot(_._1.startsWith("__"))
    assert(nonMetadataTypes.keySet === DEFAULT_TYPES ++ COMPUTED_TYPES -- FILTERED_TYPES,
      s"${nonMetadataTypes.keySet}")
  }

  @Test
  def constantlyChanging(): Unit = {
    val regeneratingProvider = new SchemaProvider {
      val underlying = simpleSchemaProvider()

      override def mergedType(resourceName: ResourceName): Option[RecordDataSchema] = {
        underlying.mergedType(resourceName)
      }

      override def fullSchema: FullSchema = {
        FullSchema(
          Set.empty ++ underlying.fullSchema.resources,
          Set.empty ++ underlying.fullSchema.types)
      }
    }

    assert(!(regeneratingProvider.fullSchema eq regeneratingProvider.fullSchema))

    val regenerating = new DefaultGraphqlSchemaProvider(regeneratingProvider)

    val nonMetadataTypes = regenerating.schema.allTypes.filterNot(_._1.startsWith("__"))
    assert(nonMetadataTypes.keySet === DEFAULT_TYPES ++ COMPUTED_TYPES -- FILTERED_TYPES,
      s"${nonMetadataTypes.keySet}")
  }

  // TODO: check to ensure that it recomputes only when required.
}

object DefaultGraphqlSchemaProviderTest extends MockitoSugar {
  import org.coursera.naptime.ari.engine.EngineImplTest._

  val DEFAULT_TYPES = Set(
    "ID",
    "root",
    "Boolean",
    "Long",
    "Float",
    "Int",
    "BigInt",
    "String",
    "BigDecimal")

  val COMPUTED_TYPES = Set(
    "CoursesV1",
    "CoursesV1Connection",
    "CoursesV1Resource",
    "InstructorsV1",
    "InstructorsV1Connection",
    "InstructorsV1Resource",
    "CoursesV1_intMember",
    "PartnersV1_org_coursera_naptime_ari_graphql_models_Coordinates",
    "org_coursera_naptime_ari_graphql_models_CoursePlatform",
    "CoursesV1_originalId",
    "CoursesV1_platformSpecificData",
    "CoursesV1_org_coursera_naptime_ari_graphql_models_OldPlatformData",
    "CoursesV1_org_coursera_naptime_ari_graphql_models_OldPlatformDataMember",
    "CoursesV1_org_coursera_naptime_ari_graphql_models_NewPlatformDataMember",
    "CoursesV1_org_coursera_naptime_ari_graphql_models_NewPlatformData",
    "PartnersV1",
    "ResponsePagination",
    "CoursesV1_stringMember",
    "DataMap")

  val FILTERED_TYPES = Set(
    "PartnersV1",
    "PartnersV1_org_coursera_naptime_ari_graphql_models_Coordinates")

  val extraTypes = TYPE_SCHEMAS.map { case (key, value) => Keyed(key, value) }.toList

  def simpleSchemaProvider(): SchemaProvider = {

    val courseRouterBuilder = mock[ResourceRouterBuilder]
    when(courseRouterBuilder.schema).thenReturn(COURSES_RESOURCE)
    when(courseRouterBuilder.types).thenReturn(extraTypes)
    when(courseRouterBuilder.resourceClass()).thenReturn(
      classOf[CoursesResource].asInstanceOf[Class[courseRouterBuilder.ResourceClass]])


    val instructorRouterBuilder = mock[ResourceRouterBuilder]
    when(instructorRouterBuilder.schema).thenReturn(INSTRUCTORS_RESOURCE)
    when(instructorRouterBuilder.types).thenReturn(extraTypes)
    when(instructorRouterBuilder.resourceClass()).thenReturn(
      classOf[InstructorsResource].asInstanceOf[Class[instructorRouterBuilder.ResourceClass]])

    val partnerRouterBuilder = mock[ResourceRouterBuilder]
    when(partnerRouterBuilder.schema).thenReturn(PARTNERS_RESOURCE)
    when(partnerRouterBuilder.types).thenReturn(extraTypes)
    when(partnerRouterBuilder.resourceClass()).thenReturn(
      classOf[PartnersResource].asInstanceOf[Class[partnerRouterBuilder.ResourceClass]])

    val injector = mock[Injector]
    new LocalSchemaProvider(NaptimeRoutes(injector, Set(
      courseRouterBuilder,
      instructorRouterBuilder,
      partnerRouterBuilder)))
  }

  def emptySchemaProvider() = {
    val injector = mock[Injector]
    new LocalSchemaProvider(NaptimeRoutes(injector, Set.empty))
  }
}
