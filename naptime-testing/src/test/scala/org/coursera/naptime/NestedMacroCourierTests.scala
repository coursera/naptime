package org.coursera.naptime

import com.google.inject.Guice
import com.linkedin.data.DataMap
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.NestedMacroCourierTests.CoursesResource
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.ari.fetcher.LocalFetcher
import org.coursera.naptime.couriertests.ExpectedMergedCourse
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.CourierCollectionResource
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.router2.Router
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.JsString
import play.api.test.FakeRequest

/**
 * This test suite uses Courier to exercise advanced use cases for Naptime.
 */
object NestedMacroCourierTests {

  object CoursesResource {
    val ID = ResourceName("courses", 1)
  }
  class CoursesResource extends CourierCollectionResource[String, Course] {
    override def resourceName: String = CoursesResource.ID.topLevelName

    override implicit lazy val Fields: Fields[Course] = BaseFields
      .withReverseRelations(
        "instructors" -> MultiGetReverseRelation(
          resourceName = ResourceName("instructors", 1),
          idsString = "$instructorIds"))

    /**
     * This `var` can be overridden to help fake out the implementation in particular functions.
     */
    var relatedFunction: Option[String => List[String]] = None

    private[this] def fallbackRelatedFunction(id: String): List[String] = {
      id match {
        case "abc" => List("123")
        case "xyz" => List.empty
        case "qrs" => List("456", "789")
        case _ => List.empty
      }
    }

    private[this] def makeCourse(id: String): Keyed[String, Course] = {
      val relatedInstructors = relatedFunction.getOrElse(fallbackRelatedFunction _)(id)

      Keyed(id, Course(s"course-$id", s"$id description", relatedInstructors))
    }

    def get(id: String) = Nap.get { ctx =>
      Ok(makeCourse(id))
    }

    def multiGet(ids: Set[String]) = Nap.multiGet { ctx =>
      Ok(ids.map(id => makeCourse(id)).toList)
    }

    def search(query: Option[String]) = Nap.finder { ctx =>
      val ids = query.map(List(_)).getOrElse(List("abc", "qrs"))
      Ok(ids.map(id => makeCourse(id)))
    }

    def byInstructor(instructorId: String) = Nap.finder { ctx =>
      val ids = instructorId match {
        case "123" => List("xyz")
        case "456" => List("xyz", "abc")
        case "789" => List("qrs")
        case _ => List.empty
      }
      Ok(ids.map(id => makeCourse(id)))
    }
  }

  class InstructorsResource extends CourierCollectionResource[String, Instructor] {
    override def resourceName: String = "instructors"

    def multiGet(ids: Set[String]) = Nap.multiGet { ctx =>
      ???
    }

  }

  val courseRouter = Router.build[CoursesResource]
  val instructorRouter = Router.build[InstructorsResource]
}

class NestedMacroCourierTests extends AssertionsForJUnit with ScalaFutures {

  @Test
  def checkCoursesMergedType(): Unit = {
    val types = NestedMacroCourierTests.courseRouter.types
    assert(3 === types.size, s"Got $types")
    val mergedTypeOpt = types.find(_.key == "org.coursera.naptime.NestedMacroCourierTests.CoursesResource.Model")
    assert(mergedTypeOpt.isDefined)
    val mergedType = mergedTypeOpt.get
    assert(!mergedType.value.hasError)
    assert(mergedType.value.getType === DataSchema.Type.RECORD)
    val mergedValueRecord = mergedType.value.asInstanceOf[RecordDataSchema]
    assert(5 === mergedValueRecord.getFields.size(), mergedValueRecord)
    val instructorsField = mergedValueRecord.getField("instructors")
    assert(null != instructorsField, instructorsField)
    assert(null != instructorsField.getProperties)
    val typesProperty = instructorsField.getProperties.get(Types.Relations.REVERSE_PROPERTY_NAME)
    assert(null != typesProperty)
    assert(typesProperty.isInstanceOf[DataMap])
    val annotation = typesProperty.asInstanceOf[DataMap]
    assert(annotation.getString("resourceName") === "instructors.v1")
    assert(annotation.getDataMap("arguments").getString("ids") === "$instructorIds")
    assert(annotation.getString("relationType") === "MULTI_GET")
  }

