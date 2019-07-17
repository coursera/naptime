/*
 * Copyright 2016 Coursera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.coursera.naptime.actions

import akka.stream.Materializer
import com.linkedin.data.DataList
import org.coursera.common.stringkey.StringKey
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.courier.CourierFormats
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.RestError
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.Errors
import org.coursera.naptime.FacetField
import org.coursera.naptime.FacetFieldValue
import org.coursera.naptime.ResourceFields
import org.coursera.naptime.Ok
import org.coursera.naptime.QueryFields
import org.coursera.naptime.QueryIncludes
import org.coursera.naptime.RequestPagination
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResourceTestImplicits
import org.coursera.naptime.actions.util.Validators
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import play.api.Application
import play.api.http.HeaderNames
import play.api.http.HttpEntity
import play.api.http.Status
import play.api.http.Writeable
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.mvc.AnyContent
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers.defaultAwaitTimeout

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object RestActionCategoryEngine2Test {

  case class Person(name: String, email: String)

  object Person {
    implicit val jsonFormat: OFormat[Person] = Json.format[Person]
  }

  /**
   * A test resource used for testing the DataMap-centric rest engines. (Uses Play-JSON adapters)
   *
   * Note: because we're not using the routing components of Naptime, we can get away with multiple
   * get's / etc.
   *
   * In general, it is a very bad idea to have multiple gets, creates, etc, in a single resource.
   */
  class PlayJsonTestResource(
      implicit val executionContext: ExecutionContext,
      val materializer: Materializer,
      val application: Application)
    extends TopLevelCollectionResource[Int, Person] {
    import RestActionCategoryEngine2._

    override def keyFormat: KeyFormat[Int] = KeyFormat.intKeyFormat
    override def resourceName: String = "testResource"
    override implicit val resourceFormat: OFormat[Person] = Person.jsonFormat
    implicit val fields = Fields.withDefaultFields("name").withRelated(
      "relatedCaseClass" -> RelatedResources.CaseClass.relatedName,
      "relatedCourier" -> RelatedResources.Courier.relatedName)

    def get1(id: Int) = Nap.get { ctx =>
      RelatedResources.addRelated {
        Ok(Keyed(id, Person(s"$id", s"$id@coursera.org")))
      }
    }

    def get2(id: Int) = Nap.get { ctx =>
      throw Errors.NotFound(errorCode = "id", msg = s"Bad id $id")
    }

    def multiGet(ids: Set[Int]) = Nap.multiGet { ctx =>
      RelatedResources.addRelated {
        Ok(ids.map(id => Keyed(id, Person(s"$id", s"$id@coursera.org"))).toSeq)
      }
    }

    def create1 = Nap.create { ctx =>
      Ok(Keyed(2, Some(Person("newId", "newId@coursera.org"))))
    }

    def create2 = Nap.create { ctx =>
      Ok(Keyed(3, None))
    }

    def create3 = Nap.catching {
      case e: RuntimeException => RestError(NaptimeActionException(Status.BAD_REQUEST, Some("boom"), None))
    }.create { ctx =>
      throw new RuntimeException("boooooooom")
    }

    def delete1(id: Int) = Nap.delete {
      Ok(())
    }
  }

  /**
   * A test resource used for testing the DataMap-centric rest engines. (Uses Play-JSON adapters)
   *
   * Note: because we're not using the routing components of Naptime, we can get away with multiple
   * get's / etc.
   *
   * In general, it is a very bad idea to have multiple gets, creates, etc, in a single resource.
   */
  class CourierTestResource(implicit val executionContext: ExecutionContext, val materializer: Materializer)
    extends TopLevelCollectionResource[String, Course] {
    import RestActionCategoryEngine2._

    override def keyFormat: KeyFormat[String] = KeyFormat.stringKeyFormat
    override def resourceName: String = "testResource"
    override implicit val resourceFormat: OFormat[Course] =
      CourierFormats.recordTemplateFormats[Course]
    implicit val fields = Fields.withDefaultFields("name").withRelated(
      "relatedCaseClass" -> RelatedResources.CaseClass.relatedName,
      "relatedCourier" -> RelatedResources.Courier.relatedName)

    def mk(id: String): Course = Course(s"$id name", s"$id description")

    def get1(id: String) = Nap.get { ctx =>
      RelatedResources.addRelated {
        Ok(Keyed(id, mk(id)))
      }
    }

    def get2(id: String) = Nap.get { ctx =>
      throw Errors.NotFound(errorCode = "id", msg = s"Bad id: $id")
    }

    def multiGet(ids: Set[String]) = Nap.multiGet { ctx =>
      RelatedResources.addRelated {
        Ok(ids.map(id => Keyed(id, mk(id))).toSeq)
      }
    }

    def create1 = Nap.create { ctx =>
      Ok(Keyed("1", Some(mk("1"))))
    }

    def create2 = Nap.create { ctx =>
      Ok(Keyed("1", None))
    }

    def create3 = Nap.catching {
      case e: RuntimeException =>
        RestError(NaptimeActionException(Status.BAD_REQUEST, Some("boom"), None))
    }.create { ctx =>
      throw new RuntimeException("boooooooom")
    }

    def delete1(id: String) = Nap.delete {
      Ok(())
    }
  }

  class courierKeyedTestResource(implicit val executionContext: ExecutionContext, val materializer: Materializer)
    extends TopLevelCollectionResource[EnrollmentId, Course] {
    import RestActionCategoryEngine2._

    override def resourceName: String = "testResource"
    implicit val sessionIdStringKeyFormat = CourierFormats.recordTemplateStringKeyFormat[EnrollmentId]
    override implicit def keyFormat =
      KeyFormat.idAsStringWithFields(CourierFormats.recordTemplateFormats[EnrollmentId])
    override implicit def resourceFormat: OFormat[Course] = CourierFormats.recordTemplateFormats[Course]
    implicit val fields = Fields.withDefaultFields("name").withRelated(
      "relatedCaseClass" -> RelatedResources.CaseClass.relatedName,
      "relatedCourier" -> RelatedResources.Courier.relatedName)

    def mk(id: EnrollmentId): Course = Course(s"${StringKey.toStringKey(id).key} name", s"$id description")

    object EnrollmentIds {
      val a = EnrollmentId(userId = 1225, courseId = SessionId(courseId = "abc", iterationId = 2))
      val b = EnrollmentId(userId = 2, courseId = SessionId(courseId = "xyz", iterationId = 8))
    }

    def get1(id: EnrollmentId) = Nap.get { ctx =>
      RelatedResources.addRelated {
        Ok(Keyed(id, mk(id)))
      }
    }

    def get2(id: EnrollmentId) = Nap.get { ctx =>
      throw Errors.NotFound(errorCode = "id", msg = s"Bad id: $id")
    }

    def multiGet(ids: Set[EnrollmentId]) = Nap.multiGet { ctx =>
      RelatedResources.addRelated {
        Ok(ids.map(id => Keyed(id, mk(id))).toSeq)
      }
    }

    def create1 = Nap.create { ctx =>
      Ok(Keyed(EnrollmentIds.a, Some(mk(EnrollmentIds.a))))
    }

    def create2 = Nap.create { ctx =>
      Ok(Keyed(EnrollmentIds.b, None))
    }

    def create3 = Nap.catching {
      case e: RuntimeException =>
        RestError(NaptimeActionException(Status.BAD_REQUEST, Some("boom"), None))
    }.create { ctx =>
      throw new RuntimeException("boooooooom")
    }

    def delete1(id: EnrollmentId) = Nap.delete {
      Ok(())
    }
  }

  object RelatedResources extends AssertionsForJUnit {
    object CaseClass {
      val relatedName = ResourceName("relatedCaseClass", 2)
      implicit val fields = ResourceFields[Person]

      val related = Seq(
        Keyed(1, Person("related1", "1@related.com"))
      )

      def addRelated[T](ok: Ok[T]): Ok[T] = {
        ok.withRelated(relatedName, related)
      }
    }

    object Courier {
      val relatedName = ResourceName("relatedCourier", 3)
      implicit val format = CourierFormats.recordTemplateFormats[Course]
      implicit val fields = ResourceFields[Course]

      val related = Seq(
        Keyed(1, Course("relatedCourse1", "All about the first related course!"))
      )

      def addRelated[T](ok: Ok[T]): Ok[T] = {
        ok.withRelated(relatedName, related)
      }
    }

    def addRelated[T](ok: Ok[T]): Ok[T] = {
      val withCaseClass = CaseClass.addRelated(ok)
      val withCourier = Courier.addRelated(withCaseClass)
      withCourier
    }

    private[this] def checkBasicResponseForRelated(response: Result): (JsObject, JsObject) = {
      val bodyContent = Helpers.contentAsJson(Future.successful(response))
      assert(bodyContent.isInstanceOf[JsObject])
      val json = bodyContent.asInstanceOf[JsObject]
      assert(json.value.contains("elements"))
      assert(json.value.contains("linked"))
      assert((json \ "linked").toOption.isDefined, s"Linked: ${json \ "linked"}")
      assert((json \ "linked").validate[JsObject].asOpt.isDefined,
        s"Got ${(json \ "linked").validate[JsObject]}. Json: $json")
      val linked = (json \ "linked").validate[JsObject].get
      (linked, json)
    }

    def assertRelatedPresent(response: Result): Unit = {
      val (linked, json) = checkBasicResponseForRelated(response)
      assert(linked.value.size === 2, s"Response: $json")
      val expected = Json.obj(
        CaseClass.relatedName.identifier -> Json.arr(
          Json.obj(
            "id" -> 1,
            "name" -> "related1")),
        Courier.relatedName.identifier -> Json.arr(
          Json.obj(
            "id" -> 1,
            "name" -> "relatedCourse1")))
      assert(expected === linked, s"Linked was not what we expected. Got $linked")
    }

    def assertRelatedAbsent(response: Result): Unit = {
      val (linked, json) = checkBasicResponseForRelated(response)
      assert(linked.value.size === 0, s"Response: $json")
    }
  }
}

