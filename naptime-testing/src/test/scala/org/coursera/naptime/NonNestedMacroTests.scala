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

package org.coursera.naptime

import akka.stream.Materializer
import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.naptime.actions.NaptimeActionSerializer.AnyWrites._
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.path.ParseSuccess
import org.coursera.naptime.path.RootParsedPathKey
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.coursera.naptime.router2._
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import org.mockito.Mockito._
import org.mockito.Matchers.any

import scala.concurrent.ExecutionContext

case class Item(name: String, description: String)
object Item {
  implicit val jsonFormat: OFormat[Item] = Json.format[Item]
}

case class ComplexEmailType(user: String, domain: String)

object ComplexEmailType {
  implicit val stringKeyFormat =
    StringKeyFormat.caseClassFormat((ComplexEmailType.apply _).tupled, ComplexEmailType.unapply)
}

class Resource(implicit val executionContext: ExecutionContext, val materializer: Materializer)
    extends TopLevelCollectionResource[String, Item] {

  override def keyFormat: KeyFormat[String] = KeyFormat.stringKeyFormat

  override implicit def resourceFormat: OFormat[Item] = Item.jsonFormat

  override def resourceName: String = "items"

  implicit val fields = Fields.withDefaultFields("name")

  val allItems = List(
    Keyed("1", Item("one", "the first one")),
    Keyed("2", Item("two", "the first one after the first one")),
    Keyed("3", Item("tree", "with branches and leaves")),
    Keyed("4", Item("flour", "make bread with me")))

  def getAll = Nap.getAll { implicit ctx =>
    Ok(allItems)
  }

  def get(id: String) = Nap.get { ctx =>
    Ok(allItems.head)
  }

  def multiGet(ids: Set[String]) = Nap.multiGet { ctx =>
    Ok(allItems.filter(item => ids.contains(item.key)))
  }

  def update(id: String) = Nap.update { ctx =>
    ???
  }

  def delete(id: String) = Nap.delete { ctx =>
    ???
  }

  def create = Nap.create { ctx =>
    ???
  }

  def byEmail(email: String) = Nap.finder { ctx =>
    ???
  }

  def byUsernameAndDomain(userName: String, domain: String) = Nap.finder { ctx =>
    ???
  }

  def complex(complex: ComplexEmailType, extra: Option[String]) = Nap.finder { ctx =>
    ???
  }

  def batchModify(someParam: Option[ComplexEmailType]) = Nap.action { ctx =>
    ???
  }

  def parameterless() = Nap.action { ctx =>
    ???
  }

}

object Resource {
  val routerBuilder = Router.build[Resource]
}

class NonNestedMacroTests extends AssertionsForJUnit with MockitoSugar with ResourceTestImplicits {

  val instance = mock[Resource]
  val instanceImpl = new Resource
  when(instance.resourceName).thenReturn("items")
  when(instance.resourceVersion).thenReturn(1)
  when(instance.keyFormat).thenReturn(KeyFormat.stringKeyFormat)

  when(instance.get(any())).thenReturn(instanceImpl.get("id"))
  when(instance.multiGet(any())).thenReturn(instanceImpl.multiGet(Set.empty))
  when(instance.getAll).thenReturn(instanceImpl.getAll)
  when(instance.create).thenReturn(instanceImpl.create)
  when(instance.delete(any())).thenReturn(instanceImpl.delete("someId"))
  when(instance.update(any())).thenReturn(instanceImpl.update("someId"))
  when(instance.byEmail(any())).thenReturn(instanceImpl.byEmail("emailAddr"))
  when(instance.byUsernameAndDomain(any(), any()))
    .thenReturn(instanceImpl.byUsernameAndDomain("user", "domain"))
  when(instance.parameterless()).thenReturn(instanceImpl.parameterless())
  when(instance.batchModify(any())).thenReturn(instanceImpl.batchModify(None))
  when(instance.complex(any(), any())).thenReturn(instanceImpl.complex(null, None))

  val router =
    Resource.routerBuilder.build(instance.asInstanceOf[Resource.routerBuilder.ResourceClass])

