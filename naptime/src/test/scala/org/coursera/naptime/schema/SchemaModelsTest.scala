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

package org.coursera.naptime.schema

import com.linkedin.data.ByteString
import org.coursera.courier.data.StringMap
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

/**
 * Tests for courier-generated schema model classes.
 * These tests cover construction, field access, equality, hashcode, toString, copy, etc.
 */
class SchemaModelsTest extends AssertionsForJUnit {

  // ─── Resource ──────────────────────────────────────────────────────────────

  @Test
  def resource_apply_andAccessFields(): Unit = {
    val resource = Resource(
      kind = ResourceKind.COLLECTION,
      name = "courses",
      version = Some(1L),
      parentClass = None,
      keyType = "string",
      valueType = "org.coursera.Course",
      mergedType = "org.coursera.MergedCourse",
      handlers = HandlerArray(),
      className = "org.coursera.CourseResource",
      attributes = AttributeArray())
    assert(resource.kind === ResourceKind.COLLECTION)
    assert(resource.name === "courses")
    assert(resource.version === Some(1L))
    assert(resource.parentClass === None)
    assert(resource.keyType === "string")
    assert(resource.mergedType === "org.coursera.MergedCourse")
    assert(resource.className === "org.coursera.CourseResource")
  }

  @Test
  def resource_withParentClass(): Unit = {
    val resource = Resource(
      kind = ResourceKind.COLLECTION,
      name = "sessions",
      version = Some(1L),
      parentClass = Some("org.coursera.naptime.resources.TopLevelCollectionResource"),
      keyType = "string",
      valueType = "org.coursera.Session",
      mergedType = "org.coursera.MergedSession",
      handlers = HandlerArray(),
      className = "org.coursera.SessionResource",
      attributes = AttributeArray())
    assert(resource.parentClass === Some("org.coursera.naptime.resources.TopLevelCollectionResource"))
  }

  @Test
  def resource_copy_changesFields(): Unit = {
    val resource = Resource(
      kind = ResourceKind.COLLECTION,
      name = "courses",
      version = None,
      parentClass = None,
      keyType = "string",
      valueType = "org.coursera.Course",
      mergedType = "org.coursera.MergedCourse",
      handlers = HandlerArray(),
      className = "org.coursera.CourseResource",
      attributes = AttributeArray())
    val copied = resource.copy(name = "updatedCourses")
    assert(copied.name === "updatedCourses")
    assert(copied.kind === ResourceKind.COLLECTION)
  }

  @Test
  def resource_equality(): Unit = {
    val r1 = Resource(
      kind = ResourceKind.COLLECTION,
      name = "courses",
      version = None,
      parentClass = None,
      keyType = "string",
      valueType = "v",
      mergedType = "m",
      handlers = HandlerArray(),
      className = "c",
      attributes = AttributeArray())
    val r2 = Resource(
      kind = ResourceKind.COLLECTION,
      name = "courses",
      version = None,
      parentClass = None,
      keyType = "string",
      valueType = "v",
      mergedType = "m",
      handlers = HandlerArray(),
      className = "c",
      attributes = AttributeArray())
    assert(r1 === r2)
    assert(r1.hashCode === r2.hashCode)
  }

  @Test
  def resource_toString(): Unit = {
    val resource = Resource(
      kind = ResourceKind.COLLECTION,
      name = "courses",
      version = None,
      parentClass = None,
      keyType = "string",
      valueType = "v",
      mergedType = "m",
      handlers = HandlerArray(),
      className = "c",
      attributes = AttributeArray())
    assert(resource.toString.contains("Resource"))
  }

  @Test
  def resource_productArity(): Unit = {
    val resource = Resource(
      kind = ResourceKind.COLLECTION,
      name = "courses",
      version = None,
      parentClass = None,
      keyType = "string",
      valueType = "v",
      mergedType = "m",
      handlers = HandlerArray(),
      className = "c",
      attributes = AttributeArray())
    assert(resource.productArity === 10)
    assert(resource.productElement(0) === ResourceKind.COLLECTION)
    assert(resource.productElement(1) === "courses")
  }

  @Test
  def resource_singletonKind(): Unit = {
    val resource = Resource(
      kind = ResourceKind.SINGLETON,
      name = "config",
      version = Some(1L),
      parentClass = None,
      keyType = "unit",
      valueType = "org.coursera.Config",
      mergedType = "org.coursera.MergedConfig",
      handlers = HandlerArray(),
      className = "org.coursera.ConfigResource",
      attributes = AttributeArray())
    assert(resource.kind === ResourceKind.SINGLETON)
  }

  // ─── Handler ────────────────────────────────────────────────────────────────

  @Test
  def handler_apply_andAccessFields(): Unit = {
    val handler = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    assert(handler.kind === HandlerKind.GET)
    assert(handler.name === "get")
  }

