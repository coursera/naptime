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

import akka.util.ByteString
import com.google.inject.Injector
import org.coursera.naptime.resources.RootResource
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.Parameter
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind
import org.junit.Test
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchers.any
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.streams.Accumulator
import play.api.mvc.EssentialAction
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.test.FakeRequest

class NaptimePlayRouterTest extends AssertionsForJUnit with MockitoSugar {
  object FakeHandler extends /* RouteAction */ EssentialAction with NaptimeRequestTaggingHandler {
    override def tagRequest(request: RequestHeader): RequestHeader = request

    override def apply(v1: RequestHeader): Accumulator[ByteString, Result] = ???
  }

  val resourceSchema = Resource(
    kind = ResourceKind.COLLECTION,
    name = "fakeResource",
    version = Some(1L),
    parentClass = Some(classOf[RootResource].getName),
    keyType = "java.lang.String",
    valueType = "FakeModel",
    mergedType = "FakeResourceModel",
    handlers = List(
      Handler(
        kind = HandlerKind.GET,
        name = "get",
        parameters =
          List(Parameter(name = "id", `type` = "String", attributes = List.empty, default = None)),
        inputBodyType = None,
        customOutputBodyType = None,
        attributes = List.empty)),
    className = "org.coursera.naptime.FakeResource",
    attributes = List.empty)

  val resourceRouter = mock[ResourceRouter]
  val resourceRouterBuilder = mock[ResourceRouterBuilder]
  when(resourceRouterBuilder.build(any())).thenReturn(resourceRouter)
  when(resourceRouterBuilder.schema).thenReturn(resourceSchema)

  val injector = mock[Injector]
  val naptimeRoutes = NaptimeRoutes(injector, Set(resourceRouterBuilder))
  val router = new NaptimePlayRouter(naptimeRoutes)

  @Test
  def simpleRouting(): Unit = {
    when(resourceRouter.routeRequest(any(), any())).thenReturn(Some(FakeHandler))
    val handler = router.handlerFor(FakeRequest())
    assert(handler.isDefined)
  }

  @Test
  def simpleRoutingNothing(): Unit = {
    when(resourceRouter.routeRequest(any(), any())).thenReturn(None)
    val handler = router.handlerFor(FakeRequest())
    assert(handler.isEmpty)
  }

  @Test
  def generateDocumentation(): Unit = {
    val documentation = router.documentation
    assert(1 === documentation.length)
    assert(
      (
        "GET --- GET",
        "/fakeResource.v1/$id",
        "[NAPTIME] org.coursera.naptime.FakeResource.get(id: String)") ===
        documentation.head)
  }

  @Test
  def withPrefix_returnsNewRouterWithPrefix(): Unit = {
    val prefixed = router.withPrefix("/api")
    assert(prefixed != null)
    // Verify that the prefixed router does not route a request to a path that
    // does NOT start with the prefix.
    when(resourceRouter.routeRequest(any(), any())).thenReturn(Some(FakeHandler))
    val request = FakeRequest("GET", "/other/fakeResource.v1/someId")
    val handler = prefixed.handlerFor(request)
    assert(handler.isEmpty, "Prefixed router should not match a path outside its prefix")
  }

  @Test
  def handlerFor_pathOutsidePrefix_returnsNone(): Unit = {
    val prefixed = router.withPrefix("/api")
    when(resourceRouter.routeRequest(any(), any())).thenReturn(Some(FakeHandler))
    // Path does not start with "/api"
    val request = FakeRequest("GET", "/unrelated/path")
    val handler = prefixed.handlerFor(request)
    assert(handler.isEmpty)
  }

  @Test
  def naptimeRoutes_className_replacesDollarSign(): Unit = {
    // Verify that className strips '$' from inner class names
    val mockBuilder = mock[ResourceRouterBuilder]
    when(mockBuilder.resourceClass()).thenReturn(
      classOf[NaptimePlayRouterTest.InnerClass].asInstanceOf[Class[mockBuilder.ResourceClass]])
    val name = naptimeRoutes.className(mockBuilder)
    assert(!name.contains("$"), s"className should not contain '$$': $name")
  }
}

object NaptimePlayRouterTest {
  // A nested class whose JVM name contains a '$'
  class InnerClass
}
