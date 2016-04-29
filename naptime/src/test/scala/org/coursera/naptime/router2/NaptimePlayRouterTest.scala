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
import org.mockito.Mockito.when
import org.mockito.Matchers.any
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import play.api.libs.iteratee.Iteratee
import play.api.mvc.EssentialAction
import play.api.mvc.RequestHeader
import play.api.mvc.RequestTaggingHandler
import play.api.mvc.Result
import play.api.test.FakeRequest

class NaptimePlayRouterTest extends AssertionsForJUnit with MockitoSugar {
  object FakeHandler extends /* RouteAction */ EssentialAction with RequestTaggingHandler {
    override def tagRequest(request: RequestHeader): RequestHeader = request

    override def apply(v1: RequestHeader): Iteratee[Array[Byte], Result] = ???
  }

  val resourceRouter = mock[ResourceRouter]
  val resourceRouterBuilder = mock[ResourceRouterBuilder]
  when(resourceRouterBuilder.build(any())).thenReturn(resourceRouter)

  val injector = mock[Injector]
  val router = new NaptimePlayRouter(injector, Set(resourceRouterBuilder))

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
}
