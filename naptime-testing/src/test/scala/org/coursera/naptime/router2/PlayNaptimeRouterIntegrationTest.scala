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

package org.coursera.naptime.router2

import akka.stream.Materializer
import com.google.inject.Guice
import org.coursera.common.jsonformat.JsonFormats.Implicits.dateTimeFormat
import org.coursera.naptime.actions.NaptimeActionSerializer.AnyWrites._
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.ComplexEmailType
import org.coursera.naptime.NaptimeModule
import org.coursera.naptime.Ok
import org.coursera.naptime.ResourceTestImplicits
import org.coursera.naptime.path.ParseFailure
import org.coursera.naptime.path.ParseSuccess
import org.coursera.naptime.path.RootParsedPathKey
import org.coursera.naptime.resources.CollectionResource
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.joda.time.DateTime
import org.junit.Test
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext

// TODO(saeta): De-dupe the test resources to share amongst tests.
object PlayNaptimeRouterIntegrationTest {
  case class Person(name: String, email: String = "a@b.c")

  object Person {
    implicit val jsonFormat: OFormat[Person] = Json.format[Person]
  }

  /**
   * The top level resource in our fledgling social network.
   */
  class PersonResource(
      implicit val executionContext: ExecutionContext,
      val materializer: Materializer)
      extends TopLevelCollectionResource[String, Person] {

    val PATH_KEY: PathKey = ("myPathKeyId" ::: RootParsedPathKey).asInstanceOf[PathKey]

    override def keyFormat: KeyFormat[String] = KeyFormat.stringKeyFormat

    override implicit def resourceFormat: OFormat[Person] = Person.jsonFormat

    override def resourceName: String = "people"

    implicit val fields = Fields.withDefaultFields("name")

    val allItems = List(
      Keyed("1", Person("gail")),
      Keyed("2", Person("lucy")),
      Keyed("3", Person("fred")),
      Keyed("4", Person("bill")))

    def getAll = Nap.getAll(implicit ctx => Ok(allItems))

    def get(id: PathKey) = Nap.get(ctx => Ok(allItems.head))

    def multiGet(ids: Set[String]) = Nap.multiGet { ctx =>
      Ok(allItems.filter(item => ids.contains(item.key)))
    }

    def update(id: String) = Nap.update(ctx => ???)

    def delete(id: KeyType) = Nap.delete(ctx => ???)

    def create = Nap.create(ctx => ???)

    def byEmail(email: String) = Nap.finder(ctx => ???)

    def byUsernameAndDomain(userName: String, domain: String) = Nap.finder(ctx => ???)

    def complex(complex: ComplexEmailType, extra: Option[String]) = Nap.finder(ctx => ???)

    def batchModify(someParam: Option[ComplexEmailType]) = Nap.action(ctx => ???)

    def parameterless() = Nap.action(ctx => ???)
  }

  case class FriendshipInfo(friendshipQuality: String, friendsSince: DateTime)

  object FriendshipInfo {
    implicit val jsonFormat: OFormat[FriendshipInfo] = Json.format[FriendshipInfo]
  }

