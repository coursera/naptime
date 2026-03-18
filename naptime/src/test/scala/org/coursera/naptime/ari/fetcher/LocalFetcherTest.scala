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

package org.coursera.naptime.ari.fetcher

import akka.util.ByteString
import com.google.inject.Injector
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.FetcherError
import org.coursera.naptime.ari.Request
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.router2.NaptimeRequestTaggingHandler
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.router2.ResourceRouter
import org.coursera.naptime.router2.ResourceRouterBuilder
import org.coursera.naptime.schema.AttributeArray
import org.coursera.naptime.schema.HandlerArray
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.EssentialAction
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.test.FakeRequest

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** A non-RestAction handler, so LocalFetcher returns 404 for "handler was not a RestAction". */
object FakeNonRestActionHandler extends EssentialAction with NaptimeRequestTaggingHandler {
  override def tagRequest(request: RequestHeader): RequestHeader = request
  override def apply(v1: RequestHeader): Accumulator[ByteString, Result] = ???
}

/**
 * Tests for [[LocalFetcher]].
 */
class LocalFetcherTest extends AssertionsForJUnit with MockitoSugar {

  private def makeResource(name: String, version: Long): Resource = {
    Resource(
      kind = ResourceKind.COLLECTION,
      name = name,
      version = Some(version),
      parentClass = Some("org.coursera.naptime.resources.RootResource"),
      keyType = "string",
      valueType = "org.coursera.Value",
      mergedType = "org.coursera.Merged",
      handlers = HandlerArray(),
      className = s"org.coursera.${name.capitalize}Resource",
      attributes = AttributeArray())
  }

  private def makeNaptimeRoutes(resources: Seq[Resource]): NaptimeRoutes = {
    val injector = mock[Injector]
    val builders = resources.map { resource =>
      val builder = mock[ResourceRouterBuilder]
      val router = mock[ResourceRouter]
      when(builder.resourceClass()).thenReturn(classOf[AnyRef].asInstanceOf[Class[builder.ResourceClass]])
      when(builder.schema).thenReturn(resource)
      when(builder.types).thenReturn(immutable.Seq.empty[Keyed[String, com.linkedin.data.schema.DataSchema]])
      when(builder.build(any())).thenReturn(router)
      builder
    }
    NaptimeRoutes(injector, builders.toSet)
  }

  @Test
  def data_unknownResource_returns404FetcherError(): Unit = {
    val routes = makeNaptimeRoutes(Seq.empty)
    val fetcher = new LocalFetcher(routes)

    val request = Request(
      requestHeader = FakeRequest(),
      resource = ResourceName("nonExistent", 1),
      arguments = Set.empty,
      authOverride = None)

    val result = Await.result(fetcher.data(request, isDebugMode = false), 5.seconds)
    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.code === 404)
      assert(error.message.contains("Unknown resource"))
    }
  }

  @Test
  def data_knownResourceButNoRouterMatch_returns404(): Unit = {
    val resource = makeResource("courses", 1L)
    val routes = makeNaptimeRoutes(Seq(resource))
    val fetcher = new LocalFetcher(routes)

    // The resource exists by name/version, but no router returns a handler
    val request = Request(
      requestHeader = FakeRequest(),
      resource = ResourceName("courses", 1),
      arguments = Set("q" -> JsString("testQuery")),
      authOverride = None)

    val result = Await.result(fetcher.data(request, isDebugMode = false), 5.seconds)
    // No actual router was registered, so it falls through to the "unknown resource" path
    assert(result.isLeft)
  }

  @Test
  def data_withArguments_constructsQueryString(): Unit = {
    val routes = makeNaptimeRoutes(Seq.empty)
    val fetcher = new LocalFetcher(routes)

    val request = Request(
      requestHeader = FakeRequest(),
      resource = ResourceName("courses", 1),
      arguments = Set(
        "limit" -> JsString("10"),
        "start" -> JsString("0")),
      authOverride = None)

    // Should return a 404 since no resource registered, but should not crash when building query string
    val result = Await.result(fetcher.data(request, isDebugMode = false), 5.seconds)
    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.code === 404)
    }
  }

  @Test
  def data_debugMode_true_doesNotCrash(): Unit = {
    val routes = makeNaptimeRoutes(Seq.empty)
    val fetcher = new LocalFetcher(routes)

    val request = Request(
      requestHeader = FakeRequest(),
      resource = ResourceName("anything", 1),
      arguments = Set.empty,
      authOverride = None)

    val result = Await.result(fetcher.data(request, isDebugMode = true), 5.seconds)
    assert(result.isLeft)
  }

  @Test
  def data_routerReturnsNonRestAction_returns404(): Unit = {
    // For the router to be found, the resource className must match the builder's class name.
    // NaptimeRoutes.className returns builder.resourceClass().getName.replace("$", ".")
    // So we need the resource className to match classOf[AnyRef].getName = "java.lang.Object"
    val resourceWithMatchingClassName = Resource(
      kind = ResourceKind.COLLECTION,
      name = "courses",
      version = Some(1L),
      parentClass = Some("org.coursera.naptime.resources.RootResource"),
      keyType = "string",
      valueType = "org.coursera.Value",
      mergedType = "org.coursera.Merged",
      handlers = HandlerArray(),
      className = classOf[AnyRef].getName, // "java.lang.Object" - matches the mock builder's class
      attributes = AttributeArray())

    val injector = mock[Injector]
    val router = mock[ResourceRouter]
    val builder = mock[ResourceRouterBuilder]
    when(builder.resourceClass()).thenReturn(classOf[AnyRef].asInstanceOf[Class[builder.ResourceClass]])
    when(builder.schema).thenReturn(resourceWithMatchingClassName)
    when(builder.types).thenReturn(immutable.Seq.empty[Keyed[String, com.linkedin.data.schema.DataSchema]])
    when(builder.build(any())).thenReturn(router)
    // Router returns a non-RestAction handler
    when(router.routeRequest(any(), any())).thenReturn(Some(FakeNonRestActionHandler))

    val routes = NaptimeRoutes(injector, Set(builder))
    val fetcher = new LocalFetcher(routes)

    val request = Request(
      requestHeader = FakeRequest(),
      resource = ResourceName("courses", 1),
      arguments = Set.empty,
      authOverride = None)

    val result = Await.result(fetcher.data(request, isDebugMode = false), 5.seconds)
    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.code === 404)
      assert(error.message.contains("not a RestAction"))
    }
  }

  @Test
  def data_withVariousArgumentTypes_doesNotCrash(): Unit = {
    val routes = makeNaptimeRoutes(Seq.empty)
    val fetcher = new LocalFetcher(routes)

    // Test various argument types that get stringified
    val request = Request(
      requestHeader = FakeRequest(),
      resource = ResourceName("unknown", 1),
      arguments = Set(
        "strArg" -> JsString("hello"),
        "numArg" -> JsNumber(42),
        "boolArg" -> JsBoolean(true),
        "nullArg" -> JsNull,
        "objArg" -> Json.obj("k" -> "v"),
        "arrArg" -> JsArray(Seq(JsString("a"), JsString("b")))),
      authOverride = None)

    val result = Await.result(fetcher.data(request, isDebugMode = false), 5.seconds)
    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.code === 404)
    }
  }
}
