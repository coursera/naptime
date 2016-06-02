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

import org.coursera.naptime.courier.CourierFormats
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.RestError
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.Errors
import org.coursera.naptime.Ok
import org.coursera.naptime.access.HeaderAccessControl
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.coursera.naptime.util.Validators
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.http.HeaderNames
import play.api.http.Status
import play.api.http.Writeable
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.mvc.AnyContent
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.BodyParsers
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.test.FakeRequest

import scala.concurrent.Await
import scala.concurrent.duration._

object RestActionCategoryEngine2Test {

  case class Person(name: String, email: String)

  /**
   * A test resource used for testing the DataMap-centric rest engines. (Uses Play-JSON adapters)
   *
   * Note: because we're not using the routing components of Naptime, we can get away with multiple
   * get's / etc.
   *
   * In general, it is a very bad idea to have multiple gets, creates, etc, in a single resource.
   */
  object PlayJsonTestResource
    extends TopLevelCollectionResource[String, Person] {
    override def keyFormat: KeyFormat[String] = KeyFormat.stringKeyFormat
    override def resourceName: String = "testResource"
    override implicit val resourceFormat: OFormat[Person] = Json.format[Person]
    implicit val fields = Fields.withDefaultFields("name")

    def get1(id: String) = Nap.get { ctx =>
      Ok(Keyed(id, Person(id, s"$id@coursera.org")))
    }

    def get2(id: String) = Nap.get { ctx =>
      Errors.NotFound(errorCode = "id", msg = s"Bad id $id")
    }

    def create1 = Nap.create { ctx =>
      Ok(Keyed("newId", Some(Person("newId", "newId@coursera.org"))))
    }

    def create2 = Nap.create { ctx =>
      Ok(Keyed("newId2", None))
    }

    def create3 = Nap.catching {
      case e: RuntimeException => RestError(NaptimeActionException(Status.BAD_REQUEST, Some("boom"), None))
    }.create { ctx =>
      throw new RuntimeException("boooooooom")
    }

    def delete1(id: String) = Nap.delete {
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
    override def keyFormat: KeyFormat[String] = KeyFormat.stringKeyFormat
    override def resourceName: String = "testResource"
    override implicit val resourceFormat: OFormat[Course] =
      CourierFormats.recordTemplateFormats[Course]
    implicit val fields = Fields.withDefaultFields("name")

    def mk(id: String): Course = Course(s"$id name", s"$id description")

    def get1(id: String) = Nap.get { ctx =>
      Ok(Keyed(id, mk(id)))
    }

    def get2(id: String) = Nap.get { ctx =>
      Errors.NotFound(errorCode = "id", msg = s"Bad id: $id")
    }

    def create1 = Nap.create { ctx =>
      Ok(Keyed("1", Some(mk("1"))))
    }

    def create2 = Nap.create { ctx =>
      Ok(Keyed("1", None))
    }

    def create3 = Nap.catching {
      case e: RuntimeException => RestError(NaptimeActionException(Status.BAD_REQUEST, Some("boom"), None))
    }.create { ctx =>
      throw new RuntimeException("boooooooom")
    }

    def delete1(id: String) = Nap.delete {
      Ok(())
    }
  }
}

class RestActionCategoryEngine2Test extends AssertionsForJUnit {
  import RestActionCategoryEngine2Test._
  private[this] implicit val typicalDuration = 1.second

  @Test
  def playJsonGet1(): Unit = {
    testEmptyBody(PlayJsonTestResource.get1("test"))
  }

  @Test
  def playJsonGet2(): Unit = {
    testEmptyBody(PlayJsonTestResource.get2("test"))
  }

  @Test
  def playJsonCreate1(): Unit = {
    testEmptyBody(PlayJsonTestResource.create1)
  }

  @Test
  def playJsonCreate2(): Unit = {
    testEmptyBody(PlayJsonTestResource.create2)
  }

  @Test
  def playJsonCreate3(): Unit = {
    testEmptyBody(PlayJsonTestResource.create3)
  }

  @Test
  def playJsonDelete1(): Unit = {
    testEmptyBody(PlayJsonTestResource.delete1("test"))
  }

  @Test
  def courierGet1(): Unit = {
    testEmptyBody(CourierTestResource.get1("test"))
  }

  @Test
  def courierGet2(): Unit = {
    testEmptyBody(CourierTestResource.get2("test"))
  }

  @Test
  def courierCreate1(): Unit = {
    testEmptyBody(CourierTestResource.create1)
  }

  @Test
  def courierCreate2(): Unit = {
    testEmptyBody(CourierTestResource.create2)
  }

  @Test
  def courierCreate3(): Unit = {
    testEmptyBody(CourierTestResource.create3)
  }

  @Test
  def courierDelete1(): Unit = {
    testEmptyBody(CourierTestResource.delete1("test"))
  }

  // Test helpers here and below.

  private[this] def testEmptyBody(
      actionToTest: RestAction[_, _, AnyContent, _, _, _],
      request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(),
      strictMode: Boolean = false): Result = {
    val result = runTestRequest(actionToTest, request)
    Validators.assertValidResponse(result, strictMode = strictMode)
    result
  }

  private[this] def runTestRequestInternal[BodyType](
      restAction: RestAction[_, _, BodyType, _, _, _],
      request: RequestHeader,
      body: Enumerator[Array[Byte]] = Enumerator.empty)
      (implicit duration: FiniteDuration): Result = {
    val iteratee = restAction.apply(request)
    val resultFut = body.run(iteratee)
    Await.result(resultFut, duration)
  }


  private[this] def runTestRequest[BodyType](restAction: RestAction[_, _, BodyType, _, _, _],
    fakeRequest: FakeRequest[BodyType])(
    implicit duration: FiniteDuration,
    writeable: Writeable[BodyType]): Result = {
    val requestWithHeader = writeable.contentType.map { ct =>
      fakeRequest.withHeaders(HeaderNames.CONTENT_TYPE -> ct)
    }.getOrElse(fakeRequest)
    val b = Enumerator(fakeRequest.body).through(writeable.toEnumeratee)
    runTestRequestInternal(restAction, requestWithHeader, b)(duration)
  }

  private[this] def runTestRequest(restAction: RestAction[_, _, AnyContent, _, _, _],
    fakeRequest: FakeRequest[AnyContentAsEmpty.type])(
    implicit duration: FiniteDuration): Result = {
    runTestRequestInternal(restAction, fakeRequest)(duration)
  }


}