  class FriendsResource(val parentResource: PersonResource)(
      implicit val executionContext: ExecutionContext,
      val materializer: Materializer)
      extends CollectionResource[PersonResource, String, FriendshipInfo] {
    override def keyFormat: KeyFormat[KeyType] = KeyFormat.stringKeyFormat

    override implicit def resourceFormat: OFormat[FriendshipInfo] = FriendshipInfo.jsonFormat

    override def resourceName: String = "friends"

    implicit val fields = Fields.withDefaultFields("friendshipQuality")

    val ANCESTOR_KEY: AncestorKeys =
      ("parentKey" ::: RootParsedPathKey).asInstanceOf[AncestorKeys]
    val PATH_KEY: PathKey = ("friendId" ::: ANCESTOR_KEY).asInstanceOf[PathKey]
    val OPT_PATH_KEY: OptPathKey = (None ::: ANCESTOR_KEY).asInstanceOf[OptPathKey]

    def getAll(ancestorKeys: AncestorKeys) = Nap.getAll(ctx => ???)

    def get(id: PathKey) = Nap.get(ctx => ???)

    def multiGet(ids: Set[String], parentIds: AncestorKeys) = Nap.multiGet(ctx => ???)

    def update(id: String, parentIds: AncestorKeys) = Nap.update(ctx => ???)

    def delete(id: KeyType, fullId: PathKey) = Nap.delete(ctx => ???)

    def create(ancestorKeys: AncestorKeys) = Nap.create(ctx => ???)

    def byEmail(optPathKey: OptPathKey, email: String) = Nap.finder(ctx => ???)

    def byUsernameAndDomain(ancestorKeys: AncestorKeys, userName: String, domain: String) =
      Nap.finder(ctx => ???)

    def withDefaults(
        ancestorKeys: AncestorKeys,
        userName: String,
        domain: String = "defaultDomain") = Nap.finder(ctx => ???)

    def complex(complex: ComplexEmailType, extra: Option[String]) = Nap.finder(ctx => ???)

    def batchModify(ancestorKeys: AncestorKeys, someParam: Option[ComplexEmailType]) =
      Nap.action(ctx => ???)
  }
}