  @Test
  def handler_multiGet(): Unit = {
    val handler = Handler(
      kind = HandlerKind.MULTI_GET,
      name = "multiGet",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    assert(handler.kind === HandlerKind.MULTI_GET)
  }

  @Test
  def handler_finder(): Unit = {
    val handler = Handler(
      kind = HandlerKind.FINDER,
      name = "byInstructor",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    assert(handler.kind === HandlerKind.FINDER)
    assert(handler.name === "byInstructor")
  }

  @Test
  def handler_create(): Unit = {
    val handler = Handler(
      kind = HandlerKind.CREATE,
      name = "create",
      parameters = ParameterArray(),
      inputBodyType = Some("org.coursera.Course"),
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    assert(handler.inputBodyType === Some("org.coursera.Course"))
  }

  @Test
  def handler_equality(): Unit = {
    val h1 = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    val h2 = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    assert(h1 === h2)
  }

  @Test
  def handler_copy(): Unit = {
    val h = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    val copied = h.copy(name = "newName")
    assert(copied.name === "newName")
    assert(copied.kind === HandlerKind.GET)
  }

  @Test
  def handler_toString(): Unit = {
    val h = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    assert(h.toString.contains("Handler"))
  }

  // ─── Parameter ────────────────────────────────────────────────────────────────

  @Test
  def parameter_apply_andAccessFields(): Unit = {
    val param = Parameter(
      name = "limit",
      `type` = "int",
      attributes = AttributeArray(),
      required = false)
    assert(param.name === "limit")
    assert(param.`type` === "int")
    assert(param.required === false)
  }

  @Test
  def parameter_required(): Unit = {
    val param = Parameter(
      name = "id",
      `type` = "string",
      attributes = AttributeArray(),
      required = true)
    assert(param.required === true)
  }

  @Test
  def parameter_equality(): Unit = {
    val p1 = Parameter(name = "x", `type` = "int", attributes = AttributeArray(), required = false)
    val p2 = Parameter(name = "x", `type` = "int", attributes = AttributeArray(), required = false)
    assert(p1 === p2)
  }

  @Test
  def parameter_copy(): Unit = {
    val p = Parameter(name = "x", `type` = "int", attributes = AttributeArray(), required = false)
    val copied = p.copy(name = "y")
    assert(copied.name === "y")
    assert(copied.`type` === "int")
  }

  @Test
  def parameter_toString(): Unit = {
    val p = Parameter(name = "limit", `type` = "int", attributes = AttributeArray(), required = false)
    assert(p.toString.contains("Parameter"))
  }

  // ─── HandlerArray / ParameterArray / AttributeArray ───────────────────────

  @Test
  def handlerArray_emptyAndContainsElement(): Unit = {
    val empty = HandlerArray()
    assert(empty.isEmpty)

    val handler = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    val withHandler = HandlerArray(handler)
    assert(!withHandler.isEmpty)
    assert(withHandler.size === 1)
  }

  @Test
  def parameterArray_emptyAndContainsElement(): Unit = {
    val empty = ParameterArray()
    assert(empty.isEmpty)

    val param = Parameter(name = "q", `type` = "string", attributes = AttributeArray(), required = false)
    val withParam = ParameterArray(param)
    assert(!withParam.isEmpty)
    assert(withParam.size === 1)
  }

  @Test
  def attributeArray_emptyAndContainsElement(): Unit = {
    val empty = AttributeArray()
    assert(empty.isEmpty)
  }

  // ─── ResourceKind ─────────────────────────────────────────────────────────

  @Test
  def resourceKind_values(): Unit = {
    assert(ResourceKind.COLLECTION.toString.nonEmpty)
    assert(ResourceKind.SINGLETON.toString.nonEmpty)
    assert(ResourceKind.withName("COLLECTION") === ResourceKind.COLLECTION)
    assert(ResourceKind.withName("SINGLETON") === ResourceKind.SINGLETON)
  }

  @Test
  def resourceKind_withNameKnownValues(): Unit = {
    assert(ResourceKind.withName("COLLECTION") !== null)
    assert(ResourceKind.withName("SINGLETON") !== null)
  }

  // ─── HandlerKind ─────────────────────────────────────────────────────────

  @Test
  def handlerKind_values(): Unit = {
    val kinds = Seq(
      HandlerKind.GET,
      HandlerKind.MULTI_GET,
      HandlerKind.GET_ALL,
      HandlerKind.CREATE,
      HandlerKind.UPSERT,
      HandlerKind.DELETE,
      HandlerKind.PATCH,
      HandlerKind.FINDER,
      HandlerKind.ACTION)
    kinds.foreach { kind =>
      assert(kind.toString.nonEmpty)
    }
    assert(HandlerKind.withName("GET") === HandlerKind.GET)
  }

  // ─── RelationType ─────────────────────────────────────────────────────────

  @Test
  def relationType_values(): Unit = {
    val types = Seq(
      RelationType.GET,
      RelationType.MULTI_GET,
      RelationType.FINDER,
      RelationType.SINGLE_ELEMENT_FINDER)
    types.foreach { rt =>
      assert(rt.toString.nonEmpty)
    }
    assert(RelationType.withName("GET") === RelationType.GET)
  }

  // ─── GraphQLRelationAnnotation ─────────────────────────────────────────────

  @Test
  def graphQLRelationAnnotation_apply_andAccessFields(): Unit = {
    val annotation = GraphQLRelationAnnotation(
      resourceName = "courses.v1",
      arguments = StringMap(),
      relationType = RelationType.GET,
      authOverride = None)
    assert(annotation.resourceName === "courses.v1")
    assert(annotation.relationType === RelationType.GET)
    assert(annotation.authOverride === None)
  }

  @Test
  def graphQLRelationAnnotation_equality(): Unit = {
    val a1 = GraphQLRelationAnnotation("courses.v1", StringMap(), RelationType.GET, None)
    val a2 = GraphQLRelationAnnotation("courses.v1", StringMap(), RelationType.GET, None)
    assert(a1 === a2)
    assert(a1.hashCode === a2.hashCode)
  }

  @Test
  def graphQLRelationAnnotation_copy(): Unit = {
    val a = GraphQLRelationAnnotation("courses.v1", StringMap(), RelationType.GET, None)
    val copied = a.copy(resourceName = "sessions.v1")
    assert(copied.resourceName === "sessions.v1")
    assert(copied.relationType === RelationType.GET)
  }

  @Test
  def graphQLRelationAnnotation_toString(): Unit = {
    val a = GraphQLRelationAnnotation("courses.v1", StringMap(), RelationType.GET, None)
    assert(a.toString.contains("GraphQLRelationAnnotation"))
  }

  // ─── IncludedRelationAnnotation ────────────────────────────────────────────

  @Test
  def includedRelationAnnotation_apply_andAccessFields(): Unit = {
    val annotation = IncludedRelationAnnotation("courses.v1")
    assert(annotation.resourceName === "courses.v1")
  }

  @Test
  def includedRelationAnnotation_equality(): Unit = {
    val a1 = IncludedRelationAnnotation("courses.v1")
    val a2 = IncludedRelationAnnotation("courses.v1")
    assert(a1 === a2)
  }

  @Test
  def includedRelationAnnotation_copy(): Unit = {
    val a = IncludedRelationAnnotation("courses.v1")
    val copied = a.copy(resourceName = "sessions.v1")
    assert(copied.resourceName === "sessions.v1")
  }

  @Test
  def includedRelationAnnotation_toString(): Unit = {
    val a = IncludedRelationAnnotation("courses.v1")
    assert(a.toString.contains("IncludedRelationAnnotation"))
  }

  // ─── HandlerArray DataBuilder ─────────────────────────────────────────────────

  @Test
  def handlerArray_equality(): Unit = {
    val a1 = HandlerArray()
    val a2 = HandlerArray()
    assert(a1 === a2)
  }

  @Test
  def parameterArray_equality(): Unit = {
    val a1 = ParameterArray()
    val a2 = ParameterArray()
    assert(a1 === a2)
  }

  // ─── Attribute ───────────────────────────────────────────────────────────

  @Test
  def attribute_apply_andAccessFields(): Unit = {
    val attr = Attribute(name = "deprecated", value = None)
    assert(attr.name === "deprecated")
    assert(attr.value === None)
  }

  @Test
  def attribute_equality(): Unit = {
    val a1 = Attribute(name = "x", value = None)
    val a2 = Attribute(name = "x", value = None)
    assert(a1 === a2)
    assert(a1.hashCode === a2.hashCode)
  }

  @Test
  def attribute_copy(): Unit = {
    val a = Attribute(name = "x", value = None)
    val copied = a.copy(name = "y")
    assert(copied.name === "y")
  }

  @Test
  def attribute_toString(): Unit = {
    val a = Attribute(name = "doc", value = None)
    assert(a.toString.contains("Attribute"))
  }

  // ─── InternalAuth ─────────────────────────────────────────────────────────

  @Test
  def internalAuth_apply_accessFields(): Unit = {
    val auth = InternalAuth()
    assert(auth != null)
  }

  @Test
  def internalAuth_equality(): Unit = {
    val a1 = InternalAuth()
    val a2 = InternalAuth()
    assert(a1 === a2)
  }

  @Test
  def internalAuth_toString(): Unit = {
    val auth = InternalAuth()
    assert(auth.toString.contains("InternalAuth"))
  }

  // ─── AuthOverride.InternalAuthMember ─────────────────────────────────────

  @Test
  def authOverride_internalAuthMember_apply(): Unit = {
    val auth = InternalAuth()
    val member = AuthOverride.InternalAuthMember(auth)
    assert(member.value === auth)
  }

  @Test
  def authOverride_internalAuthMember_unapply(): Unit = {
    val auth = InternalAuth()
    val member = AuthOverride.InternalAuthMember(auth)
    val AuthOverride.InternalAuthMember(extracted) = member
    assert(extracted === auth)
  }

  @Test
  def authOverride_internalAuthMember_toString(): Unit = {
    val auth = InternalAuth()
    val member = AuthOverride.InternalAuthMember(auth)
    // The union member toString wraps the inner value, so it contains InternalAuth
    assert(member.toString.contains("InternalAuth"))
  }

  // ─── JsValue ──────────────────────────────────────────────────────────────

  @Test
  def jsValue_apply_andAccessFields(): Unit = {
    val js = JsValue()
    assert(js != null)
  }

  @Test
  def jsValue_equality(): Unit = {
    val j1 = JsValue()
    val j2 = JsValue()
    assert(j1 === j2)
  }

  @Test
  def jsValue_toString(): Unit = {
    val j = JsValue()
    assert(j.toString.contains("JsValue"))
  }

  // ─── ArbitraryRecord ─────────────────────────────────────────────────────

  @Test
  def arbitraryRecord_apply_andAccessFields(): Unit = {
    val ar = ArbitraryRecord()
    assert(ar != null)
  }

  @Test
  def arbitraryRecord_equality(): Unit = {
    val a1 = ArbitraryRecord()
    val a2 = ArbitraryRecord()
    assert(a1 === a2)
  }

  @Test
  def arbitraryRecord_toString(): Unit = {
    val ar = ArbitraryRecord()
    assert(ar.toString.contains("ArbitraryRecord"))
  }

  // ─── ArbitraryValue union members ─────────────────────────────────────────

  @Test
  def arbitraryValue_intMember_applyAndValue(): Unit = {
    val m = ArbitraryValue.IntMember(42)
    assert(m.value === 42)
  }

  @Test
  def arbitraryValue_intMember_unapply(): Unit = {
    val m = ArbitraryValue.IntMember(99)
    val ArbitraryValue.IntMember(v) = m
    assert(v === 99)
  }

  @Test
  def arbitraryValue_intMember_toString(): Unit = {
    val m = ArbitraryValue.IntMember(1)
    assert(m.toString.nonEmpty)
  }

  @Test
  def arbitraryValue_stringMember_applyAndValue(): Unit = {
    val m = ArbitraryValue.StringMember("hello")
    assert(m.value === "hello")
  }

  @Test
  def arbitraryValue_stringMember_unapply(): Unit = {
    val m = ArbitraryValue.StringMember("world")
    val ArbitraryValue.StringMember(v) = m
    assert(v === "world")
  }

  @Test
  def arbitraryValue_longMember_applyAndValue(): Unit = {
    val m = ArbitraryValue.LongMember(123456789L)
    assert(m.value === 123456789L)
  }

  @Test
  def arbitraryValue_longMember_unapply(): Unit = {
    val m = ArbitraryValue.LongMember(9876543210L)
    val ArbitraryValue.LongMember(v) = m
    assert(v === 9876543210L)
  }

  @Test
  def arbitraryValue_floatMember_applyAndValue(): Unit = {
    val m = ArbitraryValue.FloatMember(3.14f)
    assert(m.value === 3.14f)
  }

  @Test
  def arbitraryValue_floatMember_unapply(): Unit = {
    val m = ArbitraryValue.FloatMember(2.72f)
    val ArbitraryValue.FloatMember(v) = m
    assert(v === 2.72f)
  }

  @Test
  def arbitraryValue_doubleMember_applyAndValue(): Unit = {
    val m = ArbitraryValue.DoubleMember(3.14159265)
    assert(m.value === 3.14159265)
  }

  @Test
  def arbitraryValue_doubleMember_unapply(): Unit = {
    val m = ArbitraryValue.DoubleMember(2.71828)
    val ArbitraryValue.DoubleMember(v) = m
    assert(v === 2.71828)
  }

  @Test
  def arbitraryValue_booleanMember_true_applyAndValue(): Unit = {
    val m = ArbitraryValue.BooleanMember(true)
    assert(m.value === true)
  }

  @Test
  def arbitraryValue_booleanMember_false_unapply(): Unit = {
    val m = ArbitraryValue.BooleanMember(false)
    val ArbitraryValue.BooleanMember(v) = m
    assert(v === false)
  }

  @Test
  def arbitraryValue_byteStringMember_applyAndValue(): Unit = {
    val bs = ByteString.copyAvroString("hello", false)
    val m = ArbitraryValue.ByteStringMember(bs)
    assert(m.value === bs)
  }

  @Test
  def arbitraryValue_byteStringMember_unapply(): Unit = {
    val bs = ByteString.copyAvroString("world", false)
    val m = ArbitraryValue.ByteStringMember(bs)
    val ArbitraryValue.ByteStringMember(v) = m
    assert(v === bs)
  }

  @Test
  def arbitraryValue_arbitraryRecordMember_applyAndValue(): Unit = {
    val rec = ArbitraryRecord()
    val m = ArbitraryValue.ArbitraryRecordMember(rec)
    assert(m.value === rec)
  }

  @Test
  def arbitraryValue_arbitraryRecordMember_unapply(): Unit = {
    val rec = ArbitraryRecord()
    val m = ArbitraryValue.ArbitraryRecordMember(rec)
    val ArbitraryValue.ArbitraryRecordMember(v) = m
    assert(v === rec)
  }

  @Test
  def arbitraryValue_intMember_equality(): Unit = {
    val m1 = ArbitraryValue.IntMember(7)
    val m2 = ArbitraryValue.IntMember(7)
    assert(m1 === m2)
    assert(m1.hashCode === m2.hashCode)
  }

  // ─── ArbitraryBytesBody ───────────────────────────────────────────────────

  @Test
  def arbitraryBytesBody_apply_noMimeType(): Unit = {
    val body = ArbitraryBytesBody()
    assert(body.mimeType === None)
  }

  @Test
  def arbitraryBytesBody_apply_withMimeType(): Unit = {
    val body = ArbitraryBytesBody(mimeType = Some("image/png"))
    assert(body.mimeType === Some("image/png"))
  }

  @Test
  def arbitraryBytesBody_equality(): Unit = {
    val b1 = ArbitraryBytesBody(mimeType = Some("text/plain"))
    val b2 = ArbitraryBytesBody(mimeType = Some("text/plain"))
    assert(b1 === b2)
    assert(b1.hashCode === b2.hashCode)
  }

  @Test
  def arbitraryBytesBody_copy(): Unit = {
    val body = ArbitraryBytesBody(mimeType = None)
    val copied = body.copy(mimeType = Some("application/json"))
    assert(copied.mimeType === Some("application/json"))
  }

  @Test
  def arbitraryBytesBody_toString(): Unit = {
    val body = ArbitraryBytesBody(mimeType = Some("image/jpeg"))
    assert(body.toString.contains("ArbitraryBytesBody"))
  }

  @Test
  def arbitraryBytesBody_productArity(): Unit = {
    val body = ArbitraryBytesBody(mimeType = Some("text/html"))
    assert(body.productArity === 1)
    assert(body.productElement(0) === Some("text/html"))
  }

  // ─── ParameterDataSchema ──────────────────────────────────────────────────

  @Test
  def parameterDataSchema_apply(): Unit = {
    val pds = ParameterDataSchema()
    assert(pds != null)
  }

  @Test
  def parameterDataSchema_equality(): Unit = {
    val p1 = ParameterDataSchema()
    val p2 = ParameterDataSchema()
    assert(p1 === p2)
    assert(p1.hashCode === p2.hashCode)
  }

  @Test
  def parameterDataSchema_toString(): Unit = {
    val pds = ParameterDataSchema()
    assert(pds.toString.contains("ParameterDataSchema"))
  }

  @Test
  def parameterDataSchema_productArity(): Unit = {
    val pds = ParameterDataSchema()
    assert(pds.productArity === 0)
  }

  // ─── ResourceDataSchema ───────────────────────────────────────────────────

  @Test
  def resourceDataSchema_apply(): Unit = {
    val rds = ResourceDataSchema()
    assert(rds != null)
  }

  @Test
  def resourceDataSchema_equality(): Unit = {
    val r1 = ResourceDataSchema()
    val r2 = ResourceDataSchema()
    assert(r1 === r2)
    assert(r1.hashCode === r2.hashCode)
  }

  @Test
  def resourceDataSchema_toString(): Unit = {
    val rds = ResourceDataSchema()
    assert(rds.toString.contains("ResourceDataSchema"))
  }

  @Test
  def resourceDataSchema_productArity(): Unit = {
    val rds = ResourceDataSchema()
    assert(rds.productArity === 0)
  }

  // ─── ResourceDataSchemaMap ────────────────────────────────────────────────

  @Test
  def resourceDataSchemaMap_empty(): Unit = {
    val m = ResourceDataSchemaMap()
    assert(m.isEmpty)
    assert(m.size === 0)
  }

  @Test
  def resourceDataSchemaMap_withElement(): Unit = {
    val schema = ResourceDataSchema()
    val m = ResourceDataSchemaMap("key" -> schema)
    assert(m.size === 1)
    assert(m.get("key").isDefined)
  }

  @Test
  def resourceDataSchemaMap_get_missing(): Unit = {
    val m = ResourceDataSchemaMap()
    assert(m.get("nonexistent") === None)
  }

  @Test
  def resourceDataSchemaMap_iterator(): Unit = {
    val schema = ResourceDataSchema()
    val m = ResourceDataSchemaMap("k1" -> schema)
    val entries = m.iterator.toList
    assert(entries.size === 1)
    assert(entries.head._1 === "k1")
  }

  @Test
  def resourceDataSchemaMap_plus_withSchemaValue(): Unit = {
    val m = ResourceDataSchemaMap()
    val schema = ResourceDataSchema()
    val updated = m + ("newKey" -> schema)
    assert(updated.size === 1)
  }

  @Test
  def resourceDataSchemaMap_removed(): Unit = {
    val schema = ResourceDataSchema()
    val m = ResourceDataSchemaMap("key" -> schema)
    val after = m.removed("key")
    assert(after.isEmpty)
  }

  // ─── ResourceSchemas ──────────────────────────────────────────────────────

  private def makeResource(): Resource = Resource(
    kind = ResourceKind.COLLECTION,
    name = "items",
    version = None,
    parentClass = None,
    keyType = "string",
    valueType = "org.coursera.Item",
    mergedType = "org.coursera.MergedItem",
    handlers = HandlerArray(),
    className = "org.coursera.ItemResource",
    attributes = AttributeArray())

  @Test
  def resourceSchemas_apply_andAccessFields(): Unit = {
    val rs = ResourceSchemas(
      resourceSchema = makeResource(),
      dataSchemas = ResourceDataSchemaMap())
    assert(rs.resourceSchema.name === "items")
    assert(rs.dataSchemas.isEmpty)
  }

  @Test
  def resourceSchemas_equality(): Unit = {
    val rs1 = ResourceSchemas(makeResource(), ResourceDataSchemaMap())
    val rs2 = ResourceSchemas(makeResource(), ResourceDataSchemaMap())
    assert(rs1 === rs2)
    assert(rs1.hashCode === rs2.hashCode)
  }

  @Test
  def resourceSchemas_copy(): Unit = {
    val rs = ResourceSchemas(makeResource(), ResourceDataSchemaMap())
    val newResource = makeResource().copy(name = "updated")
    val copied = rs.copy(resourceSchema = newResource)
    assert(copied.resourceSchema.name === "updated")
  }

  @Test
  def resourceSchemas_toString(): Unit = {
    val rs = ResourceSchemas(makeResource(), ResourceDataSchemaMap())
    assert(rs.toString.contains("ResourceSchemas"))
  }

  @Test
  def resourceSchemas_productArity(): Unit = {
    val rs = ResourceSchemas(makeResource(), ResourceDataSchemaMap())
    assert(rs.productArity === 2)
  }

  @Test
  def resourceSchemas_unapply(): Unit = {
    val resource = makeResource()
    val rs = ResourceSchemas(resource, ResourceDataSchemaMap())
    val ResourceSchemas(extractedResource, extractedSchemas) = rs
    assert(extractedResource.name === "items")
    assert(extractedSchemas.isEmpty)
  }

  // ─── AttributeArray.DataBuilder / HandlerArray.DataBuilder / ParameterArray.DataBuilder ───

  @Test
  def attributeArray_dataBuilder_buildEmpty(): Unit = {
    val builder = AttributeArray.newBuilder
    val result = builder.result()
    assert(result.isEmpty)
  }

  @Test
  def attributeArray_dataBuilder_addElement(): Unit = {
    val builder = AttributeArray.newBuilder
    builder += Attribute(name = "test", value = None)
    val result = builder.result()
    assert(result.size === 1)
  }

  @Test
  def handlerArray_dataBuilder_buildEmpty(): Unit = {
    val builder = HandlerArray.newBuilder
    val result = builder.result()
    assert(result.isEmpty)
  }

  @Test
  def handlerArray_dataBuilder_addElement(): Unit = {
    val builder = HandlerArray.newBuilder
    builder += Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    val result = builder.result()
    assert(result.size === 1)
  }

  @Test
  def parameterArray_dataBuilder_buildEmpty(): Unit = {
    val builder = ParameterArray.newBuilder
    val result = builder.result()
    assert(result.isEmpty)
  }

  @Test
  def parameterArray_dataBuilder_addElement(): Unit = {
    val builder = ParameterArray.newBuilder
    builder += Parameter(name = "p", `type` = "string", attributes = AttributeArray(), required = false)
    val result = builder.result()
    assert(result.size === 1)
  }

  @Test
  def resourceDataSchemaMap_dataBuilder_buildEmpty(): Unit = {
    val builder = ResourceDataSchemaMap.newBuilder
    val result = builder.result()
    assert(result.isEmpty)
  }

  @Test
  def resourceDataSchemaMap_dataBuilder_addElement(): Unit = {
    val builder = ResourceDataSchemaMap.newBuilder
    builder += ("schemaKey" -> ResourceDataSchema())
    val result = builder.result()
    assert(result.size === 1)
  }

  // ─── unapply tests ────────────────────────────────────────────────────────

  @Test
  def resource_unapply(): Unit = {
    val resource = makeResource()
    val Resource(kind, name, version, parentClass, keyType, valueType, mergedType, handlers, className, attributes) = resource
    assert(kind === ResourceKind.COLLECTION)
    assert(name === "items")
    assert(version === None)
  }

  @Test
  def handler_unapply(): Unit = {
    val h = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    val Handler(kind, name, params, inputBodyType, customOutputBodyType, authType, attrs) = h
    assert(kind === HandlerKind.GET)
    assert(name === "get")
    assert(inputBodyType === None)
  }

  @Test
  def parameter_unapply(): Unit = {
    val p = Parameter(name = "limit", `type` = "int", attributes = AttributeArray(), required = false)
    val Parameter(name, typ, typeSchema, attributes, default, required) = p
    assert(name === "limit")
    assert(typ === "int")
    assert(required === false)
  }

  @Test
  def attribute_unapply(): Unit = {
    val a = Attribute(name = "deprecated", value = None)
    val Attribute(name, value) = a
    assert(name === "deprecated")
    assert(value === None)
  }

  @Test
  def includedRelationAnnotation_unapply(): Unit = {
    val a = IncludedRelationAnnotation("courses.v1")
    val IncludedRelationAnnotation(resourceName) = a
    assert(resourceName === "courses.v1")
  }

  @Test
  def graphQLRelationAnnotation_unapply(): Unit = {
    val a = GraphQLRelationAnnotation("courses.v1", StringMap(), RelationType.GET, None)
    val GraphQLRelationAnnotation(resourceName, arguments, relationType, authOverride) = a
    assert(resourceName === "courses.v1")
    assert(relationType === RelationType.GET)
    assert(authOverride === None)
  }

  @Test
  def arbitraryBytesBody_unapply(): Unit = {
    val body = ArbitraryBytesBody(mimeType = Some("image/png"))
    val ArbitraryBytesBody(mimeType) = body
    assert(mimeType === Some("image/png"))
  }

  @Test
  def parameterDataSchema_unapply(): Unit = {
    val pds = ParameterDataSchema()
    val result = ParameterDataSchema.unapply(pds)
    assert(result === true)
  }

  @Test
  def resourceDataSchema_unapply(): Unit = {
    val rds = ResourceDataSchema()
    val result = ResourceDataSchema.unapply(rds)
    assert(result === true)
  }

  @Test
  def internalAuth_unapply(): Unit = {
    val auth = InternalAuth()
    val result = InternalAuth.unapply(auth)
    assert(result === true)
  }

  // ─── More productElement tests ────────────────────────────────────────────

  @Test
  def resource_productElement_allFields(): Unit = {
    val resource = Resource(
      kind = ResourceKind.COLLECTION,
      name = "courses",
      version = Some(2L),
      parentClass = Some("ParentClass"),
      keyType = "string",
      valueType = "v",
      mergedType = "m",
      handlers = HandlerArray(),
      className = "c",
      attributes = AttributeArray())
    assert(resource.productElement(0) === ResourceKind.COLLECTION)
    assert(resource.productElement(1) === "courses")
    assert(resource.productElement(2) === Some(2L))
    assert(resource.productElement(3) === Some("ParentClass"))
    assert(resource.productElement(4) === "string")
    assert(resource.productElement(5) === "v")
    assert(resource.productElement(6) === "m")
    assert(resource.productElement(8) === "c")
    assert(resource.productElement(9) !== null)
    intercept[IndexOutOfBoundsException] {
      resource.productElement(10)
    }
  }

  @Test
  def handler_productElement_allFields(): Unit = {
    val h = Handler(
      kind = HandlerKind.FINDER,
      name = "search",
      parameters = ParameterArray(),
      inputBodyType = Some("Input"),
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    assert(h.productElement(0) === HandlerKind.FINDER)
    assert(h.productElement(1) === "search")
    assert(h.productElement(3) === Some("Input"))
    assert(h.productElement(4) === None)
    assert(h.productElement(5) === None)
    intercept[IndexOutOfBoundsException] {
      h.productElement(7)
    }
  }

  @Test
  def parameter_productElement_allFields(): Unit = {
    val p = Parameter(name = "limit", `type` = "int", attributes = AttributeArray(), required = true)
    assert(p.productElement(0) === "limit")
    assert(p.productElement(1) === "int")
    assert(p.productElement(2) === None)
    assert(p.productElement(4) === None)
    assert(p.productElement(5) === true)
    intercept[IndexOutOfBoundsException] {
      p.productElement(6)
    }
  }

  @Test
  def attribute_productElement_allFields(): Unit = {
    val a = Attribute(name = "doc", value = None)
    assert(a.productElement(0) === "doc")
    assert(a.productElement(1) === None)
    intercept[IndexOutOfBoundsException] {
      a.productElement(2)
    }
  }

  @Test
  def arbitraryBytesBody_productElement_outOfBounds(): Unit = {
    val body = ArbitraryBytesBody(mimeType = None)
    intercept[IndexOutOfBoundsException] {
      body.productElement(1)
    }
  }

  @Test
  def resourceSchemas_productElement_allFields(): Unit = {
    val rs = ResourceSchemas(makeResource(), ResourceDataSchemaMap())
    assert(rs.productElement(0) !== null)
    assert(rs.productElement(1) !== null)
    intercept[IndexOutOfBoundsException] {
      rs.productElement(2)
    }
  }

  // ─── AuthOverride wrap/build ───────────────────────────────────────────────

  @Test
  def authOverride_wrap_internalAuth(): Unit = {
    val auth = InternalAuth()
    val member: AuthOverride = AuthOverride.wrap(auth)
    assert(member.isInstanceOf[AuthOverride.InternalAuthMember])
  }

  @Test
  def authOverride_build_internalAuthMember(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("org.coursera.naptime.schema.InternalAuth", new DataMap())
    val result = AuthOverride.build(dm, DataConversion.SetReadOnly)
    assert(result.isInstanceOf[AuthOverride.InternalAuthMember])
  }

  @Test
  def authOverride_build_unknownMember(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("unknownType", new DataMap())
    val result = AuthOverride.build(dm, DataConversion.SetReadOnly)
    assert(result.isInstanceOf[AuthOverride.$UnknownMember])
  }

  @Test
  def authOverride_internalAuthMember_fromDataMap(): Unit = {
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("org.coursera.naptime.schema.InternalAuth", new DataMap())
    val member = AuthOverride.InternalAuthMember(dm)
    assert(member != null)
    assert(member.value != null)
  }

  @Test
  def authOverride_unknownMember_toString(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("unknownType", new DataMap())
    val result = AuthOverride.build(dm, DataConversion.SetReadOnly)
    assert(result.toString.nonEmpty)
  }

  // ─── ArbitraryValue build ─────────────────────────────────────────────────

  @Test
  def arbitraryValue_build_intMember(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("int", Int.box(42))
    val result = ArbitraryValue.build(dm, DataConversion.SetReadOnly)
    assert(result.isInstanceOf[ArbitraryValue.IntMember])
  }

  @Test
  def arbitraryValue_build_stringMember(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("string", "hello")
    val result = ArbitraryValue.build(dm, DataConversion.SetReadOnly)
    assert(result.isInstanceOf[ArbitraryValue.StringMember])
  }

  @Test
  def arbitraryValue_build_longMember(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("long", Long.box(123456789L))
    val result = ArbitraryValue.build(dm, DataConversion.SetReadOnly)
    assert(result.isInstanceOf[ArbitraryValue.LongMember])
  }

  @Test
  def arbitraryValue_build_floatMember(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("float", Float.box(3.14f))
    val result = ArbitraryValue.build(dm, DataConversion.SetReadOnly)
    assert(result.isInstanceOf[ArbitraryValue.FloatMember])
  }

  @Test
  def arbitraryValue_build_doubleMember(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("double", Double.box(2.718))
    val result = ArbitraryValue.build(dm, DataConversion.SetReadOnly)
    assert(result.isInstanceOf[ArbitraryValue.DoubleMember])
  }

  @Test
  def arbitraryValue_build_booleanMember(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("boolean", Boolean.box(true))
    val result = ArbitraryValue.build(dm, DataConversion.SetReadOnly)
    assert(result.isInstanceOf[ArbitraryValue.BooleanMember])
  }

  @Test
  def arbitraryValue_build_bytesMember(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("bytes", ByteString.copyAvroString("data", false))
    val result = ArbitraryValue.build(dm, DataConversion.SetReadOnly)
    assert(result.isInstanceOf[ArbitraryValue.ByteStringMember])
  }

  @Test
  def arbitraryValue_build_arbitraryRecordMember(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("org.coursera.naptime.schema.ArbitraryRecord", new DataMap())
    val result = ArbitraryValue.build(dm, DataConversion.SetReadOnly)
    assert(result.isInstanceOf[ArbitraryValue.ArbitraryRecordMember])
  }

  @Test
  def arbitraryValue_build_unknownMember(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("unknownKey", "someValue")
    val result = ArbitraryValue.build(dm, DataConversion.SetReadOnly)
    assert(result.isInstanceOf[ArbitraryValue.$UnknownMember])
  }

  @Test
  def arbitraryValue_intMember_fromDataMap(): Unit = {
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("int", Int.box(7))
    val member = ArbitraryValue.IntMember(dm)
    assert(member != null)
    assert(member.value === 7)
  }

  @Test
  def arbitraryValue_stringMember_fromDataMap(): Unit = {
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("string", "test")
    val member = ArbitraryValue.StringMember(dm)
    assert(member != null)
    assert(member.value === "test")
  }

  @Test
  def arbitraryValue_longMember_fromDataMap(): Unit = {
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("long", Long.box(99L))
    val member = ArbitraryValue.LongMember(dm)
    assert(member != null)
    assert(member.value === 99L)
  }

  @Test
  def arbitraryValue_floatMember_fromDataMap(): Unit = {
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("float", Float.box(1.0f))
    val member = ArbitraryValue.FloatMember(dm)
    assert(member != null)
    assert(member.value === 1.0f)
  }

  @Test
  def arbitraryValue_doubleMember_fromDataMap(): Unit = {
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("double", Double.box(1.0))
    val member = ArbitraryValue.DoubleMember(dm)
    assert(member != null)
    assert(member.value === 1.0)
  }

  @Test
  def arbitraryValue_booleanMember_fromDataMap(): Unit = {
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("boolean", Boolean.box(false))
    val member = ArbitraryValue.BooleanMember(dm)
    assert(member != null)
    assert(member.value === false)
  }

  @Test
  def arbitraryValue_byteStringMember_fromDataMap(): Unit = {
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("bytes", ByteString.copyAvroString("bytes", false))
    val member = ArbitraryValue.ByteStringMember(dm)
    assert(member != null)
  }

  @Test
  def arbitraryValue_arbitraryRecordMember_fromDataMap(): Unit = {
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("org.coursera.naptime.schema.ArbitraryRecord", new DataMap())
    val member = ArbitraryValue.ArbitraryRecordMember(dm)
    assert(member != null)
    assert(member.value != null)
  }

  @Test
  def arbitraryValue_unknownMember_declaringTyperefSchema(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("unknownKey", "val")
    val member = ArbitraryValue.build(dm, DataConversion.SetReadOnly)
    assert(member.isInstanceOf[ArbitraryValue.$UnknownMember])
  }

  @Test
  def authOverride_internalAuthMember_declaringTyperefSchema(): Unit = {
    val auth = InternalAuth()
    val member = AuthOverride.InternalAuthMember(auth)
    assert(member.declaringTyperefSchema.isDefined)
  }

  // ─── Array iteration tests ────────────────────────────────────────────────

  @Test
  def attributeArray_iteration(): Unit = {
    val a1 = Attribute(name = "x", value = None)
    val a2 = Attribute(name = "y", value = None)
    val arr = AttributeArray(a1, a2)
    val elems = arr.toList
    assert(elems.size === 2)
    assert(elems.head.name === "x")
  }

  @Test
  def handlerArray_iteration(): Unit = {
    val h = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    val arr = HandlerArray(h)
    val elems = arr.toList
    assert(elems.size === 1)
    assert(elems.head.name === "get")
  }

  @Test
  def parameterArray_iteration(): Unit = {
    val p = Parameter(name = "q", `type` = "string", attributes = AttributeArray(), required = false)
    val arr = ParameterArray(p)
    val elems = arr.toList
    assert(elems.size === 1)
    assert(elems.head.name === "q")
  }

  // ─── canEqual tests ───────────────────────────────────────────────────────

  @Test
  def resource_canEqual(): Unit = {
    val r1 = makeResource()
    val r2 = makeResource()
    assert(r1.canEqual(r2))
    assert(!r1.canEqual("not a resource"))
  }

  @Test
  def handler_canEqual(): Unit = {
    val h = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    assert(h.canEqual(h))
    assert(!h.canEqual(42))
  }

  @Test
  def parameter_canEqual(): Unit = {
    val p = Parameter(name = "x", `type` = "int", attributes = AttributeArray(), required = false)
    assert(p.canEqual(p))
    assert(!p.canEqual("string"))
  }

  @Test
  def attribute_canEqual(): Unit = {
    val a = Attribute(name = "n", value = None)
    assert(a.canEqual(a))
    assert(!a.canEqual(List.empty))
  }

  @Test
  def graphQLRelationAnnotation_canEqual(): Unit = {
    val a = GraphQLRelationAnnotation("r", StringMap(), RelationType.GET, None)
    assert(a.canEqual(a))
    assert(!a.canEqual("r"))
  }

  @Test
  def includedRelationAnnotation_canEqual(): Unit = {
    val a = IncludedRelationAnnotation("r")
    assert(a.canEqual(a))
    assert(!a.canEqual(42))
  }

  @Test
  def arbitraryBytesBody_canEqual(): Unit = {
    val b = ArbitraryBytesBody(mimeType = None)
    assert(b.canEqual(b))
    assert(!b.canEqual("x"))
  }

  @Test
  def resourceSchemas_canEqual(): Unit = {
    val rs = ResourceSchemas(makeResource(), ResourceDataSchemaMap())
    assert(rs.canEqual(rs))
    assert(!rs.canEqual(42))
  }

  // ─── Inequality tests ─────────────────────────────────────────────────────

  @Test
  def resource_notEqual_whenDifferent(): Unit = {
    val r1 = makeResource()
    val r2 = makeResource().copy(name = "other")
    assert(r1 !== r2)
  }

  @Test
  def handler_notEqual_whenDifferent(): Unit = {
    val h1 = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    val h2 = h1.copy(name = "other")
    assert(h1 !== h2)
  }

  @Test
  def parameter_notEqual_whenDifferent(): Unit = {
    val p1 = Parameter(name = "x", `type` = "int", attributes = AttributeArray(), required = false)
    val p2 = Parameter(name = "y", `type` = "int", attributes = AttributeArray(), required = false)
    assert(p1 !== p2)
  }

  // ─── build(DataMap, DataConversion) tests for empty record types ──────────

  @Test
  def arbitraryRecord_build_fromDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    val result = ArbitraryRecord.build(dm, DataConversion.SetReadOnly)
    assert(result != null)
  }

  @Test
  def arbitraryRecord_copy_withDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val ar = ArbitraryRecord()
    val copied = ar.copy(new DataMap(), DataConversion.SetReadOnly)
    assert(copied != null)
  }

  @Test
  def arbitraryRecord_hashCode(): Unit = {
    val a1 = ArbitraryRecord()
    val a2 = ArbitraryRecord()
    assert(a1.hashCode === a2.hashCode)
  }

  @Test
  def arbitraryRecord_notEqual_toNonRecord(): Unit = {
    val ar = ArbitraryRecord()
    assert(!ar.equals("not a record"))
    assert(!ar.equals(42))
  }

  @Test
  def arbitraryRecord_productElement_outOfBounds(): Unit = {
    val ar = ArbitraryRecord()
    intercept[IndexOutOfBoundsException] {
      ar.productElement(0)
    }
  }

  @Test
  def internalAuth_build_fromDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    val result = InternalAuth.build(dm, DataConversion.SetReadOnly)
    assert(result != null)
  }

  @Test
  def internalAuth_copy_withDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val auth = InternalAuth()
    val copied = auth.copy(new DataMap(), DataConversion.SetReadOnly)
    assert(copied != null)
  }

