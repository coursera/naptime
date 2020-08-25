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

import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.IntegerDataSchema
import com.linkedin.data.schema.StringDataSchema
import org.coursera.naptime.actions.Course
import org.coursera.naptime.actions.EnrollmentId
import org.coursera.naptime.actions.SessionId
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

import scala.collection.JavaConverters._

class TypesTest extends AssertionsForJUnit {

  @Test
  def primitiveSchema(): Unit = {
    val resultingType = Types.computeAsymType(
      "org.coursera.naptime.TestResource.Model",
      new IntegerDataSchema,
      Course.SCHEMA,
      ResourceFields.FAKE_FIELDS)

    assert(!resultingType.isErrorRecord)
    assert(resultingType.getFields().size() == 3)
    assert(resultingType.getField("id") != null)
    assert(resultingType.getField("id").getRecord == resultingType)
    assert(resultingType.getField("id").getType == new IntegerDataSchema)
    assert(resultingType.getField("name") != null)
    assert(resultingType.getField("name").getRecord == Course.SCHEMA)
    assert(resultingType.getField("description") != null)
    assert(resultingType.getField("description").getRecord == Course.SCHEMA)
  }

  @Test
  def complexSchema(): Unit = {
    val resultingType = Types.computeAsymType(
      "org.coursera.naptime.ComplexTestResource.Model",
      EnrollmentId.SCHEMA,
      Course.SCHEMA,
      ResourceFields.FAKE_FIELDS)

    assert(!resultingType.isErrorRecord)
    assert(resultingType.getFields().size() == 5)
    assert(resultingType.getField("id") != null)
    assert(resultingType.getField("id").getRecord == resultingType)
    assert(resultingType.getField("id").getType == new StringDataSchema)
    assert(resultingType.getField("userId") != null)
    assert(resultingType.getField("userId").getRecord == EnrollmentId.SCHEMA)
    assert(resultingType.getField("userId").getType == new IntegerDataSchema)
    assert(resultingType.getField("courseId") != null)
    assert(resultingType.getField("courseId").getRecord == EnrollmentId.SCHEMA)
    assert(resultingType.getField("courseId").getType == SessionId.SCHEMA)
    assert(resultingType.getField("name") != null)
    assert(resultingType.getField("name").getRecord == Course.SCHEMA)
    assert(resultingType.getField("description") != null)
    assert(resultingType.getField("description").getRecord == Course.SCHEMA)
  }

  @Test
  def idWithIdField(): Unit = {
    val resultingType = Types.computeAsymType(
      "org.coursera.naptime.IdWithIdTestResource.Model",
      IdWithIdField.SCHEMA,
      Course.SCHEMA,
      ResourceFields.FAKE_FIELDS)

    assert(!resultingType.isErrorRecord)
    assert(resultingType.getFields().size() == 4)
    assert(resultingType.getField("id") != null)
    assert(resultingType.getField("id").getRecord == IdWithIdField.SCHEMA)
    assert(resultingType.getField("id").getType == new IntegerDataSchema)
    assert(resultingType.getField("alias") != null)
    assert(resultingType.getField("alias").getRecord == IdWithIdField.SCHEMA)
    assert(resultingType.getField("alias").getType == new StringDataSchema)
    assert(resultingType.getField("name") != null)
    assert(resultingType.getField("name").getRecord == Course.SCHEMA)
    assert(resultingType.getField("description") != null)
    assert(resultingType.getField("description").getRecord == Course.SCHEMA)
  }

  @Test
  def relations(): Unit = {
    val includeOnlyResourceName = ResourceName("includeOnly", 1)
    val includeSharedResourceName = ResourceName("shared", 2)
    val includeRelations =
      Map("includeOnly" -> includeOnlyResourceName, "shared" -> includeSharedResourceName)
    val graphQLOnlyResourceName = ResourceName("graphQLOnly", 1)
    val graphQLOnly =
      FinderGraphQLRelation(
        graphQLOnlyResourceName,
        "find",
        Map("foo" -> "bar"),
        "greetings from graphql")
    val graphQLSharedResourceName = ResourceName("shared", 2)
    val graphQLShared =
      GetGraphQLRelation(
        graphQLSharedResourceName,
        "get",
        Map("foo" -> "bar"),
        "greetings from graphql")
    val graphQLRelations = Map(
      "graphQLOnly" -> graphQLOnly,
      "shared" -> graphQLShared
    )
    val resultingType = Types.computeAsymType(
      "org.coursera.naptime.Relations.Model",
      Empty.SCHEMA,
      Empty.SCHEMA,
      ResourceFields(Set.empty, FieldsFunction.default, includeRelations, graphQLRelations)(null))

    val fieldsExpectedNames = List("id", "includeOnly", "shared", "graphQLOnly")
    val includeOnlyExpectedProperties = Map("includes" -> includeOnlyResourceName.toAnnotation.data)
    val sharedExpectedProperties = Map(
      "includes" -> includeSharedResourceName.toAnnotation.data,
      "relatedOn" -> graphQLShared.toAnnotation.data
    )
    val graphQLOnlyExpectedProperties = Map("relatedOn" -> graphQLOnly.toAnnotation.data)

    assert(!resultingType.isErrorRecord)
    assert(resultingType.getFields.asScala.map(_.getName) === fieldsExpectedNames)
    val includeOnlyField = resultingType.getField("includeOnly")
    assert(includeOnlyField != null)
    assert(!includeOnlyField.getOptional)
    assert(includeOnlyField.getType == new ArrayDataSchema(new StringDataSchema))
    assert(includeOnlyField.getProperties.asScala === includeOnlyExpectedProperties)
    val sharedField = resultingType.getField("shared")
    assert(sharedField != null)
    assert(sharedField.getOptional)
    assert(sharedField.getType == new StringDataSchema)
    assert(sharedField.getProperties.asScala === sharedExpectedProperties)
    val graphQLOnlyField = resultingType.getField("graphQLOnly")
    assert(graphQLOnlyField != null)
    assert(!graphQLOnlyField.getOptional)
    assert(graphQLOnlyField.getType == new ArrayDataSchema(new StringDataSchema))
    assert(graphQLOnlyField.getProperties.asScala === graphQLOnlyExpectedProperties)
  }
}
