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

import com.linkedin.data.DataMap
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.actions.NaptimeSerializer.AnyWrites._
import org.coursera.naptime.actions.NaptimeActionSerializer.AnyWrites._
import org.coursera.naptime.courier.CourierFormats
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.ResourceKind
import org.coursera.naptime.actions.RestActionBuilder
import org.coursera.naptime.access.HeaderAccessControl
import org.coursera.naptime.path.ParseFailure
import org.coursera.naptime.path.ParseSuccess
import org.coursera.naptime.path.RootParsedPathKey
import org.coursera.naptime.resources.CollectionResource
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.coursera.naptime.router2.Router
import org.coursera.naptime.schema.ArbitraryValue.StringMember
import org.joda.time.DateTime
import org.junit.Test
import org.mockito.Mockito._
import org.mockito.Matchers.any
import org.mockito.Matchers.{eq => e}
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.mvc.AnyContent
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.BodyParsers
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest

import scala.collection.JavaConversions._

/**
 * The top level resource in our fledgling social network.
 */
class PersonResource
  extends TopLevelCollectionResource[String, Person] {

  val PATH_KEY: PathKey = ("myPathKeyId" ::: RootParsedPathKey).asInstanceOf[PathKey]

  override def keyFormat: KeyFormat[String] = KeyFormat.stringKeyFormat

  override implicit def resourceFormat: OFormat[Person] = CourierFormats.recordTemplateFormats[Person]

  override def resourceName: String = "people"

  implicit val fields = Fields.withDefaultFields("name")

  val allItems = List(
    Keyed("1", Person("gail")),
    Keyed("2", Person("lucy")),
    Keyed("3", Person("fred")),
    Keyed("4", Person("bill")))

  def getAll = Nap.getAll { implicit ctx =>
    Ok(allItems)
  }

  def get(id: PathKey) = Nap.get { ctx =>
    Ok(allItems.head)
  }

  def multiGet(ids: Set[String]) = Nap.multiGet { ctx =>
    Ok(allItems.filter(item => ids.contains(item.key)))
  }

  def update(id: String, message: Option[String]) = Nap.update(ctx => ???)

  // Required argument with default allowed.
  def delete(id: KeyType, message: String = "Something?") = Nap.delete(ctx => ???)

  def create = Nap.create(ctx => ???)

  def byEmail(email: String) = Nap.finder(ctx => ???)

  def byUsernameAndDomain(userName: String, domain: String) = Nap.finder(ctx => ???)

  def byUsernameSorted(userName: String, sort: SortOrder) = Nap.finder(ctx => ???)

  def complex(complex: ComplexEmailType, extra: Option[String], skipCache: Boolean) =
    Nap.finder(ctx => ???)

  def complexWithDefault(complex: ComplexEmailType = ComplexEmailType("daphne", "coursera.org")) =
    Nap.finder(ctx => ???)

  def batchModify(someParam: Option[ComplexEmailType]) = Nap.action(ctx => ???)

  def parameterless() = Nap.action(ctx => ???)
}

object PersonResource {
  val routerBuilder = Router.build[PersonResource]
}

case class FriendshipInfo(
  friendshipQuality: String,
  friendsSince: DateTime)

object FriendshipInfo {
  implicit val jsonFormat: OFormat[FriendshipInfo] = Json.format[FriendshipInfo]
}

