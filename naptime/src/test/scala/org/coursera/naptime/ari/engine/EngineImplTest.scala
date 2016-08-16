package org.coursera.naptime.ari.engine

import com.google.inject.Injector
import com.linkedin.data.DataList
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.ari.graphql.models.Coordinates
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.router2.ResourceRouterBuilder
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind
import org.hamcrest.Matcher
import org.junit.Test
import org.mockito.ArgumentMatcher
import org.mockito.Matchers.argThat
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.JsNumber
import play.api.libs.json.JsString
import play.api.test.FakeRequest

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CoursesResource
class InstructorsResource

class EngineImplTest extends AssertionsForJUnit with ScalaFutures with MockitoSugar {
  import EngineImplTest._

  private[this] def MatchesResourceType(resourceName: ResourceName): Matcher[Request] = {
    new ArgumentMatcher[Request] {
      override def matches(argument: scala.Any): Boolean = {
        argument match {
          case Request(_, topLevelRequests)
            if topLevelRequests.length == 1
              && topLevelRequests.head.resource == resourceName =>
            true
          case _ => false
        }
      }
    }
  }

  val fetcherApi = mock[FetcherApi]

  val extraTypes = TYPE_SCHEMAS.map { case (key, value) => Keyed(key, value) }.toList

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

  val injector = mock[Injector]
  val naptimeRoutes = NaptimeRoutes(injector, Set(courseRouterBuilder, instructorRouterBuilder))
  val engine = new EngineImpl(naptimeRoutes, fetcherApi)

  @Test
  def singleResourceFetch_Courses(): Unit = {
    val request = Request(
      requestHeader = FakeRequest(),
      topLevelRequests = List(TopLevelRequest(
        resource = COURSES_RESOURCE_ID,
        selection = RequestField(
          name = "CoursesV1",
          alias = None,
          args = Set("id" -> JsString(COURSE_A.id)),
          selections = List(
            RequestField("id", None, Set.empty, List.empty),
            RequestField("slug", None, Set.empty, List.empty),
            RequestField("name", None, Set.empty, List.empty))))))

    val topLevelDataList = new DataList()
    topLevelDataList.add(COURSE_A.id)
    val fetcherResponse = Response(
      topLevelIds = Map(request.topLevelRequests.head -> topLevelDataList),
      data = Map(COURSES_RESOURCE_ID -> Map(
        COURSE_A.id -> COURSE_A.data())))

    when(fetcherApi.data(request)).thenReturn(Future.successful(fetcherResponse))

    val result = engine.execute(request).futureValue

    assert(result.topLevelIds.contains(request.topLevelRequests.head))
    assert(1 === result.topLevelIds(request.topLevelRequests.head).size())
    assert(COURSE_A.id === result.topLevelIds(request.topLevelRequests.head).get(0))
    assert(result.data.contains(COURSES_RESOURCE_ID))
    val coursesData = result.data(COURSES_RESOURCE_ID)
    assert(1 === coursesData.size)
    assert(coursesData.contains(COURSE_A.id))
    val courseAResponse = coursesData(COURSE_A.id)
    assert(COURSE_A.id === courseAResponse.getString("id"))
    assert(COURSE_A.name === courseAResponse.getString("name"))
    assert(COURSE_A.slug === courseAResponse.getString("slug"))
  }