class RestActionCategoryEngine2Test extends AssertionsForJUnit with ScalaFutures with ResourceTestImplicits {
  import RestActionCategoryEngine2Test._
  // Increase timeout a bit.
  override def spanScaleFactor: Double = 10
  val playJsonTestResource = new PlayJsonTestResource
  val courierTestResource = new CourierTestResource
  val courierKeyedTestResource = new courierKeyedTestResource

  @Test
  def playJsonGet1(): Unit = {
    val response = testEmptyRequestBody(playJsonTestResource.get1(1))
    RelatedResources.assertRelatedPresent(response)
    val elements = assertElements(response)
    val expected = Json.arr(
      Json.obj(
        "id" -> 1,
        "name" -> "1"))
    assert(expected === elements)
  }

  @Test
  def playJsonGet1NoRelated(): Unit = {
    val response = testEmptyRequestBody(playJsonTestResource.get1(1), FakeRequest())
    RelatedResources.assertRelatedAbsent(response)
    val elements = assertElements(response)
    val expected = Json.arr(
      Json.obj(
        "id" -> 1,
        "name" -> "1"))
    assert(expected === elements)
  }

  @Test
  def playJsonGet1Etags(): Unit = {
    val response1 = testEmptyRequestBody(playJsonTestResource.get1(1))
    val responseNoRelated = testEmptyRequestBody(playJsonTestResource.get1(1), FakeRequest())
    val response2 = testEmptyRequestBody(playJsonTestResource.get1(1))

    assert(response1.header.status === Status.OK)
    assert(response1.header.headers.contains(HeaderNames.ETAG))
    assert(responseNoRelated.header.status === Status.OK)
    assert(responseNoRelated.header.headers.contains(HeaderNames.ETAG))
    assert(response2.header.status === Status.OK)
    assert(response2.header.headers.contains(HeaderNames.ETAG))
    assert(response1.header.headers.get(HeaderNames.ETAG) != responseNoRelated.header.headers.get(HeaderNames.ETAG))
    assert(response1.header.headers.get(HeaderNames.ETAG) === response2.header.headers.get(HeaderNames.ETAG))
    // Check for stability in ETag computation.
    assert(Some("W/\"-981723117\"") === response1.header.headers.get(HeaderNames.ETAG))
  }

