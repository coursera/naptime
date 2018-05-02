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
import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.naptime.ResourceTestImplicits
import org.coursera.naptime.actions.NaptimeActionSerializer.AnyWrites._
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.path.:::
import org.coursera.naptime.path.ParseSuccess
import org.coursera.naptime.path.RootParsedPathKey
import org.coursera.naptime.resources.CollectionResource
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.junit.Test
import org.mockito.Matchers.any
import org.mockito.Matchers.{eq => e}
import org.mockito.Mockito.when
import org.mockito.Mockito.verify
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext

object NestingCollectionResourceRouterTest {
  case class Person(name: String)
  object Person {
    implicit val jsonFormat: OFormat[Person] = Json.format[Person]
  }

  /**
   * A sample top-level resource, with very standard / normal Naptime operations.
   */
  class MyResource(implicit val executionContext: ExecutionContext, val materializer: Materializer)
      extends TopLevelCollectionResource[String, Person] {
    override def keyFormat: KeyFormat[KeyType] = KeyFormat.stringKeyFormat
    override implicit def resourceFormat: OFormat[Person] = Person.jsonFormat
    override def resourceName: String = "myResource"
    implicit val fields = Fields
    def get(id: String) = Nap.get { ctx =>
      ???
    }

    def multiGet(ids: Set[String]) = Nap.multiGet { ctx =>
      ???
    }

    def getAll = Nap.getAll { ctx =>
      ???
    }

    def me = Nap.finder { ctx =>
      ???
    }

    def create = Nap.create { ctx =>
      ???
    }

    def delete(id: String) = Nap.delete { ctx =>
      ???
    }

    def update(id: String) = Nap.update { ctx =>
      ???
    }

    def patch(id: String) = Nap.patch { ctx =>
      ???
    }

    def myAwesomeAction = Nap.action { ctx =>
      ???
    }
  }

  /**
   * A hand-written implementation of a router. Normally, a macro is used to generate this binding.
   */
  class MyResourceRouter(myResource: MyResource)
      extends NestingCollectionResourceRouter(myResource) {

    override def executeGet(
        requestHeader: RequestHeader,
        pathKey: resourceInstance.PathKey): RouteAction = {
      resourceInstance.get(pathKey.head).setTags(mkRequestTags("get"))
    }

    override def executeGetAll(
        requestHeader: RequestHeader,
        optPathKey: resourceInstance.OptPathKey): RouteAction = {
      resourceInstance.getAll.setTags(mkRequestTags("getAll"))
    }

    override def executeMultiGet(
        requestHeader: RequestHeader,
        optPathKey: resourceInstance.OptPathKey,
        ids: Set[resourceInstance.KeyType]): RouteAction = {
      resourceInstance.multiGet(ids).setTags(mkRequestTags("multiGet"))
    }

    override def executeFinder(
        requestHeader: RequestHeader,
        optPathKey: resourceInstance.OptPathKey,
        finderName: String): RouteAction = {
      finderName match {
        case "me" =>
          resourceInstance.me.setTags(mkRequestTags("me"))
        case _ => super.executeFinder(requestHeader, optPathKey, finderName)
      }
    }

    override def executeCreate(
        requestHeader: RequestHeader,
        optPathKey: resourceInstance.OptPathKey): RouteAction = {
      resourceInstance.create.setTags(mkRequestTags("create"))
    }

    override def executePut(
        requestHeader: RequestHeader,
        pathKey: resourceInstance.PathKey): RouteAction = {
      val x = resourceInstance.update(pathKey.head)
      x.setTags(mkRequestTags("update"))
    }

    override def executeDelete(
        requestHeader: RequestHeader,
        pathKey: resourceInstance.PathKey): RouteAction = {
      resourceInstance.delete(pathKey.head).setTags(mkRequestTags("delete"))
    }

    override def executePatch(
        requestHeader: RequestHeader,
        pathKey: resourceInstance.PathKey): RouteAction = {
      resourceInstance.patch(pathKey.head).setTags(mkRequestTags("patch"))
    }

    override def executeAction(
        requestHeader: RequestHeader,
        optPathKey: resourceInstance.OptPathKey,
        actionName: String): RouteAction = {
      actionName match {
        case "myAwesomeAction" =>
          resourceInstance.myAwesomeAction.setTags(mkRequestTags("myAwesomeAction"))
        case _ => super.executeAction(requestHeader, optPathKey, actionName)
      }
    }
  }