  @Test
  def singleResourceFetch_Instructors(): Unit = {
    val request = Request(
      requestHeader = FakeRequest(),
      topLevelRequests = List(TopLevelRequest(
        resource = INSTRUCTORS_RESOURCE_ID,
        selection = RequestField(
          name = "InstructorsV1",
          alias = None,
          args = Set("id" -> JsString(INSTRUCTOR_1.id)),
          selections = List(
            RequestField("id", None, Set.empty, List.empty),
            RequestField("name", None, Set.empty, List.empty),
            RequestField("title", None, Set.empty, List.empty))))))

    val topLevelDataList = new DataList()
    topLevelDataList.add(INSTRUCTOR_1.id)
    val fetcherResponse = Response(
      topLevelIds = Map(request.topLevelRequests.head -> topLevelDataList),
      data = Map(INSTRUCTORS_RESOURCE_ID -> Map(
        INSTRUCTOR_1.id -> INSTRUCTOR_1.data())))

    when(fetcherApi.data(request)).thenReturn(Future.successful(fetcherResponse))

    val result = engine.execute(request).futureValue

    assert(result.topLevelIds.contains(request.topLevelRequests.head))
    assert(1 === result.topLevelIds(request.topLevelRequests.head).size())
    assert(INSTRUCTOR_1.id === result.topLevelIds(request.topLevelRequests.head).get(0))
    assert(result.data.contains(INSTRUCTORS_RESOURCE_ID))
    val instructorsData = result.data(INSTRUCTORS_RESOURCE_ID)
    assert(1 === instructorsData.size)
    assert(instructorsData.contains(INSTRUCTOR_1.id))
    val instructor1Response = instructorsData(INSTRUCTOR_1.id)
    assert(INSTRUCTOR_1.id === instructor1Response.getString("id"))
    assert(INSTRUCTOR_1.name === instructor1Response.getString("name"))
    assert(INSTRUCTOR_1.title === instructor1Response.getString("title"))
  }

  // TODO: Check pagination.

  // TODO: Add invalid schema-based tests.

  /**
   * Runs 2 simple top level requests for independent resources, and ensures the response is appropriately merged.
   */
  @Test
  def multiResourceFetch(): Unit = {
    val request = Request(
      requestHeader = FakeRequest(),
      topLevelRequests = List(
        TopLevelRequest(
          resource = COURSES_RESOURCE_ID,
          selection = RequestField(
            name = "get",
            alias = None,
            args = Set("id" -> JsString(COURSE_A.id)),
            selections = List(
              RequestField("id", None, Set.empty, List.empty),
              RequestField("slug", None, Set.empty, List.empty),
              RequestField("name", None, Set.empty, List.empty)))),
        TopLevelRequest(
          resource = INSTRUCTORS_RESOURCE_ID,
          selection = RequestField(
            name = "InstructorsV1",
            alias = None,
            args = Set("id" -> JsString(INSTRUCTOR_1.id)),
            selections = List(
              RequestField("id", None, Set.empty, List.empty),
              RequestField("name", None, Set.empty, List.empty),
              RequestField("title", None, Set.empty, List.empty))))))

    val topLevelDataListCourse = new DataList(List(COURSE_A.id).asJava)
    val fetcherResponseCourse = Response(
      topLevelIds = Map(request.topLevelRequests.head -> topLevelDataListCourse),
      data = Map(COURSES_RESOURCE_ID -> Map(
        COURSE_A.id -> COURSE_A.data())))
    val topLevelDataListInstructor = new DataList()
    topLevelDataListInstructor.add(INSTRUCTOR_1.id)
    val fetcherResponseInstructors = Response(
      topLevelIds = Map(request.topLevelRequests.tail.head -> topLevelDataListInstructor),
      data = Map(INSTRUCTORS_RESOURCE_ID -> Map(
        INSTRUCTOR_1.id -> INSTRUCTOR_1.data())))

    when(fetcherApi.data(argThat(MatchesResourceType(COURSES_RESOURCE_ID)))).thenReturn(
      Future.successful(fetcherResponseCourse))
    when(fetcherApi.data(argThat(MatchesResourceType(INSTRUCTORS_RESOURCE_ID)))).thenReturn(
      Future.successful(fetcherResponseInstructors))

    val result = engine.execute(request).futureValue

    assert(result.topLevelIds.contains(request.topLevelRequests.head))
    assert(1 === result.topLevelIds(request.topLevelRequests.head).size())
    assert(COURSE_A.id === result.topLevelIds(request.topLevelRequests.head).get(0))
    assert(result.data.contains(COURSES_RESOURCE_ID))
    val coursesData = result.data(COURSES_RESOURCE_ID)
    assert(1 === coursesData.size)
    assert(coursesData.contains(COURSE_A.id))
    val courseAResponse = coursesData(COURSE_A.id)
    assert(COURSE_A.id === courseAResponse.getString("id"))
    assert(COURSE_A.name === courseAResponse.getString("name"))
    assert(COURSE_A.slug === courseAResponse.getString("slug"))

    assert(result.topLevelIds.contains(request.topLevelRequests.head))
    assert(1 === result.topLevelIds(request.topLevelRequests.head).size())
    assert(INSTRUCTOR_1.id === result.topLevelIds(request.topLevelRequests.tail.head).get(0))
    assert(result.data.contains(INSTRUCTORS_RESOURCE_ID))
    val instructorsData = result.data(INSTRUCTORS_RESOURCE_ID)
    assert(1 === instructorsData.size)
    assert(instructorsData.contains(INSTRUCTOR_1.id))
    val instructor1Response = instructorsData(INSTRUCTOR_1.id)
    assert(INSTRUCTOR_1.id === instructor1Response.getString("id"))
    assert(INSTRUCTOR_1.name === instructor1Response.getString("name"))
    assert(INSTRUCTOR_1.title === instructor1Response.getString("title"))
  }