  @Test
  def playJsonGet1IfNoneMatch(): Unit = {
    val response1 = testEmptyRequestBody(playJsonTestResource.get1(4))
    assert(response1.header.headers.contains(HeaderNames.ETAG))
    val etag = response1.header.headers(HeaderNames.ETAG)
    val request2 = standardFakeRequest.withHeaders(HeaderNames.IF_NONE_MATCH -> etag)
    val response2 = testEmptyRequestBody(playJsonTestResource.get1(4), request2)
    assert(response2.header.status === Status.NOT_MODIFIED)
  }

  @Test
  def playJsonGet2(): Unit = {
    testEmptyRequestBody(playJsonTestResource.get2(1))
  }

  @Test
  def playJsonMultiGet(): Unit = {
    val response = testEmptyRequestBody(playJsonTestResource.multiGet(Set(1, 2)))
    RelatedResources.assertRelatedPresent(response)
  }

  @Test
  def playJsonMultiGetNoRelated(): Unit = {
    val response = testEmptyRequestBody(playJsonTestResource.multiGet(Set(1, 2)), FakeRequest())
    RelatedResources.assertRelatedAbsent(response)
  }

  @Test
  def playJsonCreate1(): Unit = {
    testEmptyRequestBody(playJsonTestResource.create1)
  }

