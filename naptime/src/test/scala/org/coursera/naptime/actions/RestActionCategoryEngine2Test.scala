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

import com.linkedin.data.DataList
import org.coursera.common.stringkey.StringKey
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.courier.CourierFormats
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.RestError
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.Errors
import org.coursera.naptime.Fields
import org.coursera.naptime.Ok
import org.coursera.naptime.QueryFields
import org.coursera.naptime.QueryIncludes
import org.coursera.naptime.RequestFields
import org.coursera.naptime.RequestPagination
import org.coursera.naptime.ResourceName
import org.coursera.naptime.actions.util.Validators
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import play.api.http.HeaderNames
import play.api.http.Status
import play.api.http.Writeable
import play.api.libs.iteratee.Enumerator
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

import scala.concurrent.Future
import scala.concurrent.duration._

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
  object PlayJsonTestResource
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
      Errors.NotFound(errorCode = "id", msg = s"Bad id $id")
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
  object CourierTestResource
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
      Errors.NotFound(errorCode = "id", msg = s"Bad id: $id")
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

  object CourierKeyedTestResource
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

    def mk(id: EnrollmentId): Course = Course(s"${StringKey(id).key} name", s"$id description")

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
      Errors.NotFound(errorCode = "id", msg = s"Bad id: $id")
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
      implicit val fields = Fields[Person]

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
      implicit val fields = Fields[Course]

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

class RestActionCategoryEngine2Test extends AssertionsForJUnit with ScalaFutures {
  import RestActionCategoryEngine2Test._

  // Increase timeout a bit.
  override def spanScaleFactor: Double = 10