  @Test
  def nonJoiningNestedField(): Unit = {
    val request = Request(
      requestHeader = FakeRequest(),
      topLevelRequests = List(
        TopLevelRequest(
          resource = PARTNERS_RESOURCE_ID,
          selection = RequestField(
            name = "get",
            alias = None,
            args = Set("id" -> JsNumber(PARTNER_123.id)),
            selections = List(
              RequestField("id", None, Set.empty, List.empty),
              RequestField("slug", None, Set.empty, List.empty),
              RequestField("name", None, Set.empty, List.empty),
              RequestField("geolocation", None, Set.empty, List(
                RequestField("latitude", None, Set.empty, List.empty),
                RequestField("longitude", None, Set.empty, List.empty))))))))

    val topLevelDataList = new DataList(List(new Integer(PARTNER_123.id)).asJava)
    val fetcherResponse = Response(
      topLevelIds = Map(request.topLevelRequests.head -> topLevelDataList),
      data = Map(PARTNERS_RESOURCE_ID -> Map(
        new Integer(PARTNER_123.id) -> PARTNER_123.data())))
    when(fetcherApi.data(argThat(MatchesResourceType(PARTNERS_RESOURCE_ID)))).thenReturn(
      Future.successful(fetcherResponse))

    val result = engine.execute(request).futureValue

    assert(result.topLevelIds.contains(request.topLevelRequests.head))
    assert(1 === result.topLevelIds(request.topLevelRequests.head).size())
    assert(PARTNER_123.id === result.topLevelIds(request.topLevelRequests.head).get(0))
    assert(result.data.contains(PARTNERS_RESOURCE_ID))
    val partnersData = result.data(PARTNERS_RESOURCE_ID)
    assert(1 === partnersData.size)
    assert(partnersData.contains(new Integer(PARTNER_123.id)))
    val partner1Response = partnersData(new Integer(PARTNER_123.id))
    assert(PARTNER_123.id === partner1Response.getInteger("id"))
    assert(PARTNER_123.name === partner1Response.getString("name"))
    assert(PARTNER_123.geolocation.data() === partner1Response.getDataMap("geolocation"))
  }

