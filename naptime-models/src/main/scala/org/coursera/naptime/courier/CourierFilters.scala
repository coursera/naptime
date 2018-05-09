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

package org.coursera.naptime.courier

import com.linkedin.data.DataComplex
import com.linkedin.data.element.DataElement
import com.linkedin.data.it.Builder
import com.linkedin.data.it.IterationOrder
import com.linkedin.data.it.Predicate
import com.linkedin.data.schema.DataSchema

/**
 * Courier related data filters.
 */
object CourierFilters {

  /**
   * Removes all unrecognized fields (fields not defined in the provided schema) from the given
   * data and returns the data.
   *
   * WARNING: this mutates data in-place.
   */
  def filterUnrecognizedFields[D <: DataComplex](data: D, schema: DataSchema): D = {
    Builder.create(data, schema, IterationOrder.PRE_ORDER).filterBy(AbsentSchemaPredicate).remove()
    data
  }

  private[this] object AbsentSchemaPredicate extends Predicate {
    override def evaluate(element: DataElement): Boolean = {
      element.getSchema == null
    }
  }
}
