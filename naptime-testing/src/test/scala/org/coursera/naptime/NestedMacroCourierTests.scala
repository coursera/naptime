package org.coursera.naptime

import javax.inject.Inject

import akka.stream.Materializer
import com.google.inject.Binder
import com.google.inject.Guice
import com.google.inject.Module
import com.linkedin.data.DataMap
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.NestedMacroCourierTests.CoursesResource
import org.coursera.naptime.ari.FetcherError
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.fetcher.LocalFetcher
import org.coursera.naptime.couriertests.ExpectedMergedCourse
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.CourierCollectionResource
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.router2.Router
import org.junit.Test
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.JsString
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext

/**
 * This test suite uses Courier to exercise advanced use cases for Naptime.
 */
object NestedMacroCourierTests {

  object CoursesResource {
    val ID = ResourceName("courses", 1)
  }

  class CoursesResource @Inject()(
      implicit executionContext: ExecutionContext,
      materializer: Materializer)
      extends CourierCollectionResource[String, Course] {
    override def resourceName: String = CoursesResource.ID.topLevelName

    override implicit lazy val Fields: Fields[Course] = BaseFields
      .withReverseRelations(
        "instructors" -> MultiGetReverseRelation(
          resourceName = ResourceName("instructors", 1),
          ids = "$instructorIds",
          description = "Instructors for the course"))

    /**
     * This `var` can be overridden to help fake out the implementation in particular functions.
     */
    var relatedFunction: Option[String => List[String]] = None

    private[this] def fallbackRelatedFunction(id: String): List[String] = {
      id match {
        case "abc" => List("123")
        case "xyz" => List.empty
        case "qrs" => List("456", "789")
        case _     => List.empty
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
        case _     => List.empty
      }
      Ok(ids.map(id => makeCourse(id)))
    }
  }

  class InstructorsResource @Inject()(implicit ec: ExecutionContext, mat: Materializer)
      extends CourierCollectionResource[String, Instructor]() {
    override def resourceName: String = "instructors"

    def multiGet(ids: Set[String]) = Nap.multiGet { ctx =>
      ???
    }

  }

  val courseRouter = Router.build[CoursesResource]
  val instructorRouter = Router.build[InstructorsResource]
}

class NestedMacroCourierTests
    extends AssertionsForJUnit
    with ScalaFutures
    with ResourceTestImplicits
    with IntegrationPatience {

  val implicitsModule = new Module {
    override def configure(binder: Binder): Unit = {
      binder.bind(classOf[ExecutionContext]).toInstance(executionContext)
      binder.bind(classOf[Materializer]).toInstance(materializer)
    }
  }

  @Test
  def checkCoursesMergedType(): Unit = {
    val types = NestedMacroCourierTests.courseRouter.types
    assert(3 === types.size, s"Got $types")
    val mergedTypeOpt =
      types.find(_.key == "org.coursera.naptime.NestedMacroCourierTests.CoursesResource.Model")
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
    val mergedType =
      types.find(_.key == "org.coursera.naptime.NestedMacroCourierTests.CoursesResource.Model").get
    assert(
      ExpectedMergedCourse.SCHEMA.getFields === mergedType.value
        .asInstanceOf[RecordDataSchema]
        .getFields)
  }

  @Test
  def coursesLocalFetcher_Get(): Unit = {
    val injector = Guice.createInjector(implicitsModule)
    val routerBuilders =
      Set(NestedMacroCourierTests.courseRouter, NestedMacroCourierTests.instructorRouter)
    val naptimeRoutes = NaptimeRoutes(injector, routerBuilders)
    val fetcher = new LocalFetcher(naptimeRoutes)

    val request = Request(
      requestHeader = FakeRequest(),
      resource = CoursesResource.ID,
      arguments = Set("id" -> JsString("abc")))

    val response = fetcher.data(request, isDebugMode = false).futureValue

    assert(
      response.left.get === FetcherError(
        code = 404,
        message = "Handler was not a RestAction, or Get attempted",
        url = Some("/api/courses.v1?id=abc")))
  }

  @Test
  def coursesLocalFetcher_MultiGet(): Unit = {
    val injector = Guice.createInjector(implicitsModule)
    val routerBuilders =
      Set(NestedMacroCourierTests.courseRouter, NestedMacroCourierTests.instructorRouter)
    val naptimeRoutes = NaptimeRoutes(injector, routerBuilders)
    val fetcher = new LocalFetcher(naptimeRoutes)

    val request = Request(
      requestHeader = FakeRequest(),
      resource = CoursesResource.ID,
      arguments = Set("ids" -> JsString("abc,qrs")))

    val response = fetcher.data(request, isDebugMode = false).futureValue

    assert(2 === response.right.get.data.size)
    assert(response.right.get.data.exists(_.get("id") == "abc"))
    assert("course-abc" === response.right.get.data.find(_.get("id") == "abc").get.get("name"))
  }

  @Test
  def coursesLocalFetcher_Finder(): Unit = {
    val injector = Guice.createInjector(implicitsModule)
    val routerBuilders =
      Set(NestedMacroCourierTests.courseRouter, NestedMacroCourierTests.instructorRouter)
    val naptimeRoutes = NaptimeRoutes(injector, routerBuilders)
    val fetcher = new LocalFetcher(naptimeRoutes)

    val request = Request(
      requestHeader = FakeRequest(),
      resource = CoursesResource.ID,
      arguments = Set("q" -> JsString("search"), "query" -> JsString("xyz")))

    val response = fetcher.data(request, isDebugMode = false).futureValue

    assert(1 === response.right.get.data.size)
    assert(response.right.get.data.exists(_.get("id") == "xyz"))
    assert("course-xyz" === response.right.get.data.find(_.get("id") == "xyz").get.get("name"))
  }
}