  /**
   * Gets a course, and then the related instructors for that course.
   *
   * This tests joining against a list of "foreign key" identifiers.
   */
  @Test
  def simpleNestedJoinInstructors(): Unit = {
    val request = Request(
      requestHeader = FakeRequest(),
      topLevelRequests = List(
        TopLevelRequest(
          resource = COURSES_RESOURCE_ID,
          selection = RequestField(
            name = "get",
            alias = None,
            args = Set("id" -> JsString(COURSE_A.id)),
            selections = List(
              RequestField("id", None, Set.empty, List.empty),
              RequestField("slug", None, Set.empty, List.empty),
              RequestField("name", None, Set.empty, List.empty),
              RequestField("instructors", None, Set.empty, List(
                RequestField("id", None, Set.empty, List.empty),
                RequestField("name", None, Set.empty, List.empty),
                RequestField("title", None, Set.empty, List.empty))))))))

    val fetcherResponseCourse = Response(
      topLevelIds = Map(request.topLevelRequests.head -> new DataList(List(COURSE_A.id).asJava)),
      data = Map(COURSES_RESOURCE_ID -> Map(COURSE_A.id -> COURSE_A.data())))


    val expectedInstructorRequest = TopLevelRequest(
      resource = INSTRUCTORS_RESOURCE_ID,
      selection = RequestField(
        name = "multiGet",
        alias = None,
        args = Set("ids" -> JsString("instructor1Id")),
        selections = request.topLevelRequests.head.selection.selections.drop(3).head.selections))
    val fetcherResponseInstructors = Response(
      topLevelIds = Map(expectedInstructorRequest -> new DataList(List(INSTRUCTOR_1.id).asJava)),
      data = Map(INSTRUCTORS_RESOURCE_ID -> Map(INSTRUCTOR_1.id -> INSTRUCTOR_1.data())))

    when(fetcherApi.data(argThat(MatchesResourceType(COURSES_RESOURCE_ID)))).thenReturn(
      Future.successful(fetcherResponseCourse))
    when(fetcherApi.data(argThat(MatchesResourceType(INSTRUCTORS_RESOURCE_ID)))).thenReturn(
      Future.successful(fetcherResponseInstructors))

    val result = engine.execute(request).futureValue

    assert(1 === result.topLevelIds.size, s"Result: $result")
    assert(result.topLevelIds.contains(request.topLevelRequests.head))
    assert(1 === result.topLevelIds(request.topLevelRequests.head).size())
    assert(COURSE_A.id === result.topLevelIds(request.topLevelRequests.head).get(0))

    assert(result.data.contains(COURSES_RESOURCE_ID))
    val coursesData = result.data(COURSES_RESOURCE_ID)
    assert(1 === coursesData.size)
    assert(coursesData.contains(COURSE_A.id))
    val courseAResponse = coursesData(COURSE_A.id)
    assert(COURSE_A.id === courseAResponse.getString("id"))
    assert(COURSE_A.name === courseAResponse.getString("name"))
    assert(COURSE_A.slug === courseAResponse.getString("slug"))

    assert(result.data.contains(INSTRUCTORS_RESOURCE_ID))
    val instructorsData = result.data(INSTRUCTORS_RESOURCE_ID)
    assert(1 === instructorsData.size)
    assert(instructorsData.contains(INSTRUCTOR_1.id))
    val instructor1Response = instructorsData(INSTRUCTOR_1.id)
    assert(INSTRUCTOR_1.id === instructor1Response.getString("id"))
    assert(INSTRUCTOR_1.name === instructor1Response.getString("name"))
    assert(INSTRUCTOR_1.title === instructor1Response.getString("title"))
  }

