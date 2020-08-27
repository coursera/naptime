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

import com.linkedin.data.schema.{
  ArrayDataSchema,
  IntegerDataSchema,
  NullDataSchema,
  StringDataSchema
}
import org.coursera.naptime.actions.Course
import org.coursera.naptime.actions.EnrollmentId
import org.coursera.naptime.actions.SessionId
import org.coursera.naptime.courier.CourierFormats
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.OFormat

import scala.collection.JavaConverters._

class TypesTest extends AssertionsForJUnit {

  implicit val emptyFormat: OFormat[Empty] = CourierFormats.recordTemplateFormats[Empty]

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
    val resourceFields = ResourceFields[Empty]
      .withRelated(
        "includeOnly" -> ResourceName("includeOnly", 1),
        "shared" -> ResourceName("shared", 2))
      .withGraphQLRelations(
        "gqlOnly" -> FinderGraphQLRelation(ResourceName("gqlOnly", 1), "find"),
        "shared" -> GetGraphQLRelation(ResourceName("shared", 2), "get")
      )
    val resultingType =
      Types.computeAsymType("Relations", Empty.SCHEMA, Empty.SCHEMA, resourceFields)

    val fieldsExpectedNames = List("id", "includeOnly", "shared", "gqlOnly")
    assert(resultingType.getFields.asScala.map(_.getName) === fieldsExpectedNames)
    val includeOnlyField = resultingType.getField("includeOnly")
    val includeOnlyExpectedProperties = Map(
      "included" -> ResourceName("includeOnly", 1).toAnnotation.data)
    assert(includeOnlyField != null)
    assert(includeOnlyField.getOptional)
    assert(includeOnlyField.getType == new NullDataSchema())
    assert(includeOnlyField.getProperties.asScala === includeOnlyExpectedProperties)
    val sharedScalarField = resultingType.getField("shared")
    val sharedExpectedProperties = Map(
      "included" -> ResourceName("shared", 2).toAnnotation.data,
      "relatedOn" -> GetGraphQLRelation(ResourceName("shared", 2), "get").toAnnotation.data
    )
    assert(sharedScalarField != null)
    assert(sharedScalarField.getOptional)
    assert(sharedScalarField.getType == new StringDataSchema)
    assert(sharedScalarField.getProperties.asScala === sharedExpectedProperties)
    val graphQLOnlyArrayField = resultingType.getField("gqlOnly")
    val gqlOnlyExpectedProperties = Map(
      "relatedOn" -> FinderGraphQLRelation(ResourceName("gqlOnly", 1), "find").toAnnotation.data)
    assert(graphQLOnlyArrayField != null)
    assert(!graphQLOnlyArrayField.getOptional)
    assert(graphQLOnlyArrayField.getType == new ArrayDataSchema(new StringDataSchema))
    assert(graphQLOnlyArrayField.getProperties.asScala === gqlOnlyExpectedProperties)
  }
}