  @Test
  def checkMergedCourseModelSchema(): Unit = {
    val types = NestedMacroCourierTests.courseRouter.types
    val mergedType = types.find(_.key == "org.coursera.naptime.NestedMacroCourierTests.CoursesResource.Model").get
    assert(ExpectedMergedCourse.SCHEMA.getFields === mergedType.value.asInstanceOf[RecordDataSchema].getFields)
  }

  @Test
  def coursesLocalFetcher_Get(): Unit = {
    val injector = Guice.createInjector()
    val routerBuilders = Set(NestedMacroCourierTests.courseRouter, NestedMacroCourierTests.instructorRouter)
    val naptimeRoutes = NaptimeRoutes(injector, routerBuilders)
    val fetcher = new LocalFetcher(naptimeRoutes)

    val request = Request(
      requestHeader = FakeRequest(),
      topLevelRequests = List(
        TopLevelRequest(
          CoursesResource.ID,
          RequestField(
            name = "get",
            alias = None,
            args = Set("id" -> JsString("abc")),
            selections = List(
              RequestField("id", None, Set.empty, List.empty),
              RequestField("name", None, Set.empty, List.empty))))))

    val interceptedException = intercept[TestFailedException] {
      val response = fetcher.data(request).futureValue
    }

    assert(interceptedException.getMessage.contains("Get attempted"))
  }

  @Test
  def coursesLocalFetcher_MultiGet(): Unit = {
    val injector = Guice.createInjector()
    val routerBuilders = Set(NestedMacroCourierTests.courseRouter, NestedMacroCourierTests.instructorRouter)
    val naptimeRoutes = NaptimeRoutes(injector, routerBuilders)
    val fetcher = new LocalFetcher(naptimeRoutes)

    val request = Request(
      requestHeader = FakeRequest(),
      topLevelRequests = List(
        TopLevelRequest(
          CoursesResource.ID,
          RequestField(
            name = "multiGet",
            alias = None,
            args = Set("ids" -> JsString("abc,qrs")),
            selections = List(
              RequestField("id", None, Set.empty, List.empty),
              RequestField("name", None, Set.empty, List.empty))))))

    val response = fetcher.data(request).futureValue


    assert(1 === response.topLevelResponses.size)
    assert(1 === response.data.size)
    assert(response.data.contains(CoursesResource.ID))
    val coursesResponse = response.data(CoursesResource.ID)
    assert(2 === coursesResponse.size)
    assert(coursesResponse.contains("abc"))
    assert("course-abc" === coursesResponse("abc").get("name"),
      s"Data map: ${coursesResponse("abc")}")
    // TODO: Check pagination
  }

  @Test
  def coursesLocalFetcher_Finder(): Unit = {
    val injector = Guice.createInjector()
    val routerBuilders = Set(NestedMacroCourierTests.courseRouter, NestedMacroCourierTests.instructorRouter)
    val naptimeRoutes = NaptimeRoutes(injector, routerBuilders)
    val fetcher = new LocalFetcher(naptimeRoutes)

    val request = Request(
      requestHeader = FakeRequest(),
      topLevelRequests = List(
        TopLevelRequest(
          CoursesResource.ID,
          RequestField(
            name = "search",
            alias = None,
            args = Set("q" -> JsString("search"), "query" -> JsString("xyz")),
            selections = List(
              RequestField("id", None, Set.empty, List.empty),
              RequestField("name", None, Set.empty, List.empty))))))

    val response = fetcher.data(request).futureValue

    assert(1 === response.topLevelResponses.size)
    assert(1 === response.data.size)
    assert(response.data.contains(CoursesResource.ID))
    val coursesResponse = response.data(CoursesResource.ID)
    assert(1 === coursesResponse.size)
    assert(coursesResponse.contains("xyz"))
    assert("course-xyz" === coursesResponse("xyz").get("name"),
      s"Data map: ${coursesResponse("xyz")}")
    // TODO: Check pagination
  }
}
