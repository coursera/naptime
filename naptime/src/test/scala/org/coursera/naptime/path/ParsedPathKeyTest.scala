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

package org.coursera.naptime.path

import org.coursera.naptime.path.ParsedPathKeyTest.User
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

object ParsedPathKeyTest {
  case class User(name: String, email: String)
}

class ParsedPathKeyTest extends AssertionsForJUnit {

  @Test
  def testEvidence(): Unit = {
    val pathKey = "a" ::: 1 ::: User("Daphne", "security@coursera.org") ::: RootParsedPathKey
    assert(pathKey.key === "a")
    assert(pathKey.parentKey === 1)
    assert(pathKey.grandparentKey === User("Daphne", "security@coursera.org"))
  }

  @Test
  def testUnapply(): Unit = {
    val a ::: b ::: RootParsedPathKey = "a" ::: 1 ::: RootParsedPathKey
    assert(a === "a")
    assert(b === 1)
  }
}