  private[this] def mkRequest(
      id: Option[String],
      method: String = "GET",
      query: Map[String, String] = Map.empty): FakeRequest[AnyContentAsEmpty.type] = {
    var path = "/api/items.v1"
    id.foreach { id =>
      path = s"$path/$id"
    }
    when(instance.optParse(path.substring("/api".length))).thenReturn(
      ParseSuccess(None, id ::: RootParsedPathKey).asInstanceOf[ParseSuccess[instance.OptPathKey]])
    if (query.nonEmpty) {
      val queryStr = query
        .map { param =>
          s"${param._1}=${param._2}"
        }
        .toList
        .mkString("?", "&", "")
      path = s"$path$queryStr"
    }
    FakeRequest(method, path)
  }

  private[this] def checkRouter(requestHeader: RequestHeader, methodName: String): Unit = {
    assert(requestHeader.path.startsWith("/api"))
    val result = router.routeRequest(requestHeader.path.substring("/api".length), requestHeader)
    assert(result.isDefined)
    val taggedRequest = result.get.tagRequest(requestHeader)
    assert(taggedRequest.tags.contains(Router.NAPTIME_RESOURCE_NAME))
    assert(taggedRequest.tags.get(Router.NAPTIME_METHOD_NAME).contains(methodName))
  }

  @Test
  def getAllTest(): Unit = {
    val getAllRequest = mkRequest(None)
    checkRouter(getAllRequest, "getAll")
    verify(instance).getAll
  }

  @Test
  def getTest(): Unit = {
    val getRequest = mkRequest(Some("123"))
    checkRouter(getRequest, "get")
    verify(instance).get("123")
  }

  @Test
  def multiGetTest(): Unit = {
    val multiGetRequest = mkRequest(None, query = Map("ids" -> "1,2"))
    checkRouter(multiGetRequest, "multiGet")
    verify(instance).multiGet(Set("1", "2"))
  }

  @Test
  def updateTest(): Unit = {
    val putRequest = mkRequest(Some("123"), "PUT")
    checkRouter(putRequest, "update")
    verify(instance).update("123")
  }

  @Test
  def deleteTest(): Unit = {
    val deleteRequest = mkRequest(Some("123"), "DELETE")
    checkRouter(deleteRequest, "delete")
    verify(instance).delete("123")
  }

  @Test
  def createTest(): Unit = {
    val createRequest = mkRequest(None, "POST")
    checkRouter(createRequest, "create")
    verify(instance).create
  }

  @Test
  def finderByEmailTest(): Unit = {
    val finderRequest =
      mkRequest(None, query = Map("q" -> "byEmail", "email" -> "user@example.com"))
    checkRouter(finderRequest, "byEmail")
    verify(instance).byEmail("user@example.com")
  }

  @Test
  def finderByUsernameAndDomainTest(): Unit = {
    val finderRequest = mkRequest(
      None,
      query = Map("q" -> "byUsernameAndDomain", "userName" -> "daphne", "domain" -> "coursera.org"))
    checkRouter(finderRequest, "byUsernameAndDomain")
    verify(instance).byUsernameAndDomain("daphne", "coursera.org")
  }

  @Test
  def finderComplexTest(): Unit = {
    val finderRequest =
      mkRequest(None, query = Map("q" -> "complex", "complex" -> "daphne~coursera.org"))
    checkRouter(finderRequest, "complex")
    verify(instance).complex(ComplexEmailType("daphne", "coursera.org"), None)
  }

  @Test
  def finderComplexTypes2(): Unit = {
    val finderRequest2 = mkRequest(
      None,
      query = Map("q" -> "complex", "complex" -> "daphne~coursera.org", "extra" -> "foo"))
    checkRouter(finderRequest2, "complex")
    verify(instance).complex(ComplexEmailType("daphne", "coursera.org"), Some("foo"))
  }

  @Test
  def actionTest(): Unit = {
    val actionRequest = mkRequest(
      None,
      "POST",
      query = Map("action" -> "batchModify", "someParam" -> "daphne~coursera.org"))
    checkRouter(actionRequest, "batchModify")
    verify(instance).batchModify(Some(ComplexEmailType("daphne", "coursera.org")))
  }

  @Test
  def parameterlessTest(): Unit = {
    val actionRequest = mkRequest(None, "POST", query = Map("action" -> "parameterless"))
    checkRouter(actionRequest, "parameterless")
    verify(instance).parameterless()
  }

}
