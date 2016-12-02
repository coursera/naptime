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

import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.junit.Test
import org.mockito.Mockito.when
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import sangria.schema.Args

class NaptimePaginatedResourceFieldTest extends AssertionsForJUnit with MockitoSugar {

  val fieldName = "relatedIds"
  val resourceName = "courses.v1"
  val context = SangriaGraphQlContext(Response.empty)

  val schemaMetadata = mock[SchemaMetadata]
  val resource = Models.courseResource
  when(schemaMetadata.getResourceOpt(resourceName)).thenReturn(Some(resource))
  when(schemaMetadata.getSchema(resource)).thenReturn(Some(null))

  @Test
  def computeComplexity(): Unit = {
    val field = NaptimePaginatedResourceField.build(
      schemaMetadata, resourceName, fieldName, None, None)

    val limitTen = field.get.complexity.get.apply(context, Args(Map("limit" -> 10)), 1)
    assert(limitTen === 1 * NaptimePaginatedResourceField.COMPLEXITY_COST * 1)

    val limitFifty = field.get.complexity.get.apply(context, Args(Map("limit" -> 50)), 1)
    assert(limitFifty === 5 * NaptimePaginatedResourceField.COMPLEXITY_COST * 1)

    val limitZero = field.get.complexity.get.apply(context, Args(Map("limit" -> 0)), 1)
    assert(limitZero === 1 * NaptimePaginatedResourceField.COMPLEXITY_COST * 1)

    val childScoreFive = field.get.complexity.get.apply(context, Args(Map("limit" -> 1)), 5)
    assert(childScoreFive === 1 * NaptimePaginatedResourceField.COMPLEXITY_COST * 5)

  }

}
