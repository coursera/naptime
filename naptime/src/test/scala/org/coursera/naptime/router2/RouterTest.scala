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

import com.google.inject.Injector
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._
import play.api.test.FakeRequest

object RouterTest {
  class Resource1
  class Resource2
  class Resource3

  abstract class ResourceRouterBuilder1 extends ResourceRouterBuilder {
    type ResourceType = Resource1
  }
  abstract class ResourceRouterBuilder2 extends ResourceRouterBuilder {
    type ResourceType = Resource2
  }
  abstract class ResourceRouterBuilder3 extends ResourceRouterBuilder {
    type ResourceType = Resource3
  }

  abstract class ResourceRouter1 extends ResourceRouter {
    type ResourceType = Resource1
  }
  abstract class ResourceRouter2 extends ResourceRouter {
    type ResourceType = Resource2
  }
  abstract class ResourceRouter3 extends ResourceRouter {
    type ResourceType = Resource3
  }
}

class RouterTest extends AssertionsForJUnit with MockitoSugar {
  import RouterTest._

  val resourceRouterBuilder1 = mock[ResourceRouterBuilder1]
  val resourceRouterBuilder2 = mock[ResourceRouterBuilder2]
  val resourceRouterBuilder3 = mock[ResourceRouterBuilder3]
  val resourceRouter1 = mock[ResourceRouter1]
  val resourceRouter2 = mock[ResourceRouter2]
  val resourceRouter3 = mock[ResourceRouter3]
  val resources = List(resourceRouterBuilder1, resourceRouterBuilder2, resourceRouterBuilder3)
  val injector = mock[Injector]
  setupStandardMocks()
  val router = new Router(injector, resources)

  private[this] def setupStandardMocks(): Unit = {
    when(resourceRouterBuilder1.build(any())).thenReturn(resourceRouter1)
    when(resourceRouterBuilder2.build(any())).thenReturn(resourceRouter2)
    when(resourceRouterBuilder3.build(any())).thenReturn(resourceRouter3)
  }
  val fakeRequest = FakeRequest("GET", "/api/foo.v1")

  @Test
  def simpleRouting(): Unit = {
    when(resourceRouter1.routeRequest(any(), any())).thenReturn(Some(null))
    val result = router.onRouteRequest(fakeRequest)
    assert(result.isDefined, "Expected result to be defined.")
    verifyZeroInteractions(resourceRouter2, resourceRouter3)
  }

  @Test
  def stopImmediately(): Unit = {
    when(resourceRouter1.routeRequest(any(), any())).thenReturn(None)
    when(resourceRouter2.routeRequest(any(), any())).thenReturn(Some(null))
    val result = router.onRouteRequest(fakeRequest)
    assert(result.isDefined, "Expected result to be defined.")
    verifyZeroInteractions(resourceRouter3)
  }

  @Test
  def handleNoMatchingRequests(): Unit = {
    when(resourceRouter1.routeRequest(any(), any())).thenReturn(None)
    when(resourceRouter2.routeRequest(any(), any())).thenReturn(None)
    when(resourceRouter3.routeRequest(any(), any())).thenReturn(None)
    val result = router.onRouteRequest(fakeRequest)
    assert(result.isEmpty, "Expected result to be empty.")
  }
}
