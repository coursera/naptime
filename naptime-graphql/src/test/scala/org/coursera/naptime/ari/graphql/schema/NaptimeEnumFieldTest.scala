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

package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.schema.EnumDataSchema
import com.linkedin.data.schema.Name
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import sangria.schema.EnumType

import scala.collection.JavaConverters._

class NaptimeEnumFieldTest extends AssertionsForJUnit with MockitoSugar {

  def buildEnumDataSchema(values: List[String]): EnumDataSchema = {
    val enum = new EnumDataSchema(new Name("testEnum"))
    val stringBuilder = new java.lang.StringBuilder()
    enum.setSymbols(values.asJava, stringBuilder)
    enum
  }

  @Test
  def build_RegularEnum(): Unit = {
    val values = List("valueOne", "valueTwo")
    val enum = buildEnumDataSchema(values)
    val field = NaptimeEnumField.build(enum, "myField")
    assert(field.fieldType.asInstanceOf[EnumType[String]].values.map(_.name) === values)
  }

  @Test
  def build_EmptyEnum(): Unit = {
    val values = List()
    val expectedValues = List("UNKNOWN")
    val enum = buildEnumDataSchema(values)
    val field = NaptimeEnumField.build(enum, "myField")
    assert(field.fieldType.asInstanceOf[EnumType[String]].values.map(_.name) === expectedValues)
  }

}
