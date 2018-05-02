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

import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.helpers.ArgumentBuilder
import org.junit.Test
import org.mockito.Mockito.when
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.ExecutionContext

class NaptimePaginatedResourceFieldTest extends AssertionsForJUnit with MockitoSugar {

  val fieldName = "relatedIds"
  val resourceName = ResourceName("courses", 1)
  val context = SangriaGraphQlContext(null, null, ExecutionContext.global, debugMode = false)

  private[this] val schemaMetadata = mock[SchemaMetadata]
  private[this] val resource = Models.courseResource
  when(schemaMetadata.getResourceOpt(resourceName)).thenReturn(Some(resource))
  when(schemaMetadata.getSchema(resource)).thenReturn(Some(null))

  @Test
  def computeComplexity(): Unit = {
    val field = NaptimePaginatedResourceField.build(
      schemaMetadata,
      resourceName,
      fieldName,
      None,
      None,
      List.empty)

    val argDefinitions = NaptimePaginationField.paginationArguments

    val limitTen = field.right.get.complexity.get
      .apply(context, ArgumentBuilder.buildArgs(argDefinitions, Map("limit" -> Some(10))), 1)
    assert(limitTen === 1 * NaptimePaginatedResourceField.COMPLEXITY_COST * 1)

    val limitFifty = field.right.get.complexity.get
      .apply(context, ArgumentBuilder.buildArgs(argDefinitions, Map("limit" -> Some(50))), 1)
    assert(limitFifty === 5 * NaptimePaginatedResourceField.COMPLEXITY_COST * 1)

    val limitZero = field.right.get.complexity.get
      .apply(context, ArgumentBuilder.buildArgs(argDefinitions, Map("limit" -> Some(1))), 1)
    assert(limitZero === 1 * NaptimePaginatedResourceField.COMPLEXITY_COST * 1)

    val childScoreFive = field.right.get.complexity.get
      .apply(context, ArgumentBuilder.buildArgs(argDefinitions, Map("limit" -> Some(1))), 5)
    assert(childScoreFive === 1 * NaptimePaginatedResourceField.COMPLEXITY_COST * 5)

  }

}
