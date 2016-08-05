package org.coursera.naptime.ari.engine

import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.ResourceResponse
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.JsString
import play.api.test.FakeRequest

import scala.concurrent.Future

class EngineImplTest extends AssertionsForJUnit with ScalaFutures with MockitoSugar {

  import EngineImplTest._

  val fetcherApi = mock[FetcherApi]

  val engine = new EngineImpl(RESOURCE_SCHEMAS, TYPE_SCHEMAS, fetcherApi)

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

    val fetcherResponse = Response(
      Map(COURSES_RESOURCE_ID -> ResourceResponse(
        key = COURSES_RESOURCE_ID.identifier,
        models = List(COURSE_A.data()),
        pagination = ResponsePagination(next = None)))
    )

    when(fetcherApi.data(request)).thenReturn(Future.successful(fetcherResponse))

    val result = engine.execute(request).futureValue

    assert(result.output.contains(COURSES_RESOURCE_ID))
    val coursesResult = result.output(COURSES_RESOURCE_ID)
    assert(1 === coursesResult.models.length)
    assert(COURSE_A.id === coursesResult.models.head.getString("id"))
    assert(COURSE_A.name === coursesResult.models.head.getString("name"))
    assert(COURSE_A.slug === coursesResult.models.head.getString("slug"))
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

    val fetcherResponse = Response(
      Map(INSTRUCTORS_RESOURCE_ID -> ResourceResponse(
        key = INSTRUCTORS_RESOURCE_ID.identifier,
        models = List(INSTRUCTOR_1.data()),
        pagination = ResponsePagination(next = None))))

    when(fetcherApi.data(request)).thenReturn(Future.successful(fetcherResponse))

    val result = engine.execute(request).futureValue

    assert(result.output.contains(INSTRUCTORS_RESOURCE_ID))
    val instructorsResult = result.output(INSTRUCTORS_RESOURCE_ID)
    assert(1 === instructorsResult.models.length)
    assert(INSTRUCTOR_1.id === instructorsResult.models.head.getString("id"))
    assert(INSTRUCTOR_1.name === instructorsResult.models.head.getString("name"))
    assert(INSTRUCTOR_1.title === instructorsResult.models.head.getString("title"))
  }

  // TODO: Add sophisticated tests that involve joining resources.

  // TODO: Add invalid schema-based tests.
}

object EngineImplTest {
  val COURSE_A = MergedCourse(
    id = "courseAId",
    name = "Machine Learning",
    slug = "machine-learning",
    description = Some("An awesome course on machine learning."),
    instructors = List("instructor1Id"),
    originalId = "")

  val INSTRUCTOR_1 = MergedInstructor(
    id = "instructor1Id",
    name = "Professor X",
    title = "Chair",
    bio = "Professor X's bio",
    courses = List(COURSE_A.id))

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

  val RESOURCE_SCHEMAS = Seq(
    COURSES_RESOURCE,
    INSTRUCTORS_RESOURCE)

  val TYPE_SCHEMAS = Map(
    MergedCourse.SCHEMA.getFullName -> MergedCourse.SCHEMA)
}
