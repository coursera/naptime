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

import org.coursera.courier.data.StringMap
import org.coursera.naptime.ResourceName
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedCourses
import org.coursera.naptime.schema.RelationType
import org.coursera.naptime.schema.ReverseRelationAnnotation
import play.api.libs.json.JsArray
import play.api.libs.json.JsString

class NaptimeResourceUtilsTest extends AssertionsForJUnit with MockitoSugar {

  @Test
  def interpolateArgumentsSingleElement(): Unit = {

    val testCourse = DataMapWithParent(
      Models.COURSE_A.data(),
      ParentModel(ResourceName("courses", 1), Models.COURSE_A.data(), MergedCourse.SCHEMA))

    val finderRelation = ReverseRelationAnnotation(
      resourceName = "courses.v1",
      arguments = StringMap(Map("id" -> "COURSE~$id", "q" -> "byId")),
      relationType = RelationType.FINDER)

    val interpolatedArguments =
      NaptimeResourceUtils.interpolateArguments(testCourse, finderRelation).toMap
    assert(interpolatedArguments("id") === JsString(s"COURSE~${Models.COURSE_A.id}"))
    assert(interpolatedArguments("q") === JsString("byId"))
  }

  @Test
  def interpolateArgumentsMultipleIds(): Unit = {
    val testCourse = DataMapWithParent(
      Models.COURSE_A.data(),
      ParentModel(ResourceName("courses", 1), Models.COURSE_A.data(), MergedCourse.SCHEMA))

    val getRelation = ReverseRelationAnnotation(
      resourceName = "coursePartners.v1",
      arguments = StringMap(Map("id" -> "$id~$partnerId")),
      relationType = RelationType.GET)

    val interpolatedArguments =
      NaptimeResourceUtils.interpolateArguments(testCourse, getRelation).toMap
    assert(
      interpolatedArguments("id") === JsString(
        s"${Models.COURSE_A.id}~${Models.COURSE_A.partnerId}"))
  }

  @Test
  def interpolateArgumentsMultipleElementsFromArray(): Unit = {

    val testCourse = DataMapWithParent(
      Models.COURSE_A.data(),
      ParentModel(ResourceName("instructors", 1), Models.COURSE_A.data(), MergedCourse.SCHEMA))

    val finderRelation = ReverseRelationAnnotation(
      resourceName = "instructors.v1",
      arguments = StringMap(Map("ids" -> "INSTRUCTOR~${instructorIds}")),
      relationType = RelationType.MULTI_GET)

    val interpolatedArguments =
      NaptimeResourceUtils.interpolateArguments(testCourse, finderRelation).toMap
    val expectedInstructorIds = Models.COURSE_A.instructorIds.map(id => JsString(s"INSTRUCTOR~$id"))
    assert(interpolatedArguments("ids") === JsArray(expectedInstructorIds))
  }

  @Test
  def interpolateArgumentsMultipleElementsAndMultipleIds(): Unit = {
    val testCourse = DataMapWithParent(
      Models.COURSE_A.data(),
      ParentModel(
        ResourceName("courseInstructors", 1),
        Models.COURSE_A.data(),
        MergedCourse.SCHEMA))

    val multiGetRelation = ReverseRelationAnnotation(
      resourceName = "courseInstructors.v1",
      arguments = StringMap(Map("ids" -> "COURSE~${id}~INSTRUCTOR~${instructorIds}")),
      relationType = RelationType.MULTI_GET)

    val interpolatedArguments =
      NaptimeResourceUtils.interpolateArguments(testCourse, multiGetRelation).toMap
    val expectedIds = JsArray(
      List(
        JsString(s"COURSE~${Models.COURSE_A.id}~INSTRUCTOR~${Models.COURSE_A.instructorIds(0)}"),
        JsString(s"COURSE~${Models.COURSE_A.id}~INSTRUCTOR~${Models.COURSE_A.instructorIds(1)}")
      ))
    assert(interpolatedArguments("ids") === expectedIds)
  }

  @Test
  def interpolateArgumentsMultipleElementsFromArrayOfRecords(): Unit = {

    val testCourse = DataMapWithParent(
      Models.COURSES.data(),
      ParentModel(ResourceName("courses", 1), Models.COURSES.data(), MergedCourses.SCHEMA))

    val multiGetRelation = ReverseRelationAnnotation(
      resourceName = "courses.v1",
      arguments = StringMap(Map("ids" -> "COURSE~${courses/id}")),
      relationType = RelationType.MULTI_GET)

    val interpolatedArguments =
      NaptimeResourceUtils.interpolateArguments(testCourse, multiGetRelation).toMap
    val expectedCourseIds = JsArray(
      List(JsString(s"COURSE~${Models.COURSE_A.id}"), JsString(s"COURSE~${Models.COURSE_B.id}")))
    assert(interpolatedArguments("ids") === expectedCourseIds)
  }

  @Test
  def interpolateArgumentsMultipleElementsWithTypedDefinitions(): Unit = {

    val testCourse = DataMapWithParent(
      Models.COURSES.data(),
      ParentModel(ResourceName("oldCourses", 1), Models.COURSES.data(), MergedCourses.SCHEMA))

    val multiGetRelation = ReverseRelationAnnotation(
      resourceName = "oldCourses.v1",
      arguments =
        StringMap(Map("ids" -> "OLD_COURSE~${courses/platformSpecificData/old/oldPlatformId}")),
      relationType = RelationType.MULTI_GET)

    val interpolatedArguments =
      NaptimeResourceUtils.interpolateArguments(testCourse, multiGetRelation).toMap
    val expectedCourseIds = JsArray(
      List(
        JsString(s"OLD_COURSE~${Models.oldCourseIdA}"),
        JsString(s"OLD_COURSE~${Models.oldCourseIdB}")))
    assert(interpolatedArguments("ids") === expectedCourseIds)
  }
}
