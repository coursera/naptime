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

import org.junit.Ignore
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class QueryStringParserTest extends AssertionsForJUnit {

  @Test
  def simpleTest(): Unit = {
    test(
      "a,b,c",
      QueryFields(Set("a", "b", "c"), Map.empty),
      QueryIncludes(Set("a", "b", "c"), Map.empty))
  }

  @Test
  def handleSpaces(): Unit = {
    test(
      "a, b, d",
      QueryFields(Set("a", "b", "d"), Map.empty),
      QueryIncludes(Set("a", "b", "d"), Map.empty))
  }

  @Test
  def resourceTest(): Unit = {
    test(
      "foo.v3(a,b,c)",
      QueryFields(Set.empty, Map(ResourceName("foo", 3) -> Set("a", "b", "c"))),
      QueryIncludes(Set.empty, Map(ResourceName("foo", 3) -> Set("a", "b", "c"))))
  }

  @Test
  def subResourceTest(): Unit = {
    test(
      "foo.v20/history(name,comment)",
      QueryFields(
        Set.empty,
        Map(ResourceName("foo", 20, List("history")) -> Set("name", "comment"))),
      QueryIncludes(
        Set.empty,
        Map(ResourceName("foo", 20, List("history")) -> Set("name", "comment"))))
  }

  @Test
  def mixed(): Unit = {
    test(
      "name,related.v2(author),postedTimestamp,related.v2/history(author,date),votes",
      QueryFields(
        Set("name", "postedTimestamp", "votes"),
        Map(
          ResourceName("related", 2) -> Set("author"),
          ResourceName("related", 2, List("history")) -> Set("author", "date"))),
      QueryIncludes(
        Set("name", "postedTimestamp", "votes"),
        Map(
          ResourceName("related", 2) -> Set("author"),
          ResourceName("related", 2, List("history")) -> Set("author", "date"))))
  }

  @Test
  def deepNesting(): Unit = {
    test(
      "name,related.v100/history/authorizations(issuer,time)",
      QueryFields(
        Set("name"),
        Map(
          ResourceName("related", 100, List("history", "authorizations")) -> Set(
            "issuer",
            "time"))),
      QueryIncludes(
        Set("name"),
        Map(
          ResourceName("related", 100, List("history", "authorizations")) -> Set(
            "issuer",
            "time"))))
  }

  @Test
  def negatedQueries(): Unit = {
    test(
      "-a,b,-c",
      QueryFields(Set("-a", "b", "-c"), Map.empty),
      QueryIncludes(Set("-a", "b", "-c"), Map.empty))
  }

  @Test
  def fieldsWithDots(): Unit = {
    test(
      "a.b.c,x,y,z",
      QueryFields(Set("a.b.c", "x", "y", "z"), Map.empty),
      QueryIncludes(Set("a.b.c", "x", "y", "z"), Map.empty))
  }

  // Should the semantics of the following be replace or merge: 'related.v1(a),related.v1(b)'?

  private[this] def test(
      queryString: String,
      parsedFields: QueryFields,
      parsedInclude: QueryIncludes): Unit = {
    val resultFields = QueryStringParser.parseQueryFields(queryString)
    assert(resultFields.successful, s"Unsuccessful parse of '$queryString': $resultFields")
    assert(resultFields.get === parsedFields)
    val resultIncludes = QueryStringParser.parseQueryIncludes(queryString)
    assert(resultIncludes.successful, s"Unsuccessful parse of '$queryString': $resultIncludes")
    assert(resultIncludes.get === parsedInclude)
  }

  @Test
  @Ignore // INFRA-1986
  def perfTest(): Unit = {
    val start = System.currentTimeMillis()
    val ops = 10000
    var i = ops
    while (i > 0) {
      QueryStringParser.parseQueryFields("a,b,c")
      i -= 1
    }
    val end = System.currentTimeMillis()
    // 10,000 operations in less than 1 second is okay
    assert(end - start < 1000, s"Did 10,000 operations in ${end - start}ms. Should be < 1000 msec.")
  }
}