class PlayNaptimeRouterIntegrationTest
    extends AssertionsForJUnit
    with MockitoSugar
    with ResourceTestImplicits {
  import PlayNaptimeRouterIntegrationTest._

  val peopleInstanceImpl = new PersonResource
  val peopleInstance = mock[PersonResource]
  when(peopleInstance.resourceName).thenReturn("people")
  when(peopleInstance.resourceVersion).thenReturn(1)
  when(peopleInstance.keyFormat).thenReturn(KeyFormat.stringKeyFormat)

  when(peopleInstance.get(any())).thenReturn(peopleInstanceImpl.get(peopleInstanceImpl.PATH_KEY))
  when(peopleInstance.multiGet(any())).thenReturn(peopleInstanceImpl.multiGet(Set.empty))
  when(peopleInstance.getAll).thenReturn(peopleInstanceImpl.getAll)
  when(peopleInstance.delete(any())).thenReturn(peopleInstanceImpl.delete("123"))
  when(peopleInstance.update(any())).thenReturn(peopleInstanceImpl.update("123"))
  when(peopleInstance.create).thenReturn(peopleInstanceImpl.create)
  when(peopleInstance.batchModify(any())).thenReturn(peopleInstanceImpl.batchModify(None))
  when(peopleInstance.byEmail(any())).thenReturn(peopleInstanceImpl.byEmail("asdf"))
  when(peopleInstance.byUsernameAndDomain(any(), any()))
    .thenReturn(peopleInstanceImpl.byUsernameAndDomain("", ""))
  when(peopleInstance.complex(any(), any())).thenReturn(peopleInstanceImpl.complex(null, None))
  when(peopleInstance.parameterless()).thenReturn(peopleInstanceImpl.parameterless())

  val friendsInstanceImpl = new FriendsResource(peopleInstanceImpl)
  val friendsInstance = mock[FriendsResource]
  when(friendsInstance.resourceName).thenReturn("friends")
  when(friendsInstance.resourceVersion).thenReturn(1)
  when(friendsInstance.keyFormat).thenReturn(KeyFormat.stringKeyFormat)

  when(friendsInstance.get(any())).thenReturn(friendsInstanceImpl.get(friendsInstanceImpl.PATH_KEY))
  when(friendsInstance.multiGet(any(), any()))
    .thenReturn(friendsInstanceImpl.multiGet(Set.empty, friendsInstanceImpl.ANCESTOR_KEY))
  when(friendsInstance.getAll(any()))
    .thenReturn(friendsInstanceImpl.getAll(friendsInstanceImpl.ANCESTOR_KEY))
  when(friendsInstance.update(any(), any()))
    .thenReturn(friendsInstanceImpl.update("fakeId", friendsInstanceImpl.ANCESTOR_KEY))
  when(friendsInstance.delete(any(), any()))
    .thenReturn(friendsInstanceImpl.delete("fakeId", friendsInstanceImpl.PATH_KEY))
  when(friendsInstance.create(any()))
    .thenReturn(friendsInstanceImpl.create(friendsInstanceImpl.ANCESTOR_KEY))
  when(friendsInstance.batchModify(any(), any()))
    .thenReturn(friendsInstanceImpl.batchModify(friendsInstanceImpl.ANCESTOR_KEY, None))
  when(friendsInstance.byEmail(any(), any()))
    .thenReturn(friendsInstanceImpl.byEmail(friendsInstanceImpl.OPT_PATH_KEY, "fakeEmail"))
  when(friendsInstance.byUsernameAndDomain(any(), any(), any()))
    .thenReturn(friendsInstanceImpl.byUsernameAndDomain(friendsInstanceImpl.ANCESTOR_KEY, "", ""))
  when(friendsInstance.complex(any(), any())).thenReturn(friendsInstanceImpl.complex(null, None))
  when(friendsInstance.withDefaults(any(), any(), any()))
    .thenReturn(friendsInstanceImpl.withDefaults(friendsInstanceImpl.ANCESTOR_KEY, "", ""))

  object TestModule extends NaptimeModule {
    override def configure(): Unit = {
      bindResource[FriendsResource]
      bindResource[PersonResource]
      bind[FriendsResource].toInstance(friendsInstance)
      bind[PersonResource].toInstance(peopleInstance)
    }
  }

  val injector = Guice.createInjector(TestModule)
  val playNaptimeRouter = injector.getInstance(classOf[NaptimePlayRouter]).withPrefix("/api")

  private[this] def mkPeopleRequest(
      id: Option[String],
      method: String = "GET",
      query: Map[String, String] = Map.empty): FakeRequest[AnyContentAsEmpty.type] = {
    var path = "/api/people.v1"
    id.foreach { id =>
      path = s"$path/$id"
    }
    when(peopleInstance.optParse(path.substring("/api".length))).thenReturn(
      ParseSuccess(None, id ::: RootParsedPathKey)
        .asInstanceOf[ParseSuccess[peopleInstance.OptPathKey]])
    when(friendsInstance.optParse(path.substring("/api".length))).thenReturn(ParseFailure)
    if (query.nonEmpty) {
      val queryStr = query
        .map { param =>
          s"${param._1}=${param._2}"
        }
        .toList
        .mkString("?", "&", "")
      path = s"$path$queryStr"
    }
    val request = FakeRequest(method, path)
    request
  }

  private[this] def mkFriendRequest(
      id: Option[String],
      method: String = "GET",
      query: Map[String, String] = Map.empty,
      personId: String = "zyx"): FakeRequest[AnyContentAsEmpty.type] = {
    var suffix = "/friends"
    id.foreach { id =>
      suffix = s"$suffix/$id"
    }
    var path = s"/api/people.v1/$personId$suffix"
    when(friendsInstance.optParse(path.substring("/api".length))).thenReturn(
      ParseSuccess(None, id ::: personId ::: RootParsedPathKey)
        .asInstanceOf[ParseSuccess[friendsInstance.OptPathKey]])
    when(peopleInstance.optParse(path.substring("/api".length))).thenReturn(
      ParseSuccess(Some(suffix), personId ::: RootParsedPathKey)
        .asInstanceOf[ParseSuccess[peopleInstance.OptPathKey]]
    )
    if (query.nonEmpty) {
      val queryStr = query
        .map { param =>
          s"${param._1}=${param._2}"
        }
        .toList
        .mkString("?", "&", "")
      path = s"$path$queryStr"
    }
    val request = FakeRequest(method, path)
    request
  }

  private[this] def test(
      request: RequestHeader,
      resource: CollectionResource[_, _, _],
      methodName: String): Unit = {
    val result = playNaptimeRouter.handlerFor(request)
    assert(result.isDefined)
    val routeAction = result.get.asInstanceOf[RouteAction]
    val taggedRequest = routeAction.tagRequest(request)
    assert(taggedRequest.tags.get(Router.NAPTIME_RESOURCE_NAME).contains(resource.getClass.getName))
    assert(taggedRequest.tags.get(Router.NAPTIME_METHOD_NAME).contains(methodName))
  }

  @Test
  def peopleGetAll(): Unit = {
    val getAllRequest = mkPeopleRequest(None)
    test(getAllRequest, peopleInstance, "getAll")
    verify(peopleInstance).getAll
  }

  @Test
  def peopleGet(): Unit = {
    val request = mkPeopleRequest(Some("asdf"))
    test(request, peopleInstance, "get")
    verify(peopleInstance).get(any())
  }

  @Test
  def peopleMultiGet(): Unit = {
    val request = mkPeopleRequest(None, query = Map("ids" -> "a,b,c,d"))
    test(request, peopleInstance, "multiGet")
    verify(peopleInstance).multiGet(any())
  }

  @Test
  def peopleCreate(): Unit = {
    val request = mkPeopleRequest(None, "POST")
    test(request, peopleInstance, "create")
    verify(peopleInstance).create
  }

  @Test
  def peopleUpdate(): Unit = {
    val request = mkPeopleRequest(Some("asdf"), "PUT")
    test(request, peopleInstance, "update")
    verify(peopleInstance).update(any())
  }

  @Test
  def peopleDelete(): Unit = {
    val request = mkPeopleRequest(Some("asdf"), "DELETE")
    test(request, peopleInstance, "delete")
    verify(peopleInstance).delete(any())
  }

  @Test
  def peopleFinderByEmail(): Unit = {
    val request = mkPeopleRequest(None, query = Map("q" -> "byEmail", "email" -> "asdf@g.c"))
    test(request, peopleInstance, "byEmail")
    verify(peopleInstance).byEmail(any())
  }

  @Test
  def friendsGetAll(): Unit = {
    val request = mkFriendRequest(None)
    test(request, friendsInstance, "getAll")
    verify(friendsInstance).getAll(any())
  }

  @Test
  def friendsGet(): Unit = {
    val request = mkFriendRequest(Some("asdf"))
    test(request, friendsInstance, "get")
    verify(friendsInstance).get(any())
  }

  @Test
  def friendsMultiGet(): Unit = {
    val request = mkFriendRequest(None, query = Map("ids" -> "asdf,fdsa"))
    test(request, friendsInstance, "multiGet")
    verify(friendsInstance).multiGet(any(), any())
  }

  @Test
  def documentationCheck(): Unit = {
    val docs = playNaptimeRouter.documentation
    assert(22 === docs.length)
    val defaultMethod = docs.filter { docLine =>
      docLine._3.contains("withDefaults")
    }
    assert(defaultMethod.length === 1, "Could not find a unique delete method.")
    assert(defaultMethod.head._3.contains("defaultDomain"))
    assert(defaultMethod.head._2 === "/api/people.v1/$id/friends?q=withDefaults")
    assert(defaultMethod.head._1 === "GET --- FINDER")

    val deleteFriends = docs.filter { docLine =>
      docLine._1.contains("DELETE") && docLine._2.contains("/friend")
    }
    assert(deleteFriends.length === 1, s"Could not find a unique delete friends method:\n$docs")
    assert(deleteFriends.head._1 === "DELETE --- DELETE")
    assert(deleteFriends.head._2 === "/api/people.v1/$id/friends/$id")
    assert(
      deleteFriends.head._3 ===
        "[NAPTIME] org.coursera.naptime.router2.PlayNaptimeRouterIntegrationTest." +
          "FriendsResource.delete(" +
          "id: FriendsResource.this.KeyType, fullId: FriendsResource.this.PathKey)")

    val parameterless = docs.filter { docLine =>
      docLine._1.contains("ACTION") && docLine._2.contains("parameterless")
    }
    assert(parameterless.length === 1, s"Could not find a unique parameterless action:\n$docs")
    assert(parameterless.head._1 === "POST --- ACTION")
    assert(parameterless.head._2 === "/api/people.v1?action=parameterless")
    assert(
      parameterless.head._3 === "[NAPTIME] org.coursera.naptime.router2." +
        "PlayNaptimeRouterIntegrationTest.PersonResource.parameterless")
  }

}
