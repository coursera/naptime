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

import org.coursera.common.stringkey.StringKey
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class ETagTest extends AssertionsForJUnit {

  @Test
  def serialization(): Unit = {
    assertResult("W/\"abc\"")(StringKey(ETag("abc")).key)
  }

  @Test
  def deserialization(): Unit = {
    assertResult(StringKey("W/\"abc\"").asOpt[ETag])(Some(ETag("abc")))
  }

  @Test
  def deserializationRejectStrong(): Unit = {
    assertResult(StringKey("\"abc\"").asOpt[ETag])(None)
  }

}
