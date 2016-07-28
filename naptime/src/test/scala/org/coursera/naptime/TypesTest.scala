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

import com.linkedin.data.schema.IntegerDataSchema
import com.linkedin.data.schema.StringDataSchema
import org.coursera.naptime.actions.Course
import org.coursera.naptime.actions.EnrollmentId
import org.coursera.naptime.actions.SessionId
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class TypesTest extends AssertionsForJUnit {

  @Test
  def primitiveSchema(): Unit = {
    val resultingType = Types.computeAsymType(
      "org.coursera.naptime.TestResource.Model",
      new IntegerDataSchema,
      Course.SCHEMA,
      Fields.FAKE_FIELDS)

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
      Fields.FAKE_FIELDS)

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
}