  @Test
  def playJsonGet1(): Unit = {
    val response = testEmptyRequestBody(PlayJsonTestResource.get1(1))
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
    val response = testEmptyRequestBody(PlayJsonTestResource.get1(1), FakeRequest())
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
    val response1 = testEmptyRequestBody(PlayJsonTestResource.get1(1))
    val responseNoRelated = testEmptyRequestBody(PlayJsonTestResource.get1(1), FakeRequest())
    val response2 = testEmptyRequestBody(PlayJsonTestResource.get1(1))

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
  def playJsonGet2(): Unit = {
    testEmptyRequestBody(PlayJsonTestResource.get2(1))
  }

  @Test
  def playJsonMultiGet(): Unit = {
    val response = testEmptyRequestBody(PlayJsonTestResource.multiGet(Set(1, 2)))
    RelatedResources.assertRelatedPresent(response)
  }

  @Test
  def playJsonMultiGetNoRelated(): Unit = {
    val response = testEmptyRequestBody(PlayJsonTestResource.multiGet(Set(1, 2)), FakeRequest())
    RelatedResources.assertRelatedAbsent(response)
  }

  @Test
  def playJsonCreate1(): Unit = {
    testEmptyRequestBody(PlayJsonTestResource.create1)
  }

  @Test
  def playJsonCreate2(): Unit = {
    testEmptyRequestBody(PlayJsonTestResource.create2)
  }

  @Test
  def playJsonCreate3(): Unit = {
    testEmptyRequestBody(PlayJsonTestResource.create3)
  }

  @Test
  def playJsonDelete1(): Unit = {
    testEmptyRequestBody(PlayJsonTestResource.delete1(1))
  }

  @Test
  def courierGet1(): Unit = {
    val response = testEmptyRequestBody(CourierTestResource.get1("test"))
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
    val response = testEmptyRequestBody(CourierTestResource.get1("test"), FakeRequest())
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
    val response1 = testEmptyRequestBody(CourierTestResource.get1("test"))
    val response2 = testEmptyRequestBody(CourierTestResource.get1("test"))
    val responseNoRelated = testEmptyRequestBody(CourierTestResource.get1("test"), FakeRequest())

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
  def courierGet2(): Unit = {
    testEmptyRequestBody(CourierTestResource.get2("test"))
  }

  @Test
  def courierMultiGet(): Unit = {
    val response = testEmptyRequestBody(CourierTestResource.multiGet(Set("test1", "test2")))
    RelatedResources.assertRelatedPresent(response)
  }

  @Test
  def courierMultiGetNoRelated(): Unit = {
    val response = testEmptyRequestBody(CourierTestResource.multiGet(Set("test1", "test2")), FakeRequest())
    RelatedResources.assertRelatedAbsent(response)
  }

  @Test
  def courierCreate1(): Unit = {
    testEmptyRequestBody(CourierTestResource.create1)
  }

  @Test
  def courierCreate2(): Unit = {
    testEmptyRequestBody(CourierTestResource.create2)
  }

  @Test
  def courierCreate3(): Unit = {
    testEmptyRequestBody(CourierTestResource.create3)
  }

  @Test
  def courierDelete1(): Unit = {
    testEmptyRequestBody(CourierTestResource.delete1("test"))
  }


  @Test
  def courierKeyedGet1(): Unit = {
    val response = testEmptyRequestBody(CourierKeyedTestResource.get1(CourierKeyedTestResource.EnrollmentIds.a))
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
    val response = testEmptyRequestBody(CourierKeyedTestResource.get1(CourierKeyedTestResource.EnrollmentIds.a),
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
    testEmptyRequestBody(CourierKeyedTestResource.get2(CourierKeyedTestResource.EnrollmentIds.a))
  }

  @Test
  def courierKeyedMultiGet(): Unit = {
    val response = testEmptyRequestBody(CourierKeyedTestResource.multiGet(Set(
      CourierKeyedTestResource.EnrollmentIds.a, CourierKeyedTestResource.EnrollmentIds.b)))
    RelatedResources.assertRelatedPresent(response)
  }

  @Test
  def courierKeyedMultiGetNoRelated(): Unit = {
    val response = testEmptyRequestBody(CourierKeyedTestResource.multiGet(Set(
      CourierKeyedTestResource.EnrollmentIds.a, CourierKeyedTestResource.EnrollmentIds.b)), FakeRequest())
    RelatedResources.assertRelatedAbsent(response)
  }

  @Test
  def courierKeyedCreate1(): Unit = {
    testEmptyRequestBody(CourierKeyedTestResource.create1)
  }

  @Test
  def courierKeyedCreate2(): Unit = {
    testEmptyRequestBody(CourierKeyedTestResource.create2)
  }

  @Test
  def courierKeyedCreate3(): Unit = {
    testEmptyRequestBody(CourierKeyedTestResource.create3)
  }

  @Test
  def courierKeyedDelete1(): Unit = {
    testEmptyRequestBody(CourierKeyedTestResource.delete1(CourierKeyedTestResource.EnrollmentIds.b))
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
      Fields(CourierFormats.recordTemplateFormats[ExpandedCourse]))

    val model2 = mkModel("test-course-2").copy(model1.data(), DataConversion.SetReadOnly)

    RestActionCategoryEngine2.serializeCollection(
      new DataList(),
      List(Keyed("test-course-id2", model2)),
      KeyFormat.stringKeyFormat,
      NaptimeSerializer.courierModels,
      fields,
      Fields(CourierFormats.recordTemplateFormats[ExpandedCourse]))
  }

  @Test
  def multiHopRelatedIncludes(): Unit = {
    val partnersResourceName = ResourceName("partners", 1)
    val instructorsResourceName = ResourceName("instructors", 1)

    implicit val courseFormat = CourierFormats.recordTemplateFormats[ExpandedCourse]
    implicit val instructorFormats = CourierFormats.recordTemplateFormats[Instructor]
    implicit val partnerFormats = CourierFormats.recordTemplateFormats[Partner]

    implicit val coursesFields = Fields[ExpandedCourse].withRelated("instructorIds" -> instructorsResourceName)
    implicit val instructorFields = Fields[Instructor].withRelated("partner" -> partnersResourceName)
    implicit val partnerFields = Fields[Partner]

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
    val wireResponse = engine.mkResponse(
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

    val wireResponse2 = engine.mkResponse(
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

    val wireResponse3 = engine.mkResponse(
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
  }


  // Test helpers here and below.

  private[this] val fieldsQueryParam = s"${RelatedResources.CaseClass.relatedName.identifier}(name)," +
    s"${RelatedResources.Courier.relatedName.identifier}(name)"
  private[this] def testEmptyRequestBody(
      actionToTest: RestAction[_, _, AnyContent, _, _, _],
      request: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest("GET", s"/?includes=relatedCaseClass,relatedCourier&fields=$fieldsQueryParam"),
      strictMode: Boolean = false): Result = {
    val result = runTestRequest(actionToTest, request)
    Validators.assertValidResponse(result, strictMode = strictMode)
    result
  }

  private[this] def runTestRequestInternal[BodyType](
      restAction: RestAction[_, _, BodyType, _, _, _],
      request: RequestHeader,
      body: Enumerator[Array[Byte]] = Enumerator.empty): Result = {
    val iteratee = restAction.apply(request)
    val resultFut = body.run(iteratee)
    resultFut.futureValue
  }

  private[this] def runTestRequest[BodyType](restAction: RestAction[_, _, BodyType, _, _, _],
    fakeRequest: FakeRequest[BodyType])(
    implicit writeable: Writeable[BodyType]): Result = {
    val requestWithHeader = writeable.contentType.map { ct =>
      fakeRequest.withHeaders(HeaderNames.CONTENT_TYPE -> ct)
    }.getOrElse(fakeRequest)
    val b = Enumerator(fakeRequest.body).through(writeable.toEnumeratee)
    runTestRequestInternal(restAction, requestWithHeader, b)
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
