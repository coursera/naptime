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

package org.coursera.naptime.router2

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.test.FakeRequest

class QueryParserTests extends AssertionsForJUnit {
  val request = FakeRequest("GET", "/api/myResource.v1?bool1=true&bool2=false&bool3=&bool4=1")

  @Test
  def checkTrueParsing(): Unit = {
    assert(
      CollectionResourceRouter.BooleanFlagParser("bool1", getClass).evaluate(request) ===
        Right(true))
  }

  @Test
  def checkFalseParsing(): Unit = {
    assert(
      CollectionResourceRouter.BooleanFlagParser("bool2", getClass).evaluate(request) ===
        Right(false))
  }

  @Test
  def checkEmptyParsing(): Unit = {
    assert(CollectionResourceRouter.BooleanFlagParser("bool3", getClass).evaluate(request).isLeft)
  }

  @Test
  def checkMalformedParsing(): Unit = {
    assert(CollectionResourceRouter.BooleanFlagParser("bool4", getClass).evaluate(request).isLeft)
  }
}
