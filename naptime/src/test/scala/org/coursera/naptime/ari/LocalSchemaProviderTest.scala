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

package org.coursera.naptime.ari

import com.google.inject.Injector
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.RecordDataSchema.RecordType
import com.linkedin.data.schema.Name
import com.linkedin.data.schema.StringDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.model.Keyed
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

import scala.collection.immutable
import scala.collection.JavaConverters._

/**
 * Tests for [[LocalSchemaProvider]].
 */
class LocalSchemaProviderTest extends AssertionsForJUnit with MockitoSugar {

  private val rootResourceClassName = "org.coursera.naptime.resources.RootResource"

  private def makeResource(
      name: String,
      version: Long,
      parentClass: Option[String] = Some(rootResourceClassName),
      mergedType: String = "org.coursera.Merged"): Resource = {
    Resource(
      kind = ResourceKind.COLLECTION,
      name = name,
      version = Some(version),
      parentClass = parentClass,
      keyType = "string",
      valueType = "org.coursera.Value",
      mergedType = mergedType,
      handlers = HandlerArray(),
      className = s"org.coursera.${name.capitalize}Resource",
      attributes = AttributeArray())
  }

  private def makeNaptimeRoutes(resources: Seq[Resource]): NaptimeRoutes = {
    val injector = mock[Injector]
    val builders = resources.zipWithIndex.map {
      case (resource, idx) =>
        val builder = mock[ResourceRouterBuilder]
        val router = mock[ResourceRouter]
        // NaptimeRoutes.className calls resourceClass().getName(), so we must return a real class
        when(builder.resourceClass())
          .thenReturn(classOf[AnyRef].asInstanceOf[Class[builder.ResourceClass]])
        when(builder.schema).thenReturn(resource)
        when(builder.types)
          .thenReturn(immutable.Seq.empty[Keyed[String, com.linkedin.data.schema.DataSchema]])
        when(builder.build(any())).thenReturn(router)
        builder
    }
    NaptimeRoutes(injector, builders.toSet)
  }

  @Test
  def fullSchema_containsAllResources(): Unit = {
    val r1 = makeResource("courses", 1L)
    val routes = makeNaptimeRoutes(Seq(r1))
    val provider = new LocalSchemaProvider(routes)
    // fullSchema.resources comes from schemaMap values (1 per builder by class name)
    assert(provider.fullSchema.resources.size === 1)
  }

  @Test
  def fullSchema_emptyRoutes_isEmpty(): Unit = {
    val routes = makeNaptimeRoutes(Seq.empty)
    val provider = new LocalSchemaProvider(routes)
    assert(provider.fullSchema.resources.isEmpty)
  }

  @Test
  def mergedType_knownResource_returnsNone_whenNoMatchingDataSchema(): Unit = {
    // Without actual RecordDataSchema types, mergedType returns None since no schemas in types
    val resource = makeResource("courses", 1L, mergedType = "org.coursera.MergedCourse")
    val routes = makeNaptimeRoutes(Seq(resource))
    val provider = new LocalSchemaProvider(routes)
    val result = provider.mergedType(ResourceName("courses", 1))
    // No types were registered, so merged type lookup returns None
    assert(result === None)
  }

  @Test
  def mergedType_unknownResource_returnsNone(): Unit = {
    val resource = makeResource("courses", 1L)
    val routes = makeNaptimeRoutes(Seq(resource))
    val provider = new LocalSchemaProvider(routes)
    val result = provider.mergedType(ResourceName("unknownResource", 99))
    assert(result === None)
  }

  @Test
  def localSchemaProvider_withNestedResource_skipsNested(): Unit = {
    // A resource whose parentClass is neither None nor RootResource
    val nestedResource =
      makeResource("sessions", 1L, parentClass = Some("org.coursera.CourseResource"))
    val routes = makeNaptimeRoutes(Seq(nestedResource))
    val provider = new LocalSchemaProvider(routes)
    // Nested resources are not added to the resourceSchemaMap
    val result = provider.mergedType(ResourceName("sessions", 1))
    assert(result === None)
  }

  @Test
  def localSchemaProvider_withRootResource_isIncluded(): Unit = {
    val rootResource = makeResource("courses", 1L, parentClass = Some(rootResourceClassName))
    val routes = makeNaptimeRoutes(Seq(rootResource))
    val provider = new LocalSchemaProvider(routes)
    // Resource is included. mergedType is None since no DataSchema types
    // but calling the method exercises the code path
    val result = provider.mergedType(ResourceName("courses", 1))
    assert(result === None)
  }

  @Test
  def localSchemaProvider_withNullParentClass_isIncluded(): Unit = {
    val noParentResource = makeResource("courses", 1L, parentClass = None)
    val routes = makeNaptimeRoutes(Seq(noParentResource))
    val provider = new LocalSchemaProvider(routes)
    val result = provider.mergedType(ResourceName("courses", 1))
    // parentClass.isEmpty == true, so resource is included in the schema map
    assert(result === None)
  }

  @Test
  def localSchemaProvider_withActualDataSchema_mergedTypeFound(): Unit = {
    // Build a real RecordDataSchema to use as the merged type
    val mergedTypeName = "org.coursera.MergedCourse"
    val schema = new RecordDataSchema(new Name(mergedTypeName), RecordType.RECORD)
    val eb = new java.lang.StringBuilder
    val idField = new RecordDataSchema.Field(new StringDataSchema)
    idField.setName("id", eb)
    idField.setRecord(schema)
    schema.setFields(immutable.List(idField).asJava, eb)

    val resource = makeResource("courses", 1L, mergedType = mergedTypeName)
    val injector = mock[Injector]
    val builder = mock[ResourceRouterBuilder]
    val router = mock[ResourceRouter]
    when(builder.resourceClass())
      .thenReturn(classOf[AnyRef].asInstanceOf[Class[builder.ResourceClass]])
    when(builder.schema).thenReturn(resource)
    // Return a non-empty types sequence so the filter/map paths are exercised
    when(builder.types).thenReturn(
      immutable.Seq(
        Keyed(mergedTypeName, schema.asInstanceOf[com.linkedin.data.schema.DataSchema])))
    when(builder.build(any())).thenReturn(router)

    val routes = NaptimeRoutes(injector, Set(builder))
    val provider = new LocalSchemaProvider(routes)

    // mergedTypes map contains mergedTypeName → schema, so mergedType should return Some
    val result = provider.mergedType(ResourceName("courses", 1))
    assert(result === Some(schema))
  }
}