  @Test
  def playJsonCreate2(): Unit = {
    testEmptyRequestBody(playJsonTestResource.create2)
  }

  @Test
  def playJsonCreate3(): Unit = {
    testEmptyRequestBody(playJsonTestResource.create3)
  }

  @Test
  def playJsonDelete1(): Unit = {
    testEmptyRequestBody(playJsonTestResource.delete1(1))
  }

  @Test
  def courierGet1(): Unit = {
    val response = testEmptyRequestBody(courierTestResource.get1("test"))
    RelatedResources.assertRelatedPresent(response)
    val elements = assertElements(response)
    val expected = Json.arr(
      Json.obj(
        "id" -> "test",
        "name" -> "test name"))
    assert(expected === elements)
  }

  @Test
  def courierGet1NoRelated(): Unit = {
    val response = testEmptyRequestBody(courierTestResource.get1("test"), FakeRequest())
    RelatedResources.assertRelatedAbsent(response)
    val elements = assertElements(response)
    val expected = Json.arr(
      Json.obj(
        "id" -> "test",
        "name" -> "test name"))
    assert(expected === elements)
  }

  @Test
  def courierGet1Etags(): Unit = {
    val response1 = testEmptyRequestBody(courierTestResource.get1("test"))
    val response2 = testEmptyRequestBody(courierTestResource.get1("test"))
    val responseNoRelated = testEmptyRequestBody(courierTestResource.get1("test"), FakeRequest())

    assert(response1.header.status === Status.OK)
    assert(response2.header.status === Status.OK)
    assert(responseNoRelated.header.status === Status.OK)
    assert(response1.header.headers.contains(HeaderNames.ETAG))
    assert(response2.header.headers.contains(HeaderNames.ETAG))
    assert(responseNoRelated.header.headers.contains(HeaderNames.ETAG))
    assert(response1.header.headers.get(HeaderNames.ETAG) != responseNoRelated.header.headers.get(HeaderNames.ETAG))
    assert(response1.header.headers.get(HeaderNames.ETAG) === response2.header.headers.get(HeaderNames.ETAG))
    // Check for stability in ETag computation.
    assert(Some("W/\"1468630371\"") === response1.header.headers.get(HeaderNames.ETAG))
  }

