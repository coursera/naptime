package org.coursera.naptime.ari.graphql

import com.google.inject.Injector
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.FullSchema
import org.coursera.naptime.ari.LocalSchemaProvider
import org.coursera.naptime.ari.SchemaProvider
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.router2.ResourceRouterBuilder
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.Parameter
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind
import org.junit.Test
import org.mockito.Mockito._
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar

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
    assert(
      nonMetadataTypes.keySet === DEFAULT_TYPES ++ COMPUTED_TYPES -- FILTERED_TYPES,
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
    assert(
      nonMetadataTypes.keySet === DEFAULT_TYPES ++ COMPUTED_TYPES -- FILTERED_TYPES,
      s"${nonMetadataTypes.keySet}")
  }

  // TODO: check to ensure that it recomputes only when required.
}

class CoursesResource
class InstructorsResource
class PartnersResource

object DefaultGraphqlSchemaProviderTest extends MockitoSugar {

  val GET_HANDLER = Handler(
    kind = HandlerKind.GET,
    name = "get",
    parameters =
      List(Parameter(name = "id", `type` = "int", attributes = List.empty, default = None)),
    inputBody = None,
    customOutputBody = None,
    attributes = List.empty)

  val MULTIGET_HANDLER = Handler(
    kind = HandlerKind.MULTI_GET,
    name = "multiGet",
    parameters =
      List(Parameter(name = "ids", `type` = "List[int]", attributes = List.empty, default = None)),
    inputBody = None,
    customOutputBody = None,
    attributes = List.empty)

  val COURSES_RESOURCE_ID = ResourceName("courses", 1)
  val COURSES_RESOURCE = Resource(
    kind = ResourceKind.COLLECTION,
    name = "courses",
    version = Some(1),
    parentClass = None,
    keyType = "string",
    valueType = "org.coursera.naptime.test.Course",
    mergedType = MergedCourse.SCHEMA.getFullName,
    handlers = List(GET_HANDLER, MULTIGET_HANDLER),
    className = "org.coursera.naptime.test.CoursesResource",
    attributes = List.empty)

  val INSTRUCTORS_RESOURCE_ID = ResourceName("instructors", 1)
  val INSTRUCTORS_RESOURCE = Resource(
    kind = ResourceKind.COLLECTION,
    name = INSTRUCTORS_RESOURCE_ID.topLevelName,
    version = Some(INSTRUCTORS_RESOURCE_ID.version),
    parentClass = None,
    keyType = "string",
    valueType = "org.coursera.naptime.test.Instructor",
    mergedType = MergedInstructor.SCHEMA.getFullName,
    handlers = List(GET_HANDLER, MULTIGET_HANDLER),
    className = "org.coursera.naptime.test.InstructorsResource",
    attributes = List.empty)

  val PARTNERS_RESOURCE_ID = ResourceName("partners", 1)
  val PARTNERS_RESOURCE = Resource(
    kind = ResourceKind.COLLECTION,
    name = PARTNERS_RESOURCE_ID.topLevelName,
    version = Some(PARTNERS_RESOURCE_ID.version),
    parentClass = None,
    keyType = "string",
    valueType = "org.coursera.naptime.test.Partner",
    mergedType = MergedPartner.SCHEMA.getFullName,
    handlers = List.empty,
    className = "org.coursera.naptime.test.PartnersResource",
    attributes = List.empty)

  val RESOURCE_SCHEMAS = Seq(COURSES_RESOURCE, INSTRUCTORS_RESOURCE, PARTNERS_RESOURCE)

  val TYPE_SCHEMAS = Map(
    MergedCourse.SCHEMA.getFullName -> MergedCourse.SCHEMA,
    MergedInstructor.SCHEMA.getFullName -> MergedInstructor.SCHEMA,
    MergedPartner.SCHEMA.getFullName -> MergedPartner.SCHEMA)

  // REFER TO https://github.com/sangria-graphql/sangria/blob/v1.4.0/src/main/scala/sangria/schema/package.scala#L175
  val DEFAULT_TYPES =
    Set("ID", "root", "Boolean", "Long", "Float", "Int", "BigInt", "String", "BigDecimal")

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
    "CoursesV1_oldMember",
    "CoursesV1_newMember",
    "CoursesV1_originalId",
    "CoursesV1_platformSpecificData",
    "CoursesV1_org_coursera_naptime_ari_graphql_models_OldPlatformData",
    "CoursesV1_org_coursera_naptime_ari_graphql_models_NewPlatformData",
    "PartnersV1",
    "ResponsePagination",
    "CoursesV1_stringMember",
    "DataMap")

  val FILTERED_TYPES =
    Set("PartnersV1", "PartnersV1_org_coursera_naptime_ari_graphql_models_Coordinates")

  val extraTypes = TYPE_SCHEMAS.map { case (key, value) => Keyed(key, value) }.toList

  def simpleSchemaProvider(): SchemaProvider = {

    val courseRouterBuilder = mock[ResourceRouterBuilder]
    when(courseRouterBuilder.schema).thenReturn(COURSES_RESOURCE)
    when(courseRouterBuilder.types).thenReturn(extraTypes)
    when(courseRouterBuilder.resourceClass())
      .thenReturn(classOf[CoursesResource].asInstanceOf[Class[courseRouterBuilder.ResourceClass]])

    val instructorRouterBuilder = mock[ResourceRouterBuilder]
    when(instructorRouterBuilder.schema).thenReturn(INSTRUCTORS_RESOURCE)
    when(instructorRouterBuilder.types).thenReturn(extraTypes)
    when(instructorRouterBuilder.resourceClass()).thenReturn(
      classOf[InstructorsResource].asInstanceOf[Class[instructorRouterBuilder.ResourceClass]])

    val partnerRouterBuilder = mock[ResourceRouterBuilder]
    when(partnerRouterBuilder.schema).thenReturn(PARTNERS_RESOURCE)
    when(partnerRouterBuilder.types).thenReturn(extraTypes)
    when(partnerRouterBuilder.resourceClass())
      .thenReturn(classOf[PartnersResource].asInstanceOf[Class[partnerRouterBuilder.ResourceClass]])

    val injector = mock[Injector]
    new LocalSchemaProvider(
      NaptimeRoutes(
        injector,
        Set(courseRouterBuilder, instructorRouterBuilder, partnerRouterBuilder)))
  }

  def emptySchemaProvider() = {
    val injector = mock[Injector]
    new LocalSchemaProvider(NaptimeRoutes(injector, Set.empty))
  }
}
