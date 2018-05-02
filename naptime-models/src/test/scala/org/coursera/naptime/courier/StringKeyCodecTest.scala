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

import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.template.DataTemplateUtil
import org.coursera.courier.templates.DataTemplates
import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test

class StringKeyCodecTest extends AssertionsForJUnit {

  @Test
  def testPre(): Unit = {
    assertTuples(
      "pre~hello",
      """
        |{ "first": "hello" }
        |""".stripMargin,
      schema = tuple1Schema,
      prefix = Some("pre"))

    assertTuples(
      "!~pre~hello",
      """
        |{ "first": "hello" }
        |""".stripMargin,
      schema = tuple1Schema,
      prefix = Some("~pre"))
  }

  @Test
  def testTuples(): Unit = {
    assertTuples(
      "coursera~awesome",
      """
        |{ "first": "coursera", "second": "awesome" }
        |""".stripMargin,
      schema = tuple2Schema)

    assertTuples(
      "coursera!~awesome~Really",
      """
        |{ "first": "coursera~awesome", "second": "Really" }
        |""".stripMargin,
      schema = tuple2Schema)

    assertTuples(
      "abc~123!~456!~789",
      """
        |{ "first": "abc", "second": "123~456~789" }
        |""".stripMargin,
      schema = tuple2Schema)

    assertTuples(
      "coursera~really~awesome",
      """
        |{ "first": "coursera", "second": "really", "third": "awesome" }
        |""".stripMargin,
      schema = tuple3Schema)
  }

  @Test
  def testSeqs(): Unit = {
    assertSeq("a", """["a"]""", schema = seqSchema)
    assertSeq("a,b", """["a", "b"]""", schema = seqSchema)
    assertSeq("a,!!b", """["a", "!b"]""", schema = seqSchema)
    assertSeq("a,!,b", """["a", ",b"]""", schema = seqSchema)
    assertSeq("a!,b", """["a,b"]""", schema = seqSchema)
    assertSeq("", """[]""", schema = seqSchema)
    assertSeq("1", """["1"]""", schema = seqSchema)
    assertSeq("1,", """["1", ""]""", schema = seqSchema)
    assertSeq("1,2", """["1", "2"]""", schema = seqSchema)
    assertSeq("1,,2", """["1", "", "2"]""", schema = seqSchema)
    assertSeq("1,a", """["1", "a"]""", schema = seqSchema)
  }

  @Test
  def testComplexTypeComposition(): Unit = {
    // tuples of tuples
    assertTuples(
      "a!~b~c!~d",
      """
        |{
        |  "first": { "first": "a", "second": "b" },
        |  "second": { "first": "c", "second": "d" }
        |}
        |""".stripMargin,
      schema = tupleInTupleSchema)

    // tuples of seq
    assertSeq(
      "a~b,c~d",
      """
        |[
        |  { "first": "a", "second": "b" },
        |  { "first": "c", "second": "d" }
        |]
        |""".stripMargin,
      schema = tupleInSeqSchema)

    // seqs in tuples
    assertTuples(
      "a,b~c,d",
      """
        |{
        |  "first": [ "a", "b" ],
        |  "second": [ "c", "d" ]
        |}
        |""".stripMargin,
      schema = seqInTupleSchema)

    // seqs in seq
    assertSeq(
      "a!,b,c!,d",
      """
        |[
        |  [ "a", "b" ],
        |  [ "c", "d" ]
        |]
        |""".stripMargin,
      schema = seqInSeqSchema)
  }

  def assertTuples(
      stringKey: String,
      json: String,
      schema: DataSchema,
      prefix: Option[String] = None): Unit = {
    val codec = new StringKeyCodec(schema, prefix)
    val stringKeyDataMap = codec.bytesToMap(stringKey.getBytes(StringKeyCodec.charset))
    val jsonDataMap = DataTemplates.readDataMap(json)
    assert(stringKeyDataMap === jsonDataMap)

    val jsonAsStringKey = new String(codec.mapToBytes(jsonDataMap), StringKeyCodec.charset)
    assert(stringKey === jsonAsStringKey)
  }