  @Test
  def courierGet1IfNoneMatch(): Unit = {
    val response1 = testEmptyRequestBody(courierTestResource.get1("etagTest"))
    assert(response1.header.headers.contains(HeaderNames.ETAG))
    val etag = response1.header.headers(HeaderNames.ETAG)
    val request2 = standardFakeRequest.withHeaders(HeaderNames.IF_NONE_MATCH -> etag)
    val response2 = testEmptyRequestBody(courierTestResource.get1("etagTest"), request2)
    assert(response2.header.status === Status.NOT_MODIFIED)
  }

  @Test
  def courierGet2(): Unit = {
    testEmptyRequestBody(courierTestResource.get2("test"))
  }

  @Test
  def courierMultiGet(): Unit = {
    val response = testEmptyRequestBody(courierTestResource.multiGet(Set("test1", "test2")))
    RelatedResources.assertRelatedPresent(response)
  }

  @Test
  def courierMultiGetNoRelated(): Unit = {
    val response = testEmptyRequestBody(courierTestResource.multiGet(Set("test1", "test2")), FakeRequest())
    RelatedResources.assertRelatedAbsent(response)
  }

  @Test
  def courierCreate1(): Unit = {
    testEmptyRequestBody(courierTestResource.create1)
  }

  @Test
  def courierCreate2(): Unit = {
    testEmptyRequestBody(courierTestResource.create2)
  }

  @Test
  def courierCreate3(): Unit = {
    testEmptyRequestBody(courierTestResource.create3)
  }

  @Test
  def courierDelete1(): Unit = {
    testEmptyRequestBody(courierTestResource.delete1("test"))
  }


  @Test
  def courierKeyedGet1(): Unit = {
    val response = testEmptyRequestBody(courierKeyedTestResource.get1(courierKeyedTestResource.EnrollmentIds.a))
    RelatedResources.assertRelatedPresent(response)
    val elements = assertElements(response)
    val expected = Json.arr(
      Json.obj(
        "id" -> "1225~abc!~2",
        "courseId" -> Json.obj(
          "iterationId" -> 2,
          "courseId" -> "abc"),
        "userId" -> 1225,
        "name" -> "1225~abc!~2 name"))
    assert(expected === elements)
  }

  @Test
  def courierKeyedGet1NoRelated(): Unit = {
    val response = testEmptyRequestBody(courierKeyedTestResource.get1(courierKeyedTestResource.EnrollmentIds.a),
      FakeRequest())
    RelatedResources.assertRelatedAbsent(response)
    val elements = assertElements(response)
    val expected = Json.arr(
      Json.obj(
        "id" -> "1225~abc!~2",
        "courseId" -> Json.obj(
          "iterationId" -> 2,
          "courseId" -> "abc"),
        "userId" -> 1225,
        "name" -> "1225~abc!~2 name"))
    assert(expected === elements)
  }

  @Test
  def courierKeyedGet2(): Unit = {
    testEmptyRequestBody(courierKeyedTestResource.get2(courierKeyedTestResource.EnrollmentIds.a))
  }

  @Test
  def courierKeyedMultiGet(): Unit = {
    val response = testEmptyRequestBody(courierKeyedTestResource.multiGet(Set(
      courierKeyedTestResource.EnrollmentIds.a, courierKeyedTestResource.EnrollmentIds.b)))
    RelatedResources.assertRelatedPresent(response)
  }

  @Test
  def courierKeyedMultiGetNoRelated(): Unit = {
    val response = testEmptyRequestBody(courierKeyedTestResource.multiGet(Set(
      courierKeyedTestResource.EnrollmentIds.a, courierKeyedTestResource.EnrollmentIds.b)), FakeRequest())
    RelatedResources.assertRelatedAbsent(response)
  }

