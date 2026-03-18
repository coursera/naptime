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

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.test.FakeRequest

import scala.util.Success
import scala.util.Failure

class ModelsTest extends AssertionsForJUnit {

  // ─── RequestPagination ─────────────────────────────────────────────────────

  @Test
  def requestPagination_startAsInt_valid(): Unit = {
    val rp = RequestPagination(10, Some("5"), isDefault = false)
    assert(rp.startAsInt === Some(5))
  }

  @Test
  def requestPagination_startAsInt_none(): Unit = {
    val rp = RequestPagination(10, None, isDefault = false)
    assert(rp.startAsInt === None)
  }

  @Test
  def requestPagination_startAsInt_invalid_throwsBadRequest(): Unit = {
    val rp = RequestPagination(10, Some("notANumber"), isDefault = false)
    intercept[NaptimeActionException] {
      rp.startAsInt
    }
  }

  @Test
  def requestPagination_eTagHashCode_isStable(): Unit = {
    val rp = RequestPagination(10, Some("5"), isDefault = false)
    assert(rp.eTagHashCode() === rp.eTagHashCode())
  }

  @Test
  def requestPagination_fromRequestHeader_withLimit(): Unit = {
    val rh = FakeRequest("GET", "/foo?limit=20&start=10")
    val rp = RequestPagination(rh, PaginationConfiguration())
    assert(rp.limit === 20)
    assert(rp.start === Some("10"))
    assert(rp.isDefault === false)
  }

  @Test
  def requestPagination_fromRequestHeader_defaultLimit(): Unit = {
    val rh = FakeRequest("GET", "/foo")
    val rp = RequestPagination(rh, PaginationConfiguration(defaultLimit = 50))
    assert(rp.limit === 50)
    assert(rp.start === None)
    assert(rp.isDefault === true)
  }

  @Test
  def requestPagination_fromRequestHeader_invalidLimit_usesDefault(): Unit = {
    val rh = FakeRequest("GET", "/foo?limit=notanumber")
    val rp = RequestPagination(rh, PaginationConfiguration(defaultLimit = 100))
    assert(rp.limit === 100)
    assert(rp.isDefault === true)
  }

  // ─── QueryIncludes ─────────────────────────────────────────────────────────

  @Test
  def queryIncludes_apply_empty(): Unit = {
    val result = QueryIncludes("")
    assert(result === Success(QueryIncludes.empty))
  }

  @Test
  def queryIncludes_apply_simpleField(): Unit = {
    val result = QueryIncludes("authorId")
    assert(result.isSuccess)
    assert(result.get.fields.contains("authorId"))
  }

  @Test
  def queryIncludes_apply_resourceName(): Unit = {
    val result = QueryIncludes("users.v1(name,id)")
    assert(result.isSuccess)
    val includes = result.get
    assert(includes.resources.nonEmpty)
  }

  @Test
  def queryIncludes_apply_invalidInput(): Unit = {
    val result = QueryIncludes(",bad")
    assert(result.isFailure)
  }

  @Test
  def queryIncludes_includeFieldsRelatedResource(): Unit = {
    val includes = QueryIncludes(Set("authorId", "commentIds"), Map.empty)
    assert(includes.includeFieldsRelatedResource("authorId") === true)
    assert(includes.includeFieldsRelatedResource("missing") === false)
  }

  @Test
  def queryIncludes_forResource_found(): Unit = {
    val rn = ResourceName("users", 1)
    val includes = QueryIncludes(Set.empty, Map(rn -> Set("name")))
    val sub = includes.forResource(rn)
    assert(sub.isDefined)
    assert(sub.get.fields === Set("name"))
  }

  @Test
  def queryIncludes_forResource_notFound(): Unit = {
    val includes = QueryIncludes(Set.empty, Map.empty)
    val sub = includes.forResource(ResourceName("missing", 1))
    assert(sub === None)
  }

  // ─── AllFields ─────────────────────────────────────────────────────────────

