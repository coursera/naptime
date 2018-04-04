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

import org.coursera.courier.templates.ScalaRecordTemplate
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedMultigetFreeEntity
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import sangria.schema.ObjectType
import sangria.schema.Schema
import sangria.schema.UnionType

class NaptimeTopLevelResourceFieldTest extends AssertionsForJUnit {

  val allResources = Set(
    Models.courseResource,
    Models.instructorResource,
    Models.partnersResource,
    Models.multigetFreeEntity)

  val schemaTypes = Map(
    "org.coursera.naptime.ari.graphql.models.MergedCourse" -> MergedCourse.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedInstructor" -> MergedInstructor.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedPartner" -> MergedPartner.SCHEMA,
    "org.coursera.naptime.ari.graphql.models.MergedMultigetFreeEntity" -> MergedMultigetFreeEntity.SCHEMA)

  val schemaMetadata = SchemaMetadata(allResources, schemaTypes)

  @Test
  def parseTopLevelFields(): Unit = {
    val courseResourceField = NaptimeTopLevelResourceField
      .generateLookupTypeForResource(Models.courseResource, schemaMetadata)
      .data
      .get
    val schema = Schema(courseResourceField)
    val (_, courseResourceType) = schema.types("CoursesV1")
    val courseResourceObjectType =
      courseResourceType.asInstanceOf[ObjectType[Unit, ScalaRecordTemplate]]
    val fieldNames = courseResourceObjectType.fieldsByName.keySet
    val expectedFieldNames = Set(
      "id",
      "name",
      "description",
      "slug",
      "instructorIds",
      "originalId",
      "platformSpecificData",
      "partnerId",
      "coursePlatform",
      "arbitraryData")
    val expectedServiceName = Some("Attributes:\nservice -> myService")

    assert(fieldNames === expectedFieldNames)
    assert(courseResourceField.description === expectedServiceName)
  }

  @Test
  def parseUnionFields(): Unit = {
    val courseResourceField = NaptimeTopLevelResourceField
      .generateLookupTypeForResource(Models.courseResource, schemaMetadata)
      .data
      .get
    val schema = Schema(courseResourceField)
    val courseUnionType = schema.unionTypes("CoursesV1_originalId")
    val courseUnionUnionType = courseUnionType.asInstanceOf[UnionType[Unit]]
    val unionObjects = courseUnionUnionType.types
    assert(
      unionObjects.find(_.name == "CoursesV1_intMember").get.fieldsByName.keySet.head === "int")
    assert(
      unionObjects
        .find(_.name == "CoursesV1_stringMember")
        .get
        .fieldsByName
        .keySet
        .head === "string")
  }

  @Test
  def parseUnionMemberFields(): Unit = {
    val courseResourceField = NaptimeTopLevelResourceField
      .generateLookupTypeForResource(Models.courseResource, schemaMetadata)
      .data
      .get
    val schema = Schema(courseResourceField)
    val (_, coursePlatformMemberType) = schema.types("CoursesV1_intMember")
    val coursePlatformMemberObjectType =
      coursePlatformMemberType.asInstanceOf[ObjectType[Unit, ScalaRecordTemplate]]
    val fieldNames = coursePlatformMemberObjectType.fieldsByName.keySet
    val expectedFieldNames = Set("int")
    assert(fieldNames === expectedFieldNames)
  }

  @Test
  def filtersTopLevelFieldsWithoutMultiget(): Unit = {
    val multigetFreeFieldAndErrors = NaptimeTopLevelResourceField
      .generateLookupTypeForResource(Models.multigetFreeEntity, schemaMetadata)

    val multigetFreeField = multigetFreeFieldAndErrors.data.get
    val resourceName = ResourceName.fromResource(Models.multigetFreeEntity)

    assert(multigetFreeField.fields.map(_.name).toSet === Set("finder", "getAll"))
    assert(multigetFreeFieldAndErrors.errors.errors.head === HasGetButMissingMultiGet(resourceName))
  }

}
