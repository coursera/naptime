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

package org.coursera.naptime.actions

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import org.coursera.naptime.schema.Parameter
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class RecordTemplateNaptimeSerializerTest extends AssertionsForJUnit {

  private[this] def helper[A](obj: A)(implicit naptimeSerializer: NaptimeSerializer[A]): DataMap = {
    naptimeSerializer.serialize(obj)
  }

  @Test
  def simpleTest(): Unit = {
    val parameter = Parameter("parameterName", "fakeType", None, List.empty)

    val expected = new DataMap()
    expected.put("name", "parameterName")
    expected.put("type", "fakeType")
    expected.put("attributes", new DataList())

    assert(expected === helper(parameter))
  }

}
