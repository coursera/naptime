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
import org.scalatestplus.junit.AssertionsForJUnit

class ETagTest extends AssertionsForJUnit {

  @Test
  def weakSerialization(): Unit = {
    assertResult("W/\"abc\"")(StringKey.toStringKey(ETag.Weak("abc")).key)
    assertResult("W/\"abc\"")(StringKey.toStringKey(ETag.Weak("abc")).key)
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
    assertResult("\"abc\"")(StringKey.toStringKey(ETag.Strong("abc")).key)
    assertResult("\"abc\"")(StringKey.toStringKey(ETag.Strong("abc")).key)
  }

  @Test
  def strongDeserialization(): Unit = {
    val stringKey = StringKey("\"abc\"")
    assertResult(stringKey.asOpt[ETag.Strong])(Some(ETag.Strong("abc")))
    assertResult(stringKey.asOpt[ETag])(Some(ETag.Strong("abc")))
    assertResult(stringKey.asOpt[ETag.Weak])(None)
  }

  @Test
  def applyFactory_createsWeak(): Unit = {
    assertResult(ETag.Weak("xyz"))(ETag("xyz"))
  }

  @Test
  def weakDeserializationNoMatch_returnsNone(): Unit = {
    // A string that matches neither W/"..." nor "..." format
    val stringKey = StringKey("plain-string")
    assertResult(None)(stringKey.asOpt[ETag.Weak])
    assertResult(None)(stringKey.asOpt[ETag.Strong])
    assertResult(None)(stringKey.asOpt[ETag])
  }

  @Test
  def weakRequire_invalidChars_throwsException(): Unit = {
    intercept[IllegalArgumentException] {
      ETag.Weak("abc\"\\def")
    }
  }

  @Test
  def strongRequire_invalidChars_throwsException(): Unit = {
    intercept[IllegalArgumentException] {
      ETag.Strong("abc\"\\def")
    }
  }

}