  @Test
  def courierKeyedCreate1(): Unit = {
    testEmptyRequestBody(courierKeyedTestResource.create1)
  }

  @Test
  def courierKeyedCreate2(): Unit = {
    testEmptyRequestBody(courierKeyedTestResource.create2)
  }

  @Test
  def courierKeyedCreate3(): Unit = {
    testEmptyRequestBody(courierKeyedTestResource.create3)
  }

  @Test
  def courierKeyedDelete1(): Unit = {
    testEmptyRequestBody(courierKeyedTestResource.delete1(courierKeyedTestResource.EnrollmentIds.b))
  }

  @Test
  def serializeCollectionCourierModelsTest(): Unit = {
    def mkModel(id: String): ExpandedCourse = {
      ExpandedCourse(
        name = id,
        description = s"$id description",
        platform = CoursePlatform.NewPlatform,
        domains = List(Domain(DomainId(Slug("my-domain")))),
        courseQnAs = List(CourseQnA(
          question = "How hard?", answer = CmlContentType(dtdId = "myDtd", value = "Very!"))),
        instructorIds = List(1L, 2L))
    }
    val fields = QueryFields(Set("name", "description", "domains"), Map.empty)
    val model1 = mkModel("test-course-1")

    assert(model1.data().isMadeReadOnly)
    assert(model1.domains.data().isMadeReadOnly)
    assert(model1.domains.head.data.isMadeReadOnly)

    RestActionCategoryEngine2.serializeCollection(
      new DataList(),
      List(Keyed("test-course-id", model1)),
      KeyFormat.stringKeyFormat,
      NaptimeSerializer.courierModels,
      fields,
      ResourceFields(CourierFormats.recordTemplateFormats[ExpandedCourse]))

    val model2 = mkModel("test-course-2").copy(model1.data(), DataConversion.SetReadOnly)

    RestActionCategoryEngine2.serializeCollection(
      new DataList(),
      List(Keyed("test-course-id2", model2)),
      KeyFormat.stringKeyFormat,
      NaptimeSerializer.courierModels,
      fields,
      ResourceFields(CourierFormats.recordTemplateFormats[ExpandedCourse]))
  }