  @Test
  def internalAuth_hashCode(): Unit = {
    val a1 = InternalAuth()
    val a2 = InternalAuth()
    assert(a1.hashCode === a2.hashCode)
  }

  @Test
  def internalAuth_notEqual_toNonRecord(): Unit = {
    val auth = InternalAuth()
    assert(!auth.equals("not an auth"))
  }

  @Test
  def internalAuth_productElement_outOfBounds(): Unit = {
    val auth = InternalAuth()
    intercept[IndexOutOfBoundsException] {
      auth.productElement(0)
    }
  }

  @Test
  def jsValue_build_fromDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    val result = JsValue.build(dm, DataConversion.SetReadOnly)
    assert(result != null)
  }

  @Test
  def jsValue_copy_withDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val js = JsValue()
    val copied = js.copy(new DataMap(), DataConversion.SetReadOnly)
    assert(copied != null)
  }

  @Test
  def jsValue_hashCode(): Unit = {
    val j1 = JsValue()
    val j2 = JsValue()
    assert(j1.hashCode === j2.hashCode)
  }

  @Test
  def jsValue_notEqual_toNonRecord(): Unit = {
    val js = JsValue()
    assert(!js.equals("not a jsvalue"))
  }

  @Test
  def jsValue_productElement_outOfBounds(): Unit = {
    val js = JsValue()
    intercept[IndexOutOfBoundsException] {
      js.productElement(0)
    }
  }

  @Test
  def includedRelationAnnotation_build_fromDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("resourceName", "courses.v1")
    val result = IncludedRelationAnnotation.build(dm, DataConversion.SetReadOnly)
    assert(result != null)
    assert(result.resourceName === "courses.v1")
  }

  @Test
  def includedRelationAnnotation_copy_withDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val a = IncludedRelationAnnotation("courses.v1")
    val dm = new DataMap()
    dm.put("resourceName", "sessions.v1")
    val copied = a.copy(dm, DataConversion.SetReadOnly)
    assert(copied.resourceName === "sessions.v1")
  }

  @Test
  def includedRelationAnnotation_hashCode(): Unit = {
    val a1 = IncludedRelationAnnotation("courses.v1")
    val a2 = IncludedRelationAnnotation("courses.v1")
    assert(a1.hashCode === a2.hashCode)
  }

  @Test
  def includedRelationAnnotation_notEqual_toNonRecord(): Unit = {
    val a = IncludedRelationAnnotation("courses.v1")
    assert(!a.equals("not a relation"))
  }

  @Test
  def includedRelationAnnotation_productElement_outOfBounds(): Unit = {
    val a = IncludedRelationAnnotation("courses.v1")
    assert(a.productElement(0) === "courses.v1")
    intercept[IndexOutOfBoundsException] {
      a.productElement(1)
    }
  }

  @Test
  def graphQLRelationAnnotation_build_fromDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val dm = new DataMap()
    dm.put("resourceName", "courses.v1")
    dm.put("relationType", "GET")
    dm.put("arguments", new DataMap())
    val result = GraphQLRelationAnnotation.build(dm, DataConversion.SetReadOnly)
    assert(result != null)
    assert(result.resourceName === "courses.v1")
  }

  @Test
  def graphQLRelationAnnotation_copy_withDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val a = GraphQLRelationAnnotation("courses.v1", StringMap(), RelationType.GET, None)
    val dm = new DataMap()
    dm.put("resourceName", "sessions.v1")
    dm.put("relationType", "GET")
    dm.put("arguments", new DataMap())
    val copied = a.copy(dm, DataConversion.SetReadOnly)
    assert(copied.resourceName === "sessions.v1")
  }

  @Test
  def graphQLRelationAnnotation_notEqual_toNonRecord(): Unit = {
    val a = GraphQLRelationAnnotation("courses.v1", StringMap(), RelationType.GET, None)
    assert(!a.equals("not an annotation"))
  }

  @Test
  def graphQLRelationAnnotation_productElement_outOfBounds(): Unit = {
    val a = GraphQLRelationAnnotation("courses.v1", StringMap(), RelationType.GET, None)
    intercept[IndexOutOfBoundsException] {
      a.productElement(4)
    }
  }

  @Test
  def graphQLRelationAnnotation_withAuthOverride_setFields(): Unit = {
    val auth = InternalAuth()
    val authOverride = AuthOverride.InternalAuthMember(auth)
    val a = GraphQLRelationAnnotation("courses.v1", StringMap(), RelationType.GET, Some(authOverride))
    assert(a.authOverride === Some(authOverride))
  }

  @Test
  def resourceSchemas_build_fromDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val resource = makeResource()
    val resourceDataMap = resource.data()
    val dm = new DataMap()
    dm.put("resourceSchema", resourceDataMap)
    dm.put("dataSchemas", new DataMap())
    val result = ResourceSchemas.build(dm, DataConversion.SetReadOnly)
    assert(result != null)
  }

  @Test
  def resourceSchemas_notEqual_toOther(): Unit = {
    val rs1 = ResourceSchemas(makeResource(), ResourceDataSchemaMap())
    val rs2 = ResourceSchemas(makeResource().copy(name = "other"), ResourceDataSchemaMap())
    assert(!rs1.equals(rs2))
    assert(!rs1.equals("not a schema"))
  }

  @Test
  def authOverride_hashCode(): Unit = {
    val auth = InternalAuth()
    val m1 = AuthOverride.InternalAuthMember(auth)
    val m2 = AuthOverride.InternalAuthMember(auth)
    assert(m1.hashCode === m2.hashCode)
  }

  @Test
  def authOverride_equals_sameType(): Unit = {
    val auth = InternalAuth()
    val m1 = AuthOverride.InternalAuthMember(auth)
    val m2 = AuthOverride.InternalAuthMember(auth)
    assert(m1 === m2)
  }

  @Test
  def authOverride_notEqual_toDifferentType(): Unit = {
    val auth = InternalAuth()
    val m = AuthOverride.InternalAuthMember(auth)
    assert(!m.equals("not a member"))
    assert(!m.equals(42))
  }

  @Test
  def authOverride_canEqual_withSelf(): Unit = {
    val auth = InternalAuth()
    val m = AuthOverride.InternalAuthMember(auth)
    assert(m.canEqual(m))
    assert(!m.canEqual("x"))
  }

  // ─── declaringTyperefSchema for ArbitraryValue members ────────────────────

  @Test
  def arbitraryValue_intMember_declaringTyperefSchema(): Unit = {
    val m = ArbitraryValue.IntMember(5)
    assert(m.declaringTyperefSchema.isDefined)
    assert(m.declaringTyperefSchema.get === ArbitraryValue.TYPEREF_SCHEMA)
  }

  @Test
  def arbitraryValue_stringMember_declaringTyperefSchema(): Unit = {
    val m = ArbitraryValue.StringMember("s")
    assert(m.declaringTyperefSchema.isDefined)
  }

  @Test
  def arbitraryValue_longMember_declaringTyperefSchema(): Unit = {
    val m = ArbitraryValue.LongMember(1L)
    assert(m.declaringTyperefSchema.isDefined)
  }

  @Test
  def arbitraryValue_floatMember_declaringTyperefSchema(): Unit = {
    val m = ArbitraryValue.FloatMember(1.0f)
    assert(m.declaringTyperefSchema.isDefined)
  }

  @Test
  def arbitraryValue_doubleMember_declaringTyperefSchema(): Unit = {
    val m = ArbitraryValue.DoubleMember(1.0)
    assert(m.declaringTyperefSchema.isDefined)
  }

  @Test
  def arbitraryValue_booleanMember_declaringTyperefSchema(): Unit = {
    val m = ArbitraryValue.BooleanMember(true)
    assert(m.declaringTyperefSchema.isDefined)
  }

  @Test
  def arbitraryValue_byteStringMember_declaringTyperefSchema(): Unit = {
    val bs = ByteString.copyAvroString("data", false)
    val m = ArbitraryValue.ByteStringMember(bs)
    assert(m.declaringTyperefSchema.isDefined)
  }

  @Test
  def arbitraryValue_arbitraryRecordMember_declaringTyperefSchema(): Unit = {
    val rec = ArbitraryRecord()
    val m = ArbitraryValue.ArbitraryRecordMember(rec)
    assert(m.declaringTyperefSchema.isDefined)
  }

  // ─── implicit def wrap for ArbitraryValue members ─────────────────────────
  // Directly call the `wrap` methods to cover lines that are not otherwise hit.

  @Test
  def arbitraryValue_wrap_intImplicit(): Unit = {
    val m = ArbitraryValue.IntMember(ArbitraryValue.wrap(42).value)
    assert(m.value === 42)
  }

  @Test
  def arbitraryValue_wrap_stringImplicit(): Unit = {
    val m = ArbitraryValue.StringMember(ArbitraryValue.wrap("hello").value)
    assert(m.value === "hello")
  }

  @Test
  def arbitraryValue_wrap_longImplicit(): Unit = {
    val m = ArbitraryValue.LongMember(ArbitraryValue.wrap(99L).value)
    assert(m.value === 99L)
  }

  @Test
  def arbitraryValue_wrap_floatImplicit(): Unit = {
    val m = ArbitraryValue.FloatMember(ArbitraryValue.wrap(2.5f).value)
    assert(m.value === 2.5f)
  }

  @Test
  def arbitraryValue_wrap_doubleImplicit(): Unit = {
    val m = ArbitraryValue.DoubleMember(ArbitraryValue.wrap(3.14).value)
    assert(m.value === 3.14)
  }

  @Test
  def arbitraryValue_wrap_booleanImplicit(): Unit = {
    val m = ArbitraryValue.BooleanMember(ArbitraryValue.wrap(false).value)
    assert(m.value === false)
  }

  @Test
  def arbitraryValue_wrap_byteStringImplicit(): Unit = {
    val bs = ByteString.copyAvroString("test", false)
    // ByteString wrap creates a ByteStringMember
    val wrapped = ArbitraryValue.wrap(bs)
    assert(wrapped != null)
    assert(wrapped.value === bs)
  }

  @Test
  def arbitraryValue_wrap_arbitraryRecordImplicit(): Unit = {
    val rec = ArbitraryRecord()
    val wrapped = ArbitraryValue.wrap(rec)
    assert(wrapped != null)
    assert(wrapped.value === rec)
  }

  // ─── Handler with optional fields set ─────────────────────────────────────

  @Test
  def handler_withCustomOutputBodyType_setsField(): Unit = {
    val h = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = Some("CustomOutput"),
      authType = None,
      attributes = AttributeArray())
    assert(h.customOutputBodyType === Some("CustomOutput"))
  }

  @Test
  def handler_withAuthType_setsField(): Unit = {
    val h = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = Some("MyAuthType"),
      attributes = AttributeArray())
    assert(h.authType === Some("MyAuthType"))
  }

  @Test
  def handler_hashCode_isStable(): Unit = {
    val h1 = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    val h2 = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    assert(h1.hashCode === h2.hashCode)
  }

  @Test
  def handler_notEqual_toDifferentType(): Unit = {
    val h = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    assert(!h.equals("not a handler"))
    assert(!h.equals(42))
  }

  @Test
  def handler_copy_withDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    import com.linkedin.data.DataMap
    val h = Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = ParameterArray(),
      inputBodyType = None,
      customOutputBodyType = None,
      authType = None,
      attributes = AttributeArray())
    val copied = h.copy(h.data(), DataConversion.SetReadOnly)
    assert(copied != null)
    assert(copied.name === "get")
  }

  // ─── Parameter with optional fields set ───────────────────────────────────

  @Test
  def parameter_withDefault_setsField(): Unit = {
    val p = Parameter(
      name = "limit",
      `type` = "int",
      typeSchema = None,
      attributes = AttributeArray(),
      default = Some(ArbitraryValue.IntMember(10)),
      required = false)
    assert(p.default.isDefined)
  }

  @Test
  def parameter_hashCode_isStable(): Unit = {
    val p1 = Parameter(name = "x", `type` = "int", attributes = AttributeArray(), required = false)
    val p2 = Parameter(name = "x", `type` = "int", attributes = AttributeArray(), required = false)
    assert(p1.hashCode === p2.hashCode)
  }

  @Test
  def parameter_notEqual_toDifferentType(): Unit = {
    val p = Parameter(name = "x", `type` = "int", attributes = AttributeArray(), required = false)
    assert(!p.equals("not a parameter"))
    assert(!p.equals(42))
  }

  @Test
  def parameter_copy_withDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    val p = Parameter(name = "x", `type` = "int", attributes = AttributeArray(), required = false)
    val copied = p.copy(p.data(), DataConversion.SetReadOnly)
    assert(copied != null)
    assert(copied.name === "x")
  }

  @Test
  def parameter_withTypeSchema_setsField(): Unit = {
    val pds = ParameterDataSchema()
    val p = Parameter(
      name = "sort",
      `type` = "SortOrder",
      typeSchema = Some(pds),
      attributes = AttributeArray(),
      required = false)
    assert(p.typeSchema.isDefined)
  }

  // ─── Attribute with value set ─────────────────────────────────────────────

  @Test
  def attribute_withValue_setsField(): Unit = {
    val js = JsValue()
    val a = Attribute(name = "scaladocs", value = Some(js))
    assert(a.value === Some(js))
  }

  @Test
  def attribute_hashCode_isStable(): Unit = {
    val a1 = Attribute(name = "x", value = None)
    val a2 = Attribute(name = "x", value = None)
    assert(a1.hashCode === a2.hashCode)
  }

  @Test
  def attribute_notEqual_toDifferentType(): Unit = {
    val a = Attribute(name = "x", value = None)
    assert(!a.equals("not an attribute"))
    assert(!a.equals(42))
  }

  @Test
  def attribute_copy_withDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    val a = Attribute(name = "doc", value = None)
    val copied = a.copy(a.data(), DataConversion.SetReadOnly)
    assert(copied != null)
    assert(copied.name === "doc")
  }

  // ─── ResourceDataSchemaMap more ops ───────────────────────────────────────

  @Test
  def resourceDataSchemaMap_getOrElse(): Unit = {
    val m = ResourceDataSchemaMap()
    val result = m.getOrElse("missing", ResourceDataSchema())
    assert(result != null)
  }

  @Test
  def resourceDataSchemaMap_contains(): Unit = {
    val schema = ResourceDataSchema()
    val m = ResourceDataSchemaMap("k" -> schema)
    assert(m.contains("k"))
    assert(!m.contains("missing"))
  }

  // ─── ResourceSchemas more ops ──────────────────────────────────────────────

  @Test
  def resourceSchemas_hashCode_isStable(): Unit = {
    val rs1 = ResourceSchemas(makeResource(), ResourceDataSchemaMap())
    val rs2 = ResourceSchemas(makeResource(), ResourceDataSchemaMap())
    assert(rs1.hashCode === rs2.hashCode)
  }

  @Test
  def resourceSchemas_copy_withDataMap(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    val rs = ResourceSchemas(makeResource(), ResourceDataSchemaMap())
    val copied = rs.copy(rs.data(), DataConversion.SetReadOnly)
    assert(copied != null)
  }

  // ─── GraphQLRelationAnnotation more ops ───────────────────────────────────

  @Test
  def graphQLRelationAnnotation_hashCode_isStable(): Unit = {
    val a1 = GraphQLRelationAnnotation("r", StringMap(), RelationType.GET, None)
    val a2 = GraphQLRelationAnnotation("r", StringMap(), RelationType.GET, None)
    assert(a1.hashCode === a2.hashCode)
  }

  @Test
  def graphQLRelationAnnotation_copy_withDataMap2(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    val a = GraphQLRelationAnnotation("r", StringMap(), RelationType.GET, None)
    val copied = a.copy(a.data(), DataConversion.SetReadOnly)
    assert(copied != null)
    assert(copied.resourceName === "r")
  }

  // ─── IncludedRelationAnnotation more ops ──────────────────────────────────

  @Test
  def includedRelationAnnotation_copy_withDataMap2(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    val a = IncludedRelationAnnotation("r.v1")
    val copied = a.copy(a.data(), DataConversion.SetReadOnly)
    assert(copied != null)
    assert(copied.resourceName === "r.v1")
  }

  @Test
  def includedRelationAnnotation_notEqual_toDifferentType(): Unit = {
    val a = IncludedRelationAnnotation("r.v1")
    assert(!a.equals("not a relation"))
    assert(!a.equals(42))
  }

  // ─── HandlerArray indexing and copy / apply-Iterable ─────────────────────

  @Test
  def handlerArray_applyIdx_returnsElement(): Unit = {
    // Exercises HandlerArray.apply(idx) (coerceInput + apply).
    val h = Handler(kind = HandlerKind.GET, name = "get", parameters = ParameterArray())
    val arr = HandlerArray(h)
    assert(arr(0).name === "get")
  }

  @Test
  def handlerArray_copy_withDataList(): Unit = {
    // Exercises HandlerArray.copy(dataList, conversion).
    import org.coursera.courier.templates.DataTemplates.DataConversion
    val h = Handler(kind = HandlerKind.GET, name = "get", parameters = ParameterArray())
    val arr = HandlerArray(h)
    val copied = arr.copy(arr.data(), DataConversion.SetReadOnly)
    assert(copied.length === 1)
  }

  @Test
  def handlerArray_applyIterable_createsArray(): Unit = {
    // Exercises HandlerArray.apply(Iterable).
    val h = Handler(kind = HandlerKind.GET, name = "getAll", parameters = ParameterArray())
    val arr = HandlerArray(List(h))
    assert(arr.length === 1)
    assert(arr(0).name === "getAll")
  }

  // ─── AttributeArray indexing and copy / apply-Iterable ───────────────────

  @Test
  def attributeArray_applyIdx_returnsElement(): Unit = {
    val a = Attribute(name = "doc", value = None)
    val arr = AttributeArray(a)
    assert(arr(0).name === "doc")
  }

  @Test
  def attributeArray_copy_withDataList(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    val a = Attribute(name = "x", value = None)
    val arr = AttributeArray(a)
    val copied = arr.copy(arr.data(), DataConversion.SetReadOnly)
    assert(copied.length === 1)
  }

  @Test
  def attributeArray_applyIterable_createsArray(): Unit = {
    val a = Attribute(name = "y", value = None)
    val arr = AttributeArray(List(a))
    assert(arr.length === 1)
    assert(arr(0).name === "y")
  }

  // ─── ParameterArray indexing and copy / apply-Iterable ───────────────────

  @Test
  def parameterArray_applyIdx_returnsElement(): Unit = {
    val p = Parameter(name = "limit", `type` = "int", attributes = AttributeArray())
    val arr = ParameterArray(p)
    assert(arr(0).name === "limit")
  }

  @Test
  def parameterArray_copy_withDataList(): Unit = {
    import org.coursera.courier.templates.DataTemplates.DataConversion
    val p = Parameter(name = "offset", `type` = "int", attributes = AttributeArray())
    val arr = ParameterArray(p)
    val copied = arr.copy(arr.data(), DataConversion.SetReadOnly)
    assert(copied.length === 1)
  }

  @Test
  def parameterArray_applyIterable_createsArray(): Unit = {
    val p = Parameter(name = "q", `type` = "string", attributes = AttributeArray())
    val arr = ParameterArray(List(p))
    assert(arr.length === 1)
    assert(arr(0).name === "q")
  }
}
