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

package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.ari.TopLevelResponse
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.helpers.ArgumentBuilder
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import sangria.execution.DeprecationTracker
import sangria.execution.ExecutionPath
import sangria.marshalling.ResultMarshaller
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.Schema

import scala.collection.JavaConverters._

class NaptimePaginationFieldTest extends AssertionsForJUnit with MockitoSugar {

//  def createContext[Ctx, Val](
//      ctx: Ctx,
//      value: Val,
//      args: Map[String, Any] = Map.empty)
//      (implicit ctxManifest: Manifest[Ctx], valManifest: Manifest[Val]) = {
//    Context[Ctx, Val](
//      value = value,
//      ctx = ctx,
//      args = ArgumentBuilder.buildArgs(NaptimePaginationField.paginationArguments, args),
//      schema = mock[Schema[Ctx, Val]],
//      field = mock[Field[Ctx, Val]],
//      parentType = mock[ObjectType[Ctx, Any]],
//      marshaller = mock[ResultMarshaller],
//      sourceMapper = None,
//      deprecationTracker = DeprecationTracker.empty,
//      astFields = Vector.empty,
//      path = ExecutionPath.empty,
//      deferredResolverState = None)
//  }
//
//  val fieldName = "relatedIds"
//  val resourceName = ResourceName("courses", 1)
//  val resourceContext = SangriaGraphQlContext(Response(
//    Map(TopLevelRequest(
//      resourceName,
//      RequestField("", None, Set.empty, List.empty)) ->
//    TopLevelResponse(
//      ids = new DataList(List("1").asJava),
//      pagination = ResponsePagination(None))),
//    Map(resourceName -> Map("1" -> new DataMap()))), null, null)
//
//  @Test
//  def resolveNestedEmptyList(): Unit = {
//    val model = new DataMap(Map(fieldName -> new DataList(List.empty.asJava)).asJava)
//    val context = createContext(
//      resourceContext,
//      ParentContext(createContext(resourceContext, model)))
//    val resolver = NaptimePaginationField.getResolver(resourceName, fieldName)
//    val paginationData = resolver(context).value
//    assert(paginationData === ResponsePagination(None, Some(0)))
//  }
//
//  @Test
//  def resolveNestedWithLimit(): Unit = {
//    val model = new DataMap(Map(fieldName -> new DataList(List("1", "2").asJava)).asJava)
//    val context = createContext(
//      resourceContext,
//      ParentContext(createContext(resourceContext, model, Map("limit" -> Some(1)))))
//    val resolver = NaptimePaginationField.getResolver(resourceName, fieldName)
//    val paginationData = resolver(context).value
//    assert(paginationData === ResponsePagination(Some("2"), Some(2)))
//  }
//
//  @Test
//  def resolveNestedWithStart(): Unit = {
//    val model = new DataMap(Map(fieldName -> new DataList(List("1", "2", "3").asJava)).asJava)
//    val context = createContext(
//      resourceContext,
//      ParentContext(createContext(resourceContext, model, Map("limit" -> Some(1), "start" -> Some("2")))))
//    val resolver = NaptimePaginationField.getResolver(resourceName, fieldName)
//    val paginationData = resolver(context).value
//    assert(paginationData === ResponsePagination(Some("3"), Some(3)))
//  }
//
//  @Test
//  def resolveNestedWithNonExistentStart(): Unit = {
//    val model = new DataMap(Map(fieldName -> new DataList(List("1", "2", "3").asJava)).asJava)
//    val context = createContext(
//      resourceContext,
//      ParentContext(createContext(resourceContext, model, Map("limit" -> Some(1), "start" -> Some("4")))))
//    val resolver = NaptimePaginationField.getResolver(resourceName, fieldName)
//    val paginationData = resolver(context).value
//    assert(paginationData === ResponsePagination(None, Some(3)))
//  }
//
//  @Test
//  def resolveNestedWithNonStringStart(): Unit = {
//    val model = new DataMap(Map(fieldName -> new DataList(List(1, 2, 3).asJava)).asJava)
//    val context = createContext(
//      resourceContext,
//      ParentContext(createContext(resourceContext, model, Map("limit" -> Some(1), "start" -> Some("2")))))
//    val resolver = NaptimePaginationField.getResolver(resourceName, fieldName)
//    val paginationData = resolver(context).value
//    assert(paginationData === ResponsePagination(Some("3"), Some(3)))
//  }
//
//  @Test
//  def resolveTopLevel(): Unit = {
//    val context = createContext(
//      resourceContext,
//      ParentContext(createContext(resourceContext, new DataMap(), Map("limit" -> Some(1), "start" -> Some("4")))))
//    val resolver = NaptimePaginationField.getResolver(resourceName, fieldName)
//    val paginationData = resolver(context).value
//    assert(paginationData === resourceContext.response.topLevelResponses.values.head.pagination)
//  }
}