  @Test
  def multiHopRelatedIncludes(): Unit = {
    val partnersResourceName = ResourceName("partners", 1)
    val instructorsResourceName = ResourceName("instructors", 1)

    implicit val courseFormat = CourierFormats.recordTemplateFormats[ExpandedCourse]
    implicit val instructorFormats = CourierFormats.recordTemplateFormats[Instructor]
    implicit val partnerFormats = CourierFormats.recordTemplateFormats[Partner]

    implicit val coursesFields = ResourceFields[ExpandedCourse].withRelated("instructorIds" -> instructorsResourceName)
    implicit val instructorFields = ResourceFields[Instructor].withRelated("partner" -> partnersResourceName)
    implicit val partnerFields = ResourceFields[Partner]

    val queryFields = QueryFields(Set("name", "description", "instructorIds"),
      Map(instructorsResourceName -> Set("name"), partnersResourceName -> Set("name", "slug")))
    val queryIncludes = QueryIncludes(Set("instructorIds"), Map(instructorsResourceName -> Set("partner")))

    val course = Keyed("my-course-id", ExpandedCourse(
      name = "my best course",
      description = "My favorite course",
      platform = CoursePlatform.NewPlatform,
      domains = List.empty,
      courseQnAs = List.empty,
      instructorIds = List(3L)))
    val instructor = Keyed(3L, Instructor(
      name = "Prof Example",
      bio = None,
      partner = "uuid-abc_123"))
    val partner = Keyed("uuid-abc_123", Partner(
      name = "School of awesome",
      slug = Slug("school-of-awesome")))

    val response = Ok(course)
      .withRelated(instructorsResourceName, List(instructor))
      .withRelated(partnersResourceName, List(partner))

    val engine = RestActionCategoryEngine2.getActionCategoryEngine[String, ExpandedCourse]
    val wireResponse = engine.mkResult(
      request = FakeRequest(),
      resourceFields = coursesFields,
      requestFields = queryFields,
      requestIncludes = queryIncludes,
      pagination = RequestPagination(limit = 10, start = None, isDefault = true),
      response = response)

    val content: JsValue = Helpers.contentAsJson(Future.successful(wireResponse))
    val expected = Json.obj(
      "elements" -> Json.arr(
        Json.obj(
          "id" -> "my-course-id",
          "name" -> "my best course",
          "description" -> "My favorite course",
          "instructorIds" -> Json.arr(3L))),
      "paging" -> Json.obj(),
      "linked" -> Json.obj(
        "partners.v1" -> Json.arr(
          Json.obj(
            "id" -> "uuid-abc_123",
            "name" -> "School of awesome",
            "slug" -> "school-of-awesome")),
        "instructors.v1" -> Json.arr(
          Json.obj(
            "id" -> 3L,
            "name" -> "Prof Example"))))
    assert(expected === content)

    val wireResponse2 = engine.mkResult(
      request = FakeRequest(),
      resourceFields = coursesFields,
      requestFields = queryFields,
      requestIncludes = queryIncludes.copy(resources = Map.empty),
      pagination = RequestPagination(limit = 10, start = None, isDefault = true),
      response = response)
    val content2: JsValue = Helpers.contentAsJson(Future.successful(wireResponse2))
    val expected2 = Json.obj(
      "elements" -> Json.arr(
        Json.obj(
          "id" -> "my-course-id",
          "name" -> "my best course",
          "description" -> "My favorite course",
          "instructorIds" -> Json.arr(3L))),
      "paging" -> Json.obj(),
      "linked" -> Json.obj(
        "instructors.v1" -> Json.arr(
          Json.obj(
            "id" -> 3L,
            "name" -> "Prof Example"))))
    assert(expected2 === content2)

    val wireResponse3 = engine.mkResult(
      request = FakeRequest(),
      resourceFields = coursesFields,
      requestFields = queryFields,
      requestIncludes = QueryIncludes(Set.empty, Map.empty),
      pagination = RequestPagination(limit = 10, start = None, isDefault = true),
      response = response)
    val content3: JsValue = Helpers.contentAsJson(Future.successful(wireResponse3))
    val expected3 = Json.obj(
      "elements" -> Json.arr(
        Json.obj(
          "id" -> "my-course-id",
          "name" -> "my best course",
          "description" -> "My favorite course",
          "instructorIds" -> Json.arr(3L))),
      "paging" -> Json.obj(),
      "linked" -> Json.obj())
    assert(expected3 === content3)

    val wireResponse4 = engine.mkResult(
      request = FakeRequest(),
      resourceFields = coursesFields,
      requestFields = queryFields,
      requestIncludes = queryIncludes.copy(fields = queryIncludes.fields + "_links"),
      pagination = RequestPagination(limit = 10, start = None, isDefault = true),
      response = response)
    val content4: JsValue = Helpers.contentAsJson(Future.successful(wireResponse4))
    val expected4 = Json.obj(
      "elements" -> Json.arr(
        Json.obj(
          "id" -> "my-course-id",
          "name" -> "my best course",
          "description" -> "My favorite course",
          "instructorIds" -> Json.arr(3L))),
      "paging" -> Json.obj(),
      "linked" -> Json.obj(
        "partners.v1" -> Json.arr(
          Json.obj(
            "id" -> "uuid-abc_123",
            "name" -> "School of awesome",
            "slug" -> "school-of-awesome")),
        "instructors.v1" -> Json.arr(
          Json.obj(
            "id" -> 3L,
            "name" -> "Prof Example"))),
      "links" -> Json.obj(
        "elements" -> Json.obj(
          "instructorIds" -> "instructors.v1"),
        "instructors.v1" -> Json.obj(
          "partner" -> "partners.v1"),
        "partners.v1" -> Json.obj()))
    assert(expected4 === content4)
  }