  /**
   * A nested resource that has more sophisticated parameters to its Naptime operations to test the
   * capabilities of the routing system.
   */
  class MyNestedResource(val parentResource: MyResource)(
      implicit val executionContext: ExecutionContext,
      val materializer: Materializer)
      extends CollectionResource[MyResource, String, Person] {

    override def keyFormat: KeyFormat[KeyType] = KeyFormat.stringKeyFormat
    override implicit def resourceFormat: OFormat[Person] = Person.jsonFormat
    override def resourceName: String = "myNestedResource"
    implicit val fields = Fields

    val ANCESTOR_KEY: AncestorKeys = ("parentKey" ::: RootParsedPathKey).asInstanceOf[AncestorKeys]
    val PATH_KEY: PathKey = ("resourceId" ::: ANCESTOR_KEY).asInstanceOf[PathKey]

    def get(id: String, parentKeys: AncestorKeys) = Nap.get { ctx =>
      ???
    }

    def multiGet(ids: Set[String], parentKeys: AncestorKeys) = Nap.multiGet { ctx =>
      ???
    }

    def getAll(parentKeys: AncestorKeys) = Nap.getAll { ctx =>
      ???
    }

    def me = Nap.finder { ctx =>
      ???
    }

    def create(parentKeys: AncestorKeys) = Nap.create { ctx =>
      ???
    }

    def delete(id: String, pathKey: PathKey) = Nap.delete { ctx =>
      ???
    }

    def update(pathKey: PathKey) = Nap.update { ctx =>
      ???
    }

    def patch(id: String, parentKeys: AncestorKeys) = Nap.patch { ctx =>
      ???
    }

    def myAwesomeAction = Nap.action { ctx =>
      ???
    }
  }

  /**
   * A hand-written implementation of a router. Normally, a macro is used to generate this binding.
   */
  class MyNestedResourceRouter(myNestedResource: MyNestedResource)
      extends NestingCollectionResourceRouter(myNestedResource) {
    override def executeGet(
        requestHeader: RequestHeader,
        pathKey: resourceInstance.PathKey): RouteAction = {
      resourceInstance.get(pathKey.head, pathKey.tail).setTags(mkRequestTags("get"))
    }

    override def executeMultiGet(
        requestHeader: RequestHeader,
        optPathKey: resourceInstance.OptPathKey,
        ids: Set[resourceInstance.KeyType]): RouteAction = {
      resourceInstance.multiGet(ids, optPathKey.tail).setTags(mkRequestTags("multiGet"))
    }

    override def executeGetAll(
        requestHeader: RequestHeader,
        optPathKey: resourceInstance.OptPathKey): RouteAction = {
      resourceInstance.getAll(optPathKey.tail).setTags(mkRequestTags("getAll"))
    }

    override def executeFinder(
        requestHeader: RequestHeader,
        optPathKey: resourceInstance.OptPathKey,
        finderName: String): RouteAction = {
      finderName match {
        case "me" =>
          resourceInstance.me.setTags(mkRequestTags("me"))
        case _ =>
          super.executeFinder(requestHeader, optPathKey, finderName)
      }
    }

    override def executeCreate(
        requestHeader: RequestHeader,
        optPathKey: resourceInstance.OptPathKey): RouteAction = {
      resourceInstance.create(optPathKey.tail).setTags(mkRequestTags("create"))
    }

    override def executePut(
        requestHeader: RequestHeader,
        pathKey: resourceInstance.PathKey): RouteAction = {
      resourceInstance.update(pathKey).setTags(mkRequestTags("update"))
    }

    override def executeDelete(
        requestHeader: RequestHeader,
        pathKey: resourceInstance.PathKey): RouteAction = {
      resourceInstance.delete(pathKey.h, pathKey).setTags(mkRequestTags("delete"))
    }

    override def executePatch(
        requestHeader: RequestHeader,
        pathKey: resourceInstance.PathKey): RouteAction = {
      resourceInstance.patch(pathKey.head, pathKey.tail).setTags(mkRequestTags("patch"))
    }

    override def executeAction(
        requestHeader: RequestHeader,
        optPathKey: resourceInstance.OptPathKey,
        actionName: String): RouteAction = {
      actionName match {
        case "myAwesomeAction" =>
          resourceInstance.myAwesomeAction.setTags(mkRequestTags("myAwesomeAction"))
        case _ => super.executeAction(requestHeader, optPathKey, actionName)
      }
    }
  }
}

