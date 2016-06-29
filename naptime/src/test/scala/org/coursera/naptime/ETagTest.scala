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
  def weakSerialization(): Unit = {
    assertResult("W/\"abc\"")(StringKey(ETag.Weak("abc")).key)
    assertResult("W/\"abc\"")(StringKey(ETag.Weak("abc"): ETag).key)
  }

  @Test
  def weakDeserialization(): Unit = {
    val stringKey = StringKey("W/\"abc\"")
    assertResult(stringKey.asOpt[ETag.Weak])(Some(ETag.Weak("abc")))
    assertResult(stringKey.asOpt[ETag])(Some(ETag.Weak("abc")))
    assertResult(stringKey.asOpt[ETag.Strong])(None)
  }

  @Test
  def strongSerialization(): Unit = {
    assertResult("\"abc\"")(StringKey(ETag.Strong("abc")).key)
    assertResult("\"abc\"")(StringKey(ETag.Strong("abc"): ETag).key)
  }

  @Test
  def strongDeserialization(): Unit = {
    val stringKey = StringKey("\"abc\"")
    assertResult(stringKey.asOpt[ETag.Strong])(Some(ETag.Strong("abc")))
    assertResult(stringKey.asOpt[ETag])(Some(ETag.Strong("abc")))
    assertResult(stringKey.asOpt[ETag.Weak])(None)
  }

}