  @Test
  def allFields_hasField_alwaysTrue(): Unit = {
    assert(AllFields.hasField("anything") === true)
    assert(AllFields.hasField("somethingElse") === true)
  }

  @Test
  def allFields_forResource_returnsSome(): Unit = {
    val rn = ResourceName("users", 1)
    assert(AllFields.forResource(rn) === Some(AllFields))
  }

  @Test
  def allFields_mergeWithDefaults_returnsSelf(): Unit = {
    val merged = AllFields.mergeWithDefaults(Set("a", "b"))
    assert(merged === AllFields)
  }

  // ─── DelegateFields ────────────────────────────────────────────────────────

  @Test
  def delegateFields_hasField_delegatesToDelegate(): Unit = {
    val inner = QueryFields(Set("name", "id"), Map.empty)
    val delegate = DelegateFields(inner, Map.empty)
    assert(delegate.hasField("name") === true)
    assert(delegate.hasField("missing") === false)
  }

  @Test
  def delegateFields_forResource_returnsFromMap(): Unit = {
    val rn = ResourceName("users", 1)
    val inner = QueryFields(Set("name"), Map.empty)
    val resourceFields = QueryFields(Set("email"), Map.empty)
    val delegate = DelegateFields(inner, Map(rn -> resourceFields))
    assert(delegate.forResource(rn) === Some(resourceFields))
  }

  @Test
  def delegateFields_forResource_missingResource_returnsNone(): Unit = {
    val inner = QueryFields(Set("name"), Map.empty)
    val delegate = DelegateFields(inner, Map.empty)
    assert(delegate.forResource(ResourceName("missing", 1)) === None)
  }

  @Test
  def delegateFields_mergeWithDefaults_delegatesInner(): Unit = {
    val inner = QueryFields(Set("name"), Map.empty)
    val delegate = DelegateFields(inner, Map.empty)
    val merged = delegate.mergeWithDefaults(Set("id", "name"))
    // "name" is in both, defaults add "id"
    assert(merged.asInstanceOf[DelegateFields].delegate.hasField("id") === true)
  }

  // ─── GraphQL Relations ─────────────────────────────────────────────────────

  @Test
  @deprecated("testing deprecated API", "always")
  def multiGetGraphQLRelation_toAnnotation(): Unit = {
    val rn = ResourceName("users", 1)
    val relation = MultiGetGraphQLRelation(rn, "authorIds")
    val annotation = relation.toAnnotation
    assert(annotation.resourceName === rn.identifier)
    assert(annotation.arguments.get("ids") === Some("authorIds"))
  }

  @Test
  @deprecated("testing deprecated API", "always")
  def singleElementFinderGraphQLRelation_toAnnotation(): Unit = {
    val rn = ResourceName("users", 1)
    val relation = SingleElementFinderGraphQLRelation(rn, "byEmail")
    val annotation = relation.toAnnotation
    assert(annotation.resourceName === rn.identifier)
    assert(annotation.arguments.get("q") === Some("byEmail"))
  }

  @Test
  @deprecated("testing deprecated API", "always")
  def finderGraphQLRelation_toAnnotation(): Unit = {
    val rn = ResourceName("posts", 2)
    val relation = FinderGraphQLRelation(rn, "byAuthor", Map("extra" -> "value"))
    val annotation = relation.toAnnotation
    assert(annotation.resourceName === rn.identifier)
    assert(annotation.arguments.get("q") === Some("byAuthor"))
    assert(annotation.arguments.get("extra") === Some("value"))
  }

  @Test
  @deprecated("testing deprecated API", "always")
  def getGraphQLRelation_toAnnotation(): Unit = {
    val rn = ResourceName("courses", 1)
    val relation = GetGraphQLRelation(rn, "courseId")
    val annotation = relation.toAnnotation
    assert(annotation.resourceName === rn.identifier)
    assert(annotation.arguments.get("ids") === Some("courseId"))
  }
}
