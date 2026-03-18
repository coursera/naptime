/*
 * Copyright 2024 Coursera Inc.
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

import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.resolvers.NaptimeError
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

class SchemaErrorsTest extends AssertionsForJUnit {

  private val resource1 = ResourceName("courses", 1)
  private val resource2 = ResourceName("instructors", 1)

  // ─── SchemaErrors container ───────────────────────────────────────────────────

  @Test def schemaErrors_empty_hasNoErrors(): Unit = {
    assertResult(0)(SchemaErrors.empty.errors.size)
  }

  @Test def schemaErrors_plusSingle_appendsError(): Unit = {
    val err = SchemaNotFound(resource1)
    val result = SchemaErrors.empty + err
    assertResult(1)(result.errors.size)
    assertResult(err)(result.errors.head)
  }

  @Test def schemaErrors_plusPlusList_appendsAll(): Unit = {
    val e1 = SchemaNotFound(resource1)
    val e2 = NoHandlersAvailable(resource2)
    val result = SchemaErrors.empty ++ List(e1, e2)
    assertResult(2)(result.errors.size)
  }

  @Test def schemaErrors_plusPlusSchemaErrors_merges(): Unit = {
    val left = SchemaErrors.empty + SchemaNotFound(resource1)
    val right = SchemaErrors.empty + NoHandlersAvailable(resource2)
    val merged = left ++ right
    assertResult(2)(merged.errors.size)
  }

  @Test def withSchemaErrors_defaultIsEmpty(): Unit = {
    val wrapped = WithSchemaErrors("data")
    assertResult("data")(wrapped.data)
    assertResult(0)(wrapped.errors.errors.size)
  }

  @Test def withSchemaErrors_withErrors(): Unit = {
    val err = SchemaNotFound(resource1)
    val wrapped = WithSchemaErrors("data", SchemaErrors.empty + err)
    assertResult(1)(wrapped.errors.errors.size)
  }

  // ─── Individual SchemaError subtypes ─────────────────────────────────────────

  @Test def hasGetButMissingMultiGet_fields(): Unit = {
    val e = HasGetButMissingMultiGet(resource1)
    assertResult(resource1)(e.resourceName)
    assertResult("HAS_GET_BUT_MISSING_MULTIGET")(e.key)
    assert(e.message.nonEmpty)
  }

  @Test def noHandlersAvailable_fields(): Unit = {
    val e = NoHandlersAvailable(resource1)
    assertResult(resource1)(e.resourceName)
    assertResult("NO_HANDLERS_AVAILABLE")(e.key)
    assert(e.message.nonEmpty)
  }

  @Test def missingMergedType_fields(): Unit = {
    val e = MissingMergedType(resource1)
    assertResult(resource1)(e.resourceName)
    assertResult("MISSING_MERGED_TYPE")(e.key)
    assert(e.message.nonEmpty)
  }

  @Test def hasForwardRelationButMissingMultiGet_fields(): Unit = {
    val e = HasForwardRelationButMissingMultiGet(resource1, "instructorId")
    assertResult(resource1)(e.resourceName)
    assertResult("HAS_FORWARD_RELATION_BUT_MISSING_MULTIGET")(e.key)
    assert(e.message.contains("instructorId"))
  }

  @Test def unknownHandlerType_fields(): Unit = {
    val e = UnknownHandlerType(resource1, "WEIRD_HANDLER")
    assertResult(resource1)(e.resourceName)
    assertResult("UNKNOWN_HANDLER_TYPE")(e.key)
    assert(e.message.contains("WEIRD_HANDLER"))
  }

  @Test def schemaNotFound_fields(): Unit = {
    val e = SchemaNotFound(resource1)
    assertResult(resource1)(e.resourceName)
    assertResult("SCHEMA_NOT_FOUND")(e.key)
    assert(e.message.nonEmpty)
  }

  @Test def missingQParameterOnFinderRelation_fields(): Unit = {
    val e = MissingQParameterOnFinderRelation(resource1, "courseId")
    assertResult(resource1)(e.resourceName)
    assertResult("MISSING_Q_PARAMETER")(e.key)
    assert(e.message.contains("courseId"))
  }

  @Test def unhandledSchemaError_fields(): Unit = {
    val e = UnhandledSchemaError(resource1, "something unexpected")
    assertResult(resource1)(e.resourceName)
    assertResult("UNHANDLED_SCHEMA_ERROR")(e.key)
    assert(e.message.contains("something unexpected"))
  }

  // ─── Schema exceptions ────────────────────────────────────────────────────────

  @Test def schemaGenerationException_message(): Unit = {
    val e = SchemaGenerationException("bad schema")
    assertResult("bad schema")(e.getMessage)
  }

  @Test def schemaExecutionException_message(): Unit = {
    val e = SchemaExecutionException("exec failure")
    assertResult("exec failure")(e.getMessage)
  }

  @Test def responseFormatException_message(): Unit = {
    val e = ResponseFormatException("bad format")
    assertResult("bad format")(e.getMessage)
  }

  @Test def notFoundException_message(): Unit = {
    val e = NotFoundException("not found")
    assertResult("not found")(e.getMessage)
  }

  @Test def naptimeResolveException_delegatesToNaptimeError(): Unit = {
    val err = NaptimeError("/api/test.v1", 404, "resource not found")
    val e = NaptimeResolveException(err)
    assertResult("resource not found")(e.getMessage)
    assertResult(err)(e.naptimeError)
  }
}
