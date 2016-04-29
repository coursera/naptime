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

import com.linkedin.data.element.DataElement
import com.linkedin.data.it.Predicate

/**
 * Courier related implicit helpers.
 */
object CourierImplicits {

  import scala.language.implicitConversions

  /**
   * Implicitly converts a DataElement => Boolean function to a Predicate
   * @param dataPredicate anonymous function that evaluates a DataElement and returns a boolean
   * @return Predicate class that evaluates DataElements with the dataPredicate
   */
  implicit def asPredicate(dataPredicate: DataElement => Boolean): Predicate = {
    new Predicate {
      override def evaluate(element: DataElement) = dataPredicate(element)
    }
  }
}