class NestingCollectionResourceRouterTest
    extends AssertionsForJUnit
    with MockitoSugar
    with ResourceTestImplicits {
  import NestingCollectionResourceRouterTest._

  val resourceInstance = new MyResource
  val mockResource = mock[MyResource]
  when(mockResource.keyFormat).thenReturn(resourceInstance.keyFormat)
  when(mockResource.get(any())).thenReturn(resourceInstance.get("someId"))
  when(mockResource.multiGet(any())).thenReturn(resourceInstance.multiGet(Set("id1", "id2")))
  when(mockResource.getAll).thenReturn(resourceInstance.getAll)
  when(mockResource.create).thenReturn(resourceInstance.create)
  when(mockResource.delete(any())).thenReturn(resourceInstance.delete("someId"))
  when(mockResource.update(any())).thenReturn(resourceInstance.update("asdf"))
  when(mockResource.patch(any())).thenReturn(resourceInstance.patch("fdsa"))
  when(mockResource.me).thenReturn(resourceInstance.me)
  when(mockResource.myAwesomeAction).thenReturn(resourceInstance.myAwesomeAction)
  val router = new MyResourceRouter(mockResource)

  val subResourceInstance = new MyNestedResource(resourceInstance)
  val mockSubResource = mock[MyNestedResource]
  when(mockSubResource.keyFormat).thenReturn(subResourceInstance.keyFormat)
  when(mockSubResource.get(any(), any()))
    .thenReturn(subResourceInstance.get("fooId", subResourceInstance.ANCESTOR_KEY))
  when(mockSubResource.getAll(any()))
    .thenReturn(subResourceInstance.getAll(subResourceInstance.ANCESTOR_KEY))
  when(mockSubResource.multiGet(any(), any()))
    .thenReturn(subResourceInstance.multiGet(Set("id1", "id2"), subResourceInstance.ANCESTOR_KEY))
  when(mockSubResource.create(any()))
    .thenReturn(subResourceInstance.create(subResourceInstance.ANCESTOR_KEY))
  when(mockSubResource.update(any()))
    .thenReturn(subResourceInstance.update(subResourceInstance.PATH_KEY))
  when(mockSubResource.delete(any(), any()))
    .thenReturn(subResourceInstance.delete("someId", subResourceInstance.PATH_KEY))
  when(mockSubResource.patch(any(), any()))
    .thenReturn(subResourceInstance.patch("fakeId", subResourceInstance.ANCESTOR_KEY))
  when(mockSubResource.me).thenReturn(subResourceInstance.me)
  when(mockSubResource.myAwesomeAction).thenReturn(subResourceInstance.myAwesomeAction)
  val subRouter = new MyNestedResourceRouter(mockSubResource)

  /**
   * Builds the path, and also sets up the [[mockResource]] to correctly handle the parsing.
   */
  private[this] def buildPath(
      id: String = null,
      queryParams: Map[String, String] = Map.empty): String = {

    val resourcePath = s"/${resourceInstance.resourceName}.v${resourceInstance.resourceVersion}"
    val subPath = Option(id)
      .map { id =>
        val p = s"$resourcePath/$id"
        when(mockResource.optParse(p)).thenReturn(
          ParseSuccess(
            None,
            (Some(id) ::: RootParsedPathKey).asInstanceOf[mockResource.OptPathKey]))
        p
      }
      .getOrElse {
        when(mockResource.optParse(resourcePath)).thenReturn(
          ParseSuccess(None, (None ::: RootParsedPathKey).asInstanceOf[mockResource.OptPathKey]))
        resourcePath
      }
    val queryStr = if (queryParams.isEmpty) {
      ""
    } else {
      queryParams.toSeq
        .map {
          case (key, value) =>
            s"$key=$value"
        }
        .mkString("?", "&", "")
    }
    s"/api$subPath$queryStr"
  }

  /**
   * Builds the path, and also sets up the [[mockSubResource]] to correctly handle parsing.
   */
  private[this] def buildSubPath(
      id: String = null,
      queryParams: Map[String, String] = Map.empty,
      parentId: String = "parentId"): String = {
    val resourcePath = s"/${resourceInstance.resourceName}.v${resourceInstance.resourceVersion}/" +
      s"$parentId/${subResourceInstance.resourceName}.v${subResourceInstance.resourceVersion}"
    val subPath = Option(id)
      .map { id =>
        val p = s"$resourcePath/$id"
        when(mockSubResource.optParse(p)).thenReturn(
          ParseSuccess(
            None,
            (Some(id) ::: parentId ::: RootParsedPathKey).asInstanceOf[mockSubResource.OptPathKey]))
        p
      }
      .getOrElse {
        when(mockSubResource.optParse(resourcePath)).thenReturn(
          ParseSuccess(
            None,
            (None ::: parentId ::: RootParsedPathKey).asInstanceOf[mockSubResource.OptPathKey]))
        resourcePath
      }
    val queryStr = if (queryParams.isEmpty) {
      ""
    } else {
      queryParams.toSeq
        .map {
          case (key, value) =>
            s"$key=$value"
        }
        .mkString("?", "&", "")
    }
    s"/api$subPath$queryStr"
  }

  def convertToAncestorKey(key: String ::: RootParsedPathKey): mockSubResource.AncestorKeys = {
    key.asInstanceOf[mockSubResource.AncestorKeys]
  }

  private[this] def route(method: String, path: String, expectedMethodName: String = null): Unit = {
    val request = FakeRequest(method, path)
    assert(request.path.startsWith("/api"))
    val routePath = request.path.substring("/api".length)
    val result = router.routeRequest(routePath, request)
    assert(result.isDefined, s"Router did not correctly route $request")
    val taggedRequest = result.get.tagRequest(request)
    assert(
      taggedRequest.tags.contains(Router.NAPTIME_RESOURCE_NAME),
      "Router result (typically a RestAction) did not tag the request properly.")
    Option(expectedMethodName).foreach { methodName =>
      assert(
        taggedRequest.tags.get(Router.NAPTIME_METHOD_NAME).contains(methodName),
        "method names did not match.")
    }
  }

  private[this] def subRoute(
      method: String,
      path: String,
      expectedMethodName: String = null): Unit = {
    val request = FakeRequest(method, path)
    assert(request.path.startsWith("/api"))
    val routePath = request.path.substring("/api".length)
    val result = subRouter.routeRequest(routePath, request)
    assert(result.isDefined, s"Router did not correctly route $request")
    val taggedRequest = result.get.tagRequest(request)
    assert(
      taggedRequest.tags.contains(Router.NAPTIME_RESOURCE_NAME),
      "Router result (typically a RestAction) did not tag the request properly.")
    Option(expectedMethodName).foreach { methodName =>
      assert(
        taggedRequest.tags.get(Router.NAPTIME_METHOD_NAME).contains(methodName),
        "method names did not match.")
    }
  }

  @Test
  def testGet(): Unit = {
    route("GET", buildPath("personId1"))
    verify(mockResource).get("personId1")
  }

  @Test
  def testMultiGet(): Unit = {
    route("GET", buildPath(queryParams = Map("ids" -> "a,b,c")))
    verify(mockResource).multiGet(Set("a", "b", "c"))
  }

  @Test
  def testGetAll(): Unit = {
    route("GET", buildPath())
    verify(mockResource).getAll
  }

  @Test
  def testFinder(): Unit = {
    route("GET", buildPath(queryParams = Map("q" -> "me")))
    verify(mockResource).me
  }

  @Test
  def testCreate(): Unit = {
    route("POST", buildPath())
    verify(mockResource).create
  }

  @Test
  def testUpdate(): Unit = {
    route("PUT", buildPath("resourceId"))
    verify(mockResource).update("resourceId")
  }

  @Test
  def testDelete(): Unit = {
    route("DELETE", buildPath("myResourceId1111"))
    verify(mockResource).delete("myResourceId1111")
  }

  @Test
  def testPatch(): Unit = {
    route("PATCH", buildPath("foo"))
    verify(mockResource).patch("foo")
  }

  @Test
  def testAction(): Unit = {
    route("POST", buildPath(queryParams = Map("action" -> "myAwesomeAction")))
    verify(mockResource).myAwesomeAction
  }

  // Note for below:
  // Mockito and path dependent types interact poorly. We cannot easily construct an instance of
  // the correct path key for mockSubResource.AncestorKeys, and thus use `any()` as a workaround.
  // This is not usually an issue, as general Naptime users do not need to mock their own resources,
  // but rather will be testing their own resources.

  @Test
  def testSubGet(): Unit = {
    subRoute("GET", buildSubPath("personId1"))
    verify(mockSubResource).get(e("personId1"), any())
  }

  @Test
  def testSubMultiGet(): Unit = {
    subRoute("GET", buildSubPath(queryParams = Map("ids" -> "p1,p2,p3")))
    verify(mockSubResource).multiGet(e(Set("p1", "p2", "p3")), any())
  }

  @Test
  def testSubGetAll(): Unit = {
    subRoute("GET", buildSubPath())
    verify(mockSubResource).getAll(any())
  }

  @Test
  def testSubFinder(): Unit = {
    subRoute("GET", buildSubPath(queryParams = Map("q" -> "me")))
    verify(mockSubResource).me
  }

  @Test
  def testSubCreate(): Unit = {
    subRoute("POST", buildSubPath())
    verify(mockSubResource).create(any())
  }

  @Test
  def testSubUpdate(): Unit = {
    subRoute("PUT", buildSubPath("resourceId"))
    verify(mockSubResource).update(any())
  }

  @Test
  def testSubDelete(): Unit = {
    subRoute("DELETE", buildSubPath("myResourceId1111"))
    verify(mockSubResource).delete(e("myResourceId1111"), any())
  }

  @Test
  def testSubPatch(): Unit = {
    subRoute("PATCH", buildSubPath("foo"))
    verify(mockSubResource).patch(e("foo"), any())
  }

  @Test
  def testSubAction(): Unit = {
    subRoute("POST", buildSubPath(queryParams = Map("action" -> "myAwesomeAction")))
    verify(mockSubResource).myAwesomeAction
  }

  @Test
  def checkParseIds(): Unit = {
    assert(
      Right(Set("a", "b", "c")) ===
        router.parseIds[String]("a,b,c", StringKeyFormat.stringFormat))
    // Note: including the slashes in the output might not be the right answer.
    assert(
      Right(Set("a\\,b\\,c")) ===
        router.parseIds[String]("a\\,b\\,c", StringKeyFormat.stringFormat))
    assert(
      Right(Set("a\\,b", "c")) ===
        router.parseIds[String]("a\\,b,c", StringKeyFormat.stringFormat))
  }
}
