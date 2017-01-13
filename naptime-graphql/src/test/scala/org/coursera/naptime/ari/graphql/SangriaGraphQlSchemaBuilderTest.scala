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

package org.coursera.naptime.ari.graphql

import org.coursera.courier.templates.ScalaRecordTemplate
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.Parameter
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import sangria.schema.EnumType
import sangria.schema.ObjectType
import sangria.schema.Schema
import sangria.schema.UnionType

class SangriaGraphQlSchemaBuilderTest extends AssertionsForJUnit {

  val allResources = Set(Models.courseResource, Models.instructorResource, Models.partnersResource)

  val schemaTypes = Map(
    "org.coursera.naptime.ari.graphql.models.MergedCourse" -> MergedCourse.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedInstructor" -> MergedInstructor.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedPartner" -> MergedPartner.SCHEMA)

  val builder = new SangriaGraphQlSchemaBuilder(allResources, schemaTypes)

  @Test
  def parseTopLevelFields(): Unit = {
    val schema = Schema(builder.generateLookupTypeForResource("courses.v1").get)
    val (_, courseResourceType) = schema.types.get("CoursesV1").get
    val courseResourceObjectType =
      courseResourceType.asInstanceOf[ObjectType[Unit, ScalaRecordTemplate]]
    val fieldNames = courseResourceObjectType.fieldsByName.keySet
    val expectedFieldNames = Set(
      "id",
      "name",
      "description",
      "slug",
      "instructors",
      "originalId",
      "platformSpecificData",
      "partner",
      "coursePlatform",
      "arbitraryData")
    assert(fieldNames === expectedFieldNames)
  }

  @Test
  def parseUnionFields(): Unit = {
    val schema = Schema(builder.generateLookupTypeForResource("courses.v1").get)
    val courseUnionType = schema.unionTypes("CoursesV1_originalId")
    val courseUnionUnionType = courseUnionType.asInstanceOf[UnionType[Unit]]
    val unionObjects = courseUnionUnionType.types
    assert(unionObjects.find(_.name == "CoursesV1_intMember").get.fieldsByName.keySet.head === "int")
    assert(unionObjects.find(_.name == "CoursesV1_stringMember").get.fieldsByName.keySet.head === "string")
  }

  @Test
  def parseUnionMemberFields(): Unit = {
    val schema = Schema(builder.generateLookupTypeForResource("courses.v1").get)
    val (_, coursePlatformMemberType) = schema.types("CoursesV1_intMember")
    val coursePlatformMemberObjectType =
      coursePlatformMemberType.asInstanceOf[ObjectType[Unit, ScalaRecordTemplate]]
    val fieldNames = coursePlatformMemberObjectType.fieldsByName.keySet
    val expectedFieldNames = Set("int")
    assert(fieldNames === expectedFieldNames)
  }

}
