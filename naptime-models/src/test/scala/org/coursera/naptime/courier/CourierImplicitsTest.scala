/*
 * Copyright 2024 Coursera Inc.
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
import com.linkedin.data.element.SimpleDataElement
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

class CourierImplicitsTest extends AssertionsForJUnit {

  import CourierImplicits._

  @Test def asPredicate_delegatesToFunction(): Unit = {
    // Any DataElement with a String value containing "hello"
    val predicate = asPredicate(elem => elem.getValue == "hello")
    val matchingElem = new SimpleDataElement("hello", null)
    val nonMatchingElem = new SimpleDataElement("world", null)
    assert(predicate.evaluate(matchingElem))
    assert(!predicate.evaluate(nonMatchingElem))
  }

  @Test def asPredicate_usedImplicitly(): Unit = {
    // The implicit conversion should work transparently
    val fn: DataElement => Boolean = _.getValue == "test"
    val predicate = fn: com.linkedin.data.it.Predicate
    val elem = new SimpleDataElement("test", null)
    assert(predicate.evaluate(elem))
  }
}