  @Test
  def serializeFacetsCorrectly(): Unit = {
    implicit val courseFormat = CourierFormats.recordTemplateFormats[Course]
    implicit val coursesFields = ResourceFields[Course]

    val engine = RestActionCategoryEngine2.finderActionCategoryEngine[String, Course]

    val response = Ok(List(
      Keyed("abc", Course("course 101", "101 course description")),
      Keyed("zyx", Course("course 999", "999 course description")))).withPagination(
        next = None,
        total = Some(2L),
        facets = Some(Map(
          "languages" -> FacetField(
            facetEntries = List(
              FacetFieldValue("en", Some("English"), 2),
              FacetFieldValue("fr", Some("French"), 0)),
            fieldCardinality = Some(23)))))

    val wireResponse = engine.mkResult(
      request = FakeRequest(),
      resourceFields = coursesFields,
      requestFields = QueryFields(Set("name"), Map.empty),
      requestIncludes = QueryIncludes.empty,
      pagination = RequestPagination(limit = 10, start = None, isDefault = true),
      response = response)
    val content: JsValue = Helpers.contentAsJson(Future.successful(wireResponse))
    val expected = Json.obj(
      "elements" -> Json.arr(
        Json.obj(
          "id" -> "abc",
          "name" -> "course 101"),
        Json.obj(
          "id" -> "zyx",
          "name" -> "course 999")),
      "paging" -> Json.obj(
        "total" -> 2,
        "facets" -> Json.obj(
          "languages" -> Json.obj(
            "facetEntries" -> Json.arr(
              Json.obj(
                "id" -> "en",
                "name" -> "English",
                "count" -> 2),
              Json.obj(
                "id" -> "fr",
                "name" -> "French",
                "count" -> 0)),
          "fieldCardinality" -> 23))),
      "linked" -> Json.obj())
    assert(expected === content)
  }


  // Test helpers here and below.

  private[this] val fieldsQueryParam = s"${RelatedResources.CaseClass.relatedName.identifier}(name)," +
    s"${RelatedResources.Courier.relatedName.identifier}(name)"
  private[this] val standardFakeRequest =
    FakeRequest("GET", s"/?includes=relatedCaseClass,relatedCourier&fields=$fieldsQueryParam")
  private[this] def testEmptyRequestBody(
      actionToTest: RestAction[_, _, AnyContent, _, _, _],
      request: FakeRequest[AnyContentAsEmpty.type] = standardFakeRequest,
      strictMode: Boolean = false): Result = {
    val result = runTestRequest(actionToTest, request)
    Validators.assertValidResponse(result, strictMode = strictMode)
    result
  }

  private[this] def runTestRequestInternal[BodyType](
      restAction: RestAction[_, _, BodyType, _, _, _],
      request: RequestHeader,
      body: HttpEntity = HttpEntity.NoEntity): Result = {
    val accumulator = restAction.apply(request)
    val resultFut = accumulator.run()
    resultFut.futureValue
  }

  private[this] def runTestRequest[BodyType](restAction: RestAction[_, _, BodyType, _, _, _],
    fakeRequest: FakeRequest[BodyType])(
    implicit writeable: Writeable[BodyType]): Result = {
    val b = writeable.toEntity(fakeRequest.body)
    runTestRequestInternal(restAction, fakeRequest, b)
  }

  private[this] def runTestRequest(restAction: RestAction[_, _, AnyContent, _, _, _],
    fakeRequest: FakeRequest[AnyContentAsEmpty.type]): Result = {
    runTestRequestInternal(restAction, fakeRequest)
  }

  private[this] def assertElements(response: Result): JsArray = {
    val bodyContent = Helpers.contentAsJson(Future.successful(response))
    assert(bodyContent.isInstanceOf[JsObject])
    val json = bodyContent.asInstanceOf[JsObject]
    val elements = (json \ "elements").validate[JsArray]
    assert(elements.isSuccess, s"Elements was not a JsArray: $elements. $bodyContent")
    elements.get
  }

}