  def assertSeq(
      stringKey: String,
      json: String,
      schema: DataSchema,
      prefix: Option[String] = None): Unit = {
    val codec = new StringKeyCodec(schema, prefix)
    val stringKeyDataList = codec.bytesToList(stringKey.getBytes(StringKeyCodec.charset))
    val jsonDataList = DataTemplates.readDataList(json)
    assert(stringKeyDataList === jsonDataList)

    val jsonAsStringKey = new String(codec.listToBytes(jsonDataList), StringKeyCodec.charset)
    assert(stringKey === jsonAsStringKey)
  }

  val tuple1Schema =
    DataTemplateUtil.parseSchema("""
      |{
      |  "name": "Tuple1", "type": "record",
      |  "fields": [
      |    { "name": "first", "type": "string" }
      |  ]
      |}
    """.stripMargin).asInstanceOf[RecordDataSchema]

  val tuple2Schema =
    DataTemplateUtil.parseSchema("""
     |{
     |  "name": "Tuple2", "type": "record",
     |  "fields": [
     |    { "name": "first", "type": "string" },
     |    { "name": "second", "type": "string" }
     |  ]
     |}
   """.stripMargin).asInstanceOf[RecordDataSchema]

  val tuple3Schema =
    DataTemplateUtil.parseSchema("""
     |{
     |  "name": "Tuple3", "type": "record",
     |  "fields": [
     |    { "name": "first", "type": "string" },
     |    { "name": "second", "type": "string" },
     |    { "name": "third", "type": "string" }
     |  ]
     |}
   """.stripMargin).asInstanceOf[RecordDataSchema]

  val seqSchema =
    DataTemplateUtil.parseSchema("""
     |{
     |  "name": "Seq", "type": "typeref",
     |  "ref": { "type": "array", "items": "string" }
     |}
   """.stripMargin).asInstanceOf[TyperefDataSchema]

  val tupleInTupleSchema =
    DataTemplateUtil.parseSchema("""
     |{
     |  "name": "TupleInTuple", "type": "record",
     |  "fields": [
     |    {
     |      "name": "first", "type": {
     |        "name": "Tuple2", "type": "record",
     |        "fields": [
     |          { "name": "first", "type": "string" },
     |          { "name": "second", "type": "string" }
     |        ]
     |      }
     |    },
     |    {
     |      "name": "second", "type": "Tuple2"
     |    }
     |  ]
     |}
   """.stripMargin).asInstanceOf[RecordDataSchema]

  val tupleInSeqSchema =
    DataTemplateUtil.parseSchema("""
     |{
     |  "name": "TupleInSeq", "type": "typeref",
     |  "ref": {
     |    "type": "array",
     |    "items": {
     |      "name": "Tuple2", "type": "record",
     |      "fields": [
     |        { "name": "first", "type": "string" },
     |        { "name": "second", "type": "string" }
     |      ]
     |    }
     |  }
     |}
   """.stripMargin).asInstanceOf[TyperefDataSchema]

  val seqInTupleSchema =
    DataTemplateUtil
      .parseSchema("""
     |{
     |  "name": "SeqInTuple", "type": "record",
     |  "fields": [
     |    {
     |      "name": "first", "type": { "type": "array", "items": "string" }
     |    },
     |    {
     |      "name": "second", "type": { "type": "array", "items": "string" }
     |    }
     |  ]
     |}
   """.stripMargin)
      .asInstanceOf[RecordDataSchema]

  val seqInSeqSchema =
    DataTemplateUtil.parseSchema("""
     |{
     |  "name": "SeqInSeq", "type": "typeref",
     |  "ref": {
     |    "type": "array",
     |    "items": { "type": "array", "items": "string" }
     |  }
     |}
   """.stripMargin).asInstanceOf[TyperefDataSchema]
}
