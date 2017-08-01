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
import org.coursera.naptime.schema.RelationType
import org.coursera.naptime.schema.ReverseRelationAnnotation
import play.api.libs.json.JsString

class NaptimeResourceUtilsTest extends AssertionsForJUnit with MockitoSugar {

  val testCourse = DataMapWithParent(
    Models.COURSE_A.data(),
    ParentModel(ResourceName("courses", 1), Models.COURSE_A.data(), MergedCourse.SCHEMA))

  val finderRelation = ReverseRelationAnnotation(
    resourceName = "courses.v1",
    arguments = StringMap(Map("id" -> "COURSE~$id", "q" -> "byId")),
    relationType = RelationType.FINDER)

  @Test
  def interpolateArguments(): Unit = {
    val interpolatedArguments =
      NaptimeResourceUtils.interpolateArguments(testCourse, finderRelation).toMap
    assert(interpolatedArguments("id") === JsString(s"COURSE~${Models.COURSE_A.id}"))
    assert(interpolatedArguments("q") === JsString("byId"))
  }

}