class FriendsResource(val parentResource: PersonResource)
  extends CollectionResource[PersonResource, String, FriendshipInfo] {
  override def keyFormat: KeyFormat[KeyType] = KeyFormat.stringKeyFormat

  override implicit def resourceFormat: OFormat[FriendshipInfo] = FriendshipInfo.jsonFormat

  override def resourceName: String = "friends"

  implicit val fields = Fields.withDefaultFields("friendshipQuality")

  val ANCESTOR_KEY: AncestorKeys =
    ("parentKey" ::: RootParsedPathKey).asInstanceOf[AncestorKeys]
  val PATH_KEY: PathKey = ("friendId" ::: ANCESTOR_KEY).asInstanceOf[PathKey]
  val OPT_PATH_KEY: OptPathKey = (None ::: ANCESTOR_KEY).asInstanceOf[OptPathKey]

  def getAll(ancestorKeys: AncestorKeys) = Nap.getAll { implicit ctx =>
    ???
  }

  def get(id: PathKey) = Nap.get(ctx => ???)

  def multiGet(ids: Set[String], parentIds: AncestorKeys) = Nap.multiGet(ctx => ???)

  def update(id: String, parentIds: AncestorKeys) = Nap.update(ctx => ???)

  def delete(id: KeyType, fullId: PathKey) = Nap.delete(ctx => ???)

  def create(ancestorKeys: AncestorKeys) = Nap.create(ctx => ???)

  def byEmail(optPathKey: OptPathKey, email: String) = Nap.finder(ctx => ???)

  def byUsernameAndDomain(
      ancestorKeys: AncestorKeys,
      userName: String,
      domain: String) = Nap.finder(ctx => ???)

  def withDefaults(
    ancestorKeys: AncestorKeys,
    userName: String,
    domain: String = "defaultDomain") = Nap.finder(ctx => ???)

  def complex(complex: ComplexEmailType, extra: Option[String]) = Nap.finder(ctx => ???)

  def batchModify(
      ancestorKeys: AncestorKeys,
      someParam: Option[ComplexEmailType]) = Nap.action(ctx => ???)
}

object FriendsResource {
  val routerBuilder = Router.build[FriendsResource]
}

class NestedMacroTests extends AssertionsForJUnit with MockitoSugar {

  val peopleInstanceImpl = new PersonResource
  val peopleInstance = mock[PersonResource]
  when(peopleInstance.resourceName).thenReturn("people")
  when(peopleInstance.resourceVersion).thenReturn(1)
  when(peopleInstance.keyFormat).thenReturn(KeyFormat.stringKeyFormat)

  when(peopleInstance.get(any())).thenReturn(peopleInstanceImpl.get(peopleInstanceImpl.PATH_KEY))
  when(peopleInstance.multiGet(any())).thenReturn(peopleInstanceImpl.multiGet(Set.empty))
  when(peopleInstance.getAll).thenReturn(peopleInstanceImpl.getAll)
  when(peopleInstance.delete(any(), any())).thenReturn(peopleInstanceImpl.delete("123"))
  when(peopleInstance.update(any(), any())).thenReturn(peopleInstanceImpl.update("123", None))
  when(peopleInstance.create).thenReturn(peopleInstanceImpl.create)
  when(peopleInstance.batchModify(any())).thenReturn(peopleInstanceImpl.batchModify(None))
  when(peopleInstance.byEmail(any())).thenReturn(peopleInstanceImpl.byEmail("asdf"))
  when(peopleInstance.byUsernameAndDomain(any(), any())).thenReturn(
    peopleInstanceImpl.byUsernameAndDomain("", ""))
  when(peopleInstance.byUsernameSorted(any(), any())).thenReturn(
    peopleInstanceImpl.byUsernameSorted("", SortOrder("any")))
  when(peopleInstance.complex(any(), any(), any())).thenReturn(
    peopleInstanceImpl.complex(null, None, false))
  when(peopleInstance.complexWithDefault(any())).thenReturn(peopleInstanceImpl.complexWithDefault())
  when(peopleInstance.parameterless()).thenReturn(peopleInstanceImpl.parameterless())

  val friendsInstanceImpl = new FriendsResource(peopleInstanceImpl)
  val friendsInstance = mock[FriendsResource]
  when(friendsInstance.resourceName).thenReturn("friends")
  when(friendsInstance.resourceVersion).thenReturn(1)
  when(friendsInstance.keyFormat).thenReturn(KeyFormat.stringKeyFormat)