  /**
   * Gets a course, and then the related partner for that course.
   *
   * This tests joining against a single foreign key field.
   */
  @Test
  def simpleNestedJoinPartners(): Unit = {
    val request = Request(
      requestHeader = FakeRequest(),
      topLevelRequests = List(
        TopLevelRequest(
          resource = COURSES_RESOURCE_ID,
          selection = RequestField(
            name = "get",
            alias = None,
            args = Set("id" -> JsString(COURSE_A.id)),
            selections = List(
              RequestField("id", None, Set.empty, List.empty),
              RequestField("slug", None, Set.empty, List.empty),
              RequestField("name", None, Set.empty, List.empty),
              RequestField("partner", None, Set.empty, List(
                RequestField("id", None, Set.empty, List.empty),
                RequestField("slug", None, Set.empty, List.empty),
                RequestField("name", None, Set.empty, List.empty),
                RequestField("geolocation", None, Set.empty, List(
                  RequestField("latitude", None, Set.empty, List.empty),
                  RequestField("longitude", None, Set.empty, List.empty))))))))))

    val fetcherResponseCourse = Response(
      topLevelIds = Map(request.topLevelRequests.head -> new DataList(List(COURSE_A.id).asJava)),
      data = Map(COURSES_RESOURCE_ID -> Map(COURSE_A.id -> COURSE_A.data())))

    val expectedPartnersRequest = TopLevelRequest(
      resource = PARTNERS_RESOURCE_ID,
      selection = RequestField(
        name = "multiGet",
        alias = None,
        args = Set("ids" -> JsString(s"${PARTNER_123.id}")),
        selections = request.topLevelRequests.head.selection.selections.drop(3).head.selections))
    val fetcherResponsePartners = Response(
      topLevelIds = Map(expectedPartnersRequest -> new DataList(List(new Integer(PARTNER_123.id)).asJava)),
      data = Map(PARTNERS_RESOURCE_ID -> Map(new Integer(PARTNER_123.id) -> PARTNER_123.data())))

    when(fetcherApi.data(argThat(MatchesResourceType(COURSES_RESOURCE_ID)))).thenReturn(
      Future.successful(fetcherResponseCourse))
    when(fetcherApi.data(argThat(MatchesResourceType(PARTNERS_RESOURCE_ID)))).thenReturn(
      Future.successful(fetcherResponsePartners))

    val result = engine.execute(request).futureValue

    assert(1 === result.topLevelIds.size, s"Result: $result")
    assert(result.topLevelIds.contains(request.topLevelRequests.head))
    assert(1 === result.topLevelIds(request.topLevelRequests.head).size())
    assert(COURSE_A.id === result.topLevelIds(request.topLevelRequests.head).get(0))

    assert(result.data.contains(COURSES_RESOURCE_ID))
    val coursesData = result.data(COURSES_RESOURCE_ID)
    assert(1 === coursesData.size)
    assert(coursesData.contains(COURSE_A.id))
    val courseAResponse = coursesData(COURSE_A.id)
    assert(COURSE_A.id === courseAResponse.getString("id"))
    assert(COURSE_A.name === courseAResponse.getString("name"))
    assert(COURSE_A.slug === courseAResponse.getString("slug"))

    assert(result.data.contains(PARTNERS_RESOURCE_ID))
    val partnersData = result.data(PARTNERS_RESOURCE_ID)
    assert(1 === partnersData.size)
    assert(partnersData.contains(new Integer(PARTNER_123.id)))
    val partner123Response = partnersData(new Integer(PARTNER_123.id))
    assert(PARTNER_123.id === partner123Response.getInteger("id"))
    assert(PARTNER_123.name === partner123Response.getString("name"))
    assert(PARTNER_123.slug === partner123Response.getString("slug"))
    assert(PARTNER_123.geolocation.data() === partner123Response.getDataMap("geolocation"))
  }
}

object EngineImplTest {
  val COURSE_A = MergedCourse(
    id = "courseAId",
    name = "Machine Learning",
    slug = "machine-learning",
    description = Some("An awesome course on machine learning."),
    instructors = List("instructor1Id"),
    partner = 123,
    originalId = "")

  val INSTRUCTOR_1 = MergedInstructor(
    id = "instructor1Id",
    name = "Professor X",
    title = "Chair",
    bio = "Professor X's bio",
    courses = List(COURSE_A.id))

  val PARTNER_123 = MergedPartner(
    id = 123,
    name = "University X",
    slug = "x-university",
    geolocation = Coordinates(37.386824, -122.061005))

  val COURSES_RESOURCE_ID = ResourceName("courses", 1)
  val COURSES_RESOURCE = Resource(
    kind = ResourceKind.COLLECTION,
    name = "courses",
    version = Some(1),
    parentClass = None,
    keyType = "string",
    valueType = "org.coursera.naptime.test.Course",
    mergedType = MergedCourse.SCHEMA.getFullName,
    handlers = List.empty,
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
    mergedType = "org.coursera.naptime.test.InstructorsResourceModel",
    handlers = List.empty,
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
    mergedType = "org.coursera.naptime.test.PartnersResourceModel",
    handlers = List.empty,
    className = "org.coursera.naptime.test.PartnersResource",
    attributes = List.empty)

  val RESOURCE_SCHEMAS = Seq(
    COURSES_RESOURCE,
    INSTRUCTORS_RESOURCE,
    PARTNERS_RESOURCE)

  val TYPE_SCHEMAS = Map(
    MergedCourse.SCHEMA.getFullName -> MergedCourse.SCHEMA)
}