  when(friendsInstance.get(any())).thenReturn(friendsInstanceImpl.get(friendsInstanceImpl.PATH_KEY))
  when(friendsInstance.multiGet(any(), any())).thenReturn(
    friendsInstanceImpl.multiGet(Set.empty, friendsInstanceImpl.ANCESTOR_KEY))
  when(friendsInstance.getAll(any())).thenReturn(
    friendsInstanceImpl.getAll(friendsInstanceImpl.ANCESTOR_KEY))
  when(friendsInstance.update(any(), any())).thenReturn(
    friendsInstanceImpl.update("fakeId", friendsInstanceImpl.ANCESTOR_KEY))
  when(friendsInstance.delete(any(), any())).thenReturn(
    friendsInstanceImpl.delete("fakeId", friendsInstanceImpl.PATH_KEY))
  when(friendsInstance.create(any())).thenReturn(
    friendsInstanceImpl.create(friendsInstanceImpl.ANCESTOR_KEY))
  when(friendsInstance.batchModify(any(), any())).thenReturn(
    friendsInstanceImpl.batchModify(friendsInstanceImpl.ANCESTOR_KEY, None))
  when(friendsInstance.byEmail(any(), any())).thenReturn(
    friendsInstanceImpl.byEmail(friendsInstanceImpl.OPT_PATH_KEY, "fakeEmail"))
  when(friendsInstance.byUsernameAndDomain(any(), any(), any())).thenReturn(
    friendsInstanceImpl.byUsernameAndDomain(friendsInstanceImpl.ANCESTOR_KEY, "", ""))
  when(friendsInstance.complex(any(), any())).thenReturn(friendsInstanceImpl.complex(null, None))
  when(friendsInstance.withDefaults(any(), any(), any())).thenReturn(
    friendsInstanceImpl.withDefaults(friendsInstanceImpl.ANCESTOR_KEY, "", ""))
  val peopleRouter = PersonResource.routerBuilder.build(
    peopleInstance.asInstanceOf[PersonResource.routerBuilder.ResourceClass])
  val friendRouter = FriendsResource.routerBuilder.build(
    friendsInstance.asInstanceOf[FriendsResource.routerBuilder.ResourceClass])

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
      val queryStr = query.map { param =>
        s"${param._1}=${param._2}"
      }.toList.mkString("?", "&", "")
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
      val queryStr = query.map { param =>
        s"${param._1}=${param._2}"
      }.toList.mkString("?", "&", "")
      path = s"$path$queryStr"
    }
    val request = FakeRequest(method, path)
    request
  }

  private[this] def checkRouter(request: RequestHeader, methodName: String): Unit = {
    assert(request.path.startsWith("/api"))
    val result = peopleRouter.routeRequest(request.path.substring("/api".length), request)
    assert(result.isDefined)
    val taggedRequest = result.get.tagRequest(request)
    assert(taggedRequest.tags.contains(Router.NAPTIME_RESOURCE_NAME))
    if (methodName != null && methodName != "") {
      assert(taggedRequest.tags.get(Router.NAPTIME_METHOD_NAME).contains(methodName))
    } else {
      assert(taggedRequest.tags.get(Router.NAPTIME_METHOD_NAME).isEmpty)
    }

    val subResult = friendRouter.routeRequest(request.path.substring("/api".length), request)
    assert(subResult.isEmpty)
  }

  private[this] def checkSubRouter(request: RequestHeader, methodName: String): Unit = {
    assert(request.path.startsWith("/api"))
    val result = friendRouter.routeRequest(request.path.substring("/api".length), request)
    assert(result.isDefined)
    val taggedRequest = result.get.tagRequest(request)
    assert(taggedRequest.tags.contains(Router.NAPTIME_RESOURCE_NAME))
    assert(taggedRequest.tags.get(Router.NAPTIME_METHOD_NAME).contains(methodName))

    val parentResult = peopleRouter.routeRequest(request.path.substring("/api".length), request)
    assert(parentResult.isEmpty)
  }

  @Test
  def getAllTest(): Unit = {
    val getAllRequest = mkPeopleRequest(None)
    checkRouter(getAllRequest, "getAll")
    verify(peopleInstance).getAll
  }

  @Test
  def getTest(): Unit = {
    val getRequest = mkPeopleRequest(Some("123"))
    checkRouter(getRequest, "get")
    verify(peopleInstance).get(any())
  }

  @Test
  def multiGetTest(): Unit = {
    val multiGetRequest = mkPeopleRequest(None, query = Map("ids" -> "123,321,abc"))
    checkRouter(multiGetRequest, "multiGet")
    verify(peopleInstance).multiGet(Set("123", "321", "abc"))
  }

  @Test
  def updateTest(): Unit = {
    val request = mkPeopleRequest(Some("321"), "PUT")
    checkRouter(request, "update")
    verify(peopleInstance).update("321", None)
  }

  @Test
  def updateTestWithMsg(): Unit = {
    val request = mkPeopleRequest(Some("321"), "PUT", query = Map("message" -> "My Message"))
    checkRouter(request, "update")
    verify(peopleInstance).update("321", Some("My Message"))
  }

  @Test
  def deleteTest(): Unit = {
    val request = mkPeopleRequest(Some("abc"), "DELETE")
    checkRouter(request, "delete")
    verify(peopleInstance).delete("abc")
  }

  @Test
  def deleteTestWithMessage(): Unit = {
    val request = mkPeopleRequest(Some("abc"), "DELETE", query = Map("message" -> "My message"))
    checkRouter(request, "delete")
    verify(peopleInstance).delete("abc", "My message")
  }

  @Test
  def createTest(): Unit = {
    val createRequest = mkPeopleRequest(None, "POST")
    checkRouter(createRequest, "create")
    verify(peopleInstance).create
  }

  @Test
  def finderByEmailTest(): Unit = {
    val finderRequest = mkPeopleRequest(None,
      query = Map("q" -> "byEmail", "email" -> "user@example.com"))
    checkRouter(finderRequest, "byEmail")
    verify(peopleInstance).byEmail("user@example.com")
  }

  @Test
  def finderByUsernameAndDomainTest(): Unit = {
    val finderRequest = mkPeopleRequest(None,
      query = Map("q" -> "byUsernameAndDomain", "userName" -> "daphne", "domain" -> "coursera.org"))
    checkRouter(finderRequest, "byUsernameAndDomain")
    verify(peopleInstance).byUsernameAndDomain("daphne", "coursera.org")
  }

  @Test
  def finderByUsernameSortedTestSimple(): Unit = {
    val finderRequest = mkPeopleRequest(None,
      query = Map("q" -> "byUsernameSorted", "userName" -> "daphne",
        "sort" -> "(field~timestamp,descending~false)"))
    checkRouter(finderRequest, "byUsernameSorted")
    verify(peopleInstance).byUsernameSorted("daphne", SortOrder("timestamp", false))
  }

  @Test
  def finderByUsernameSortedTestMissingField(): Unit = {
    val finderRequest = mkPeopleRequest(None,
      query = Map("q" -> "byUsernameSorted", "userName" -> "daphne",
        "sort" -> "(field~timestamp)"))
    checkRouter(finderRequest, "byUsernameSorted")
    verify(peopleInstance).byUsernameSorted("daphne", SortOrder("timestamp", true))
  }

  @Test
  def finderByUsernameSortedTestOldWireFormat(): Unit = {
    val finderRequest = mkPeopleRequest(None,
      query = Map("q" -> "byUsernameSorted", "userName" -> "daphne",
        "sort" -> "timestamp~false"))
    checkRouter(finderRequest, "byUsernameSorted")
    verify(peopleInstance).byUsernameSorted("daphne", SortOrder("timestamp", false))
  }

  @Test
  def finderComplexTest(): Unit = {
    val finderRequest = mkPeopleRequest(None,
      query = Map("q" -> "complex", "complex" -> "daphne~coursera.org", "skipCache" -> "false"))
    checkRouter(finderRequest, "complex")
    verify(peopleInstance).complex(ComplexEmailType("daphne", "coursera.org"), None, false)
  }

  @Test
  def finderComplexTypes2(): Unit = {
    val finderRequest2 = mkPeopleRequest(None,
      query = Map("q" -> "complex", "complex" -> "daphne~coursera.org", "extra" -> "foo",
        "skipCache" -> "true"))
    checkRouter(finderRequest2, "complex")
    verify(peopleInstance).complex(ComplexEmailType("daphne", "coursera.org"), Some("foo"), true)
  }

  @Test
  def finderComplexTypesMalformedBoolean(): Unit = {
    val finderRequest3 = mkPeopleRequest(None,
      query = Map("q" -> "complex", "complex" -> "daphne~coursera.org", "extra" -> "foo",
        "skipCache" -> "1"))
    checkRouter(finderRequest3, methodName = null)
    verify(peopleInstance, never()).complex(any(), any(), any())
  }

  @Test
  def finderComplexTypesMalformedRequiredParam(): Unit = {
    val finderRequest4 = mkPeopleRequest(None,
      query = Map("q" -> "complex", "complex" -> "malformed!", "extra" -> "foo",
        "skipCache" -> "true"))
    checkRouter(finderRequest4, methodName = null)
    verify(peopleInstance, never()).complex(any(), any(), any())
  }

  @Test
  def finderComplexTypesDefaultParam(): Unit = {
    NestedMacroTestsHelper.setupMockDefaults(peopleInstance,
      ComplexEmailType("daphne", "coursera.org"))
    val finderRequest = mkPeopleRequest(None,
      query = Map("q" -> "complexWithDefault", "complex" -> "andrew~coursera.org"))
    checkRouter(finderRequest, "complexWithDefault")
    verify(peopleInstance).complexWithDefault(ComplexEmailType("andrew", "coursera.org"))
    NestedMacroTestsHelper.verifyDefaultNotTaken(peopleInstance)
  }

  @Test
  def finderComplexTypesDefaultParamMissing(): Unit = {
    val default = ComplexEmailType("daphne", "coursera.org")
    NestedMacroTestsHelper.setupMockDefaults(peopleInstance, default)
    val finderRequest = mkPeopleRequest(None, query = Map("q" -> "complexWithDefault"))
    checkRouter(finderRequest, "complexWithDefault")
    verify(peopleInstance).complexWithDefault(default)
  }

  @Test
  def finderComplexTypesMalformedDefaultParam(): Unit = {
    NestedMacroTestsHelper.setupMockDefaults(peopleInstance,
      ComplexEmailType("daphne", "coursera.org"))
    val finderRequest = mkPeopleRequest(None,
      query = Map("q" -> "complexWithDefault", "complex" -> "malformed!"))
    checkRouter(finderRequest, methodName = null)
    verify(peopleInstance, never()).complexWithDefault(any())
    NestedMacroTestsHelper.verifyDefaultNotTaken(peopleInstance)
  }

  @Test
  def actionTest(): Unit = {
    val actionRequest = mkPeopleRequest(None, "POST",
      query = Map("action" -> "batchModify", "someParam" -> "daphne~coursera.org"))
    checkRouter(actionRequest, "batchModify")
    verify(peopleInstance).batchModify(Some(ComplexEmailType("daphne", "coursera.org")))
  }

  @Test
  def actionTestMalformedOptionalParam(): Unit = {
    val actionRequest = mkPeopleRequest(None, "POST",
      query = Map("action" -> "batchModify", "someParam" -> "malformedparam!"))
    checkRouter(actionRequest, methodName = null)
    verify(peopleInstance, never()).batchModify(any())
  }

  @Test
  def parameterlessTest(): Unit = {
    val finderRequest = mkPeopleRequest(None, "POST",
      query = Map("action" -> "parameterless"))
    checkRouter(finderRequest, "parameterless")
    verify(peopleInstance).parameterless()
  }

  @Test
  def nestedGetAllTest(): Unit = {
    val getAllRequest = mkFriendRequest(None)
    checkSubRouter(getAllRequest, "getAll")
    verify(friendsInstance).getAll(any())
  }

  @Test
  def nestedGetTest(): Unit = {
    val getRequest = mkFriendRequest(Some("123"))
    checkSubRouter(getRequest, "get")
    verify(friendsInstance).get(any())
  }

  @Test
  def nestedMultiGetTest(): Unit = {
    val multiGetRequest = mkFriendRequest(None, query = Map("ids" -> "123,321,abc"))
    checkSubRouter(multiGetRequest, "multiGet")
    verify(friendsInstance).multiGet(e(Set("123", "321", "abc")), any())
  }

  @Test
  def nestedUpdateTest(): Unit = {
    val request = mkFriendRequest(Some("321"), "PUT")
    checkSubRouter(request, "update")
    verify(friendsInstance).update(e("321"), any())
  }

  @Test
  def nestedDeleteTest(): Unit = {
    val request = mkFriendRequest(Some("abc"), "DELETE")
    checkSubRouter(request, "delete")
    verify(friendsInstance).delete(e("abc"), any())
  }

  @Test
  def nestedCreateTest(): Unit = {
    val createRequest = mkFriendRequest(None, "POST")
    checkSubRouter(createRequest, "create")
    verify(friendsInstance).create(any())
  }

  @Test
  def nestedFinderByEmailTest(): Unit = {
    val finderRequest = mkFriendRequest(None,
      query = Map("q" -> "byEmail", "email" -> "user@example.com"))
    checkSubRouter(finderRequest, "byEmail")
    verify(friendsInstance).byEmail(any(), e("user@example.com"))
  }

  @Test
  def nestedFinderByUsernameAndDomainTest(): Unit = {
    val finderRequest = mkFriendRequest(None,
      query = Map("q" -> "byUsernameAndDomain", "userName" -> "daphne", "domain" -> "coursera.org"))
    checkSubRouter(finderRequest, "byUsernameAndDomain")
    verify(friendsInstance).byUsernameAndDomain(any(), e("daphne"), e("coursera.org"))
  }

  @Test
  def defaultsMissing(): Unit = {
    NestedMacroTestsHelper.setupMockDefaults(friendsInstance, "defaultDomain")
    val finderRequest = mkFriendRequest(None,
      query = Map("q" -> "withDefaults", "userName" -> "daphne"))
    checkSubRouter(finderRequest, "withDefaults")
    verify(friendsInstance).withDefaults(any(), e("daphne"), e("defaultDomain"))
  }

  @Test
  def defaultsOverriding(): Unit = {
    val finderRequest = mkFriendRequest(None,
      query = Map("q" -> "withDefaults", "userName" -> "daphne", "domain" -> "alternateDomain"))
    checkSubRouter(finderRequest, "withDefaults")
    verify(friendsInstance).withDefaults(any(), e("daphne"), e("alternateDomain"))
  }


  @Test
  def defaultsMissingRequired(): Unit = {
    val finderRequest = mkFriendRequest(None,
      query = Map("q" -> "withDefaults"))
    val result = friendRouter.routeRequest(finderRequest.path.substring("/api".length),
      finderRequest)
    assert(result.isDefined)
    verify(friendsInstance, never()).withDefaults(any(), any(), any())
  }

  @Test
  def nestedFinderComplexTest(): Unit = {
    val finderRequest = mkFriendRequest(None,
      query = Map("q" -> "complex", "complex" -> "daphne~coursera.org"))
    checkSubRouter(finderRequest, "complex")
    verify(friendsInstance).complex(ComplexEmailType("daphne", "coursera.org"), None)
  }

  @Test
  def nestedFinderComplexTypes2(): Unit = {
    val finderRequest2 = mkFriendRequest(None,
      query = Map("q" -> "complex", "complex" -> "daphne~coursera.org", "extra" -> "foo"))
    checkSubRouter(finderRequest2, "complex")
    verify(friendsInstance).complex(ComplexEmailType("daphne", "coursera.org"), Some("foo"))
  }

  @Test
  def nestedActionTest(): Unit = {
    val actionRequest = mkFriendRequest(None, "POST",
      query = Map("action" -> "batchModify", "someParam" -> "daphne~coursera.org"))
    checkSubRouter(actionRequest, "batchModify")
    verify(friendsInstance).batchModify(any(), e(Some(ComplexEmailType("daphne", "coursera.org"))))
  }

  @Test
  def schemaTest(): Unit = {
    val schema = PersonResource.routerBuilder.schema
    assert(schema.className === "org.coursera.naptime.PersonResource")
    assert(schema.kind === ResourceKind.COLLECTION)
    assert(schema.name === "people")
    assert(schema.version === Some(1))
    assert(schema.keyType === "string")
    assert(schema.valueType === "org.coursera.naptime.Person")
    assert(schema.mergedType === "org.coursera.naptime.PersonResource.Model")
    assert(schema.handlers.length === 13)
    assert(schema.handlers.filter(_.kind == HandlerKind.FINDER).map(_.name).toSet ===
      Set("byEmail", "byUsernameAndDomain", "complex", "complexWithDefault", "byUsernameSorted"))
    assert(schema.handlers.filter(_.kind == HandlerKind.ACTION).map(_.name).toSet ===
      Set("parameterless", "batchModify"))
    assert(schema.handlers.count(_.kind == HandlerKind.MULTI_GET) === 1)
    val multiGetSchema = schema.handlers.find(_.kind == HandlerKind.MULTI_GET).get
    assert(multiGetSchema.customOutputBody === None)
    assert(multiGetSchema.inputBody === None)
    assert(multiGetSchema.parameters.length === 1)
    val multiGetSetParameter = multiGetSchema.parameters.head
    assert(multiGetSetParameter.name === "ids")
    assert(multiGetSetParameter.`type` === "Set[String]") // TODO(saeta): Use a courier type.
    assert(multiGetSetParameter.typeSchema.get.data().getString("type") === "array")
    assert(multiGetSetParameter.typeSchema.get.data().getString("items") === "string")
    assert(multiGetSetParameter.default === None)

    // TODO(saeta): verify more things (i.e. more handlers)
  }

  @Test
  def typesTest(): Unit = {
    val types = PersonResource.routerBuilder.types
    assert(types.size === 3)
    val resourceType = types.find(_.key == "org.coursera.naptime.PersonResource.Model").getOrElse {
      assert(false, "Could not find merged type in types list.")
      ???
    }
    assert(!resourceType.value.hasError)
    assert(resourceType.value.isComplex)
    assert(resourceType.value.getType === DataSchema.Type.RECORD)
    assert(resourceType.value.isInstanceOf[RecordDataSchema])
    assert(resourceType.value.asInstanceOf[RecordDataSchema].getFields.size() === 3)
    assert(resourceType.value.asInstanceOf[RecordDataSchema].getFields.toList.map(_.getName).toSet ===
      Set("id", "name", "email"))
  }

  @Test
  def nestedSchema(): Unit = {
    val schema = FriendsResource.routerBuilder.schema
    assert(schema.className === "org.coursera.naptime.FriendsResource")
    assert(schema.kind === ResourceKind.COLLECTION)
    assert(schema.name === "friends")
    assert(schema.version === Some(1))
    assert(schema.keyType === "string")
    assert(schema.valueType === "org.coursera.naptime.FriendshipInfo")
    assert(schema.mergedType === "org.coursera.naptime.FriendsResource.Model")
    assert(schema.handlers.length === 11)
    assert(schema.handlers.filter(_.kind == HandlerKind.FINDER).map(_.name).toSet ===
      Set("byEmail", "byUsernameAndDomain", "withDefaults", "complex"))
    val withDefaultsSchema = schema.handlers.find(_.name == "withDefaults").get
    assert(withDefaultsSchema.parameters.length === 3)
    assert(withDefaultsSchema.parameters.map(_.name).toSet ===
      Set("ancestorKeys", "userName", "domain"))
    val domainSchema = withDefaultsSchema.parameters.find(_.name == "domain").get
    assert(StringMember(domainSchema.data.get("default").asInstanceOf[DataMap]).value === "defaultDomain")

    assert(schema.parentClass === Some(peopleInstanceImpl.getClass.getName))

    // TODO(saeta): verify more things here (i.e. the handlers).
  }

  @Test
  def nestedTypes(): Unit = {
    val types = FriendsResource.routerBuilder.types
    assert(types.size === 3)
    val resourceModel = types.find(_.key === "org.coursera.naptime.FriendsResource.Model").getOrElse {
      assert(false, "Could not find constructed merged type.")
      ???
    }
    assert(resourceModel.key === "org.coursera.naptime.FriendsResource.Model")
    assert(!resourceModel.value.hasError)
    assert(resourceModel.value.isComplex)
    assert(resourceModel.value.getType === DataSchema.Type.RECORD)
    assert(resourceModel.value.isInstanceOf[RecordDataSchema])
    assert(resourceModel.value.asInstanceOf[RecordDataSchema].getFields.size() === 3)
    assert(resourceModel.value.asInstanceOf[RecordDataSchema].getFields.toList.map(_.getName).toSet ===
      Set("id", "friendshipQuality", "friendsSince"))
  }

  // TODO(saeta): test the generated pegasus asymmetric body types for correctness.

  def shouldNotCompileTests(): Unit = {
    shapeless.test.illTyped("""
      class MyResource extends TopLevelCollectionResource[String, FriendshipInfo] {

        override def keyFormat: KeyFormat[KeyType] = KeyFormat.stringKeyFormat
        override implicit def resourceFormat: OFormat[FriendshipInfo] = FriendshipInfo.jsonFormat
        override def resourceName: String = "friends"
        implicit val fields = Fields.withDefaultFields("friendshipQuality")

        def get(id: PathKey, optPath: OptPathKey) = Nap.get { ctx => ??? }
      }
      object MyResource {
        val router = Router.build[MyResource]
      }""",
      "You cannot bind an OptPathKey in this context."
    )

    shapeless.test.illTyped("""
      class MyResource extends TopLevelCollectionResource[String, FriendshipInfo] {

        override def keyFormat: KeyFormat[KeyType] = KeyFormat.stringKeyFormat
        override implicit def resourceFormat: OFormat[FriendshipInfo] = FriendshipInfo.jsonFormat
        override def resourceName: String = "friends"
        implicit val fields = Fields.withDefaultFields("friendshipQuality")

        def getAll(pathKey: PathKey) = Nap.getAll { ctx => ??? }
      }
      object MyResource {
        val router = Router.build[MyResource]
      }""",
      "You cannot bind a PathKey in this context."
    )

    shapeless.test.illTyped("""
      class MyResource extends TopLevelCollectionResource[String, FriendshipInfo] {

        override def keyFormat: KeyFormat[KeyType] = KeyFormat.stringKeyFormat
        override implicit def resourceFormat: OFormat[FriendshipInfo] = FriendshipInfo.jsonFormat
        override def resourceName: String = "friends"
        implicit val fields = Fields.withDefaultFields("friendshipQuality")

        def myFinder(pathKey: PathKey) = Nap.finder { ctx => ??? }
      }
      object MyResource {
        val router = Router.build[MyResource]
      }""",
      "Cannot automatically bind parameter pathKey.*"
    )

    shapeless.test.illTyped("""
      class MyResource extends TopLevelCollectionResource[String, FriendshipInfo] {

        override def keyFormat: KeyFormat[KeyType] = KeyFormat.stringKeyFormat
        override implicit def resourceFormat: OFormat[FriendshipInfo] = FriendshipInfo.jsonFormat
        override def resourceName: String = "friends"
        implicit val fields = Fields.withDefaultFields("friendshipQuality")

        def getall(randomParameter: String) = Nap.getAll { ctx => ??? }
      }
      object MyResource {
        val router = Router.build[MyResource]
      }""",
      "Parameter randomParameter: String not allowed here. " +
        "Please see https://docs.dkandu.me/projects/naptime/advanced.html"
    )
  }
}
