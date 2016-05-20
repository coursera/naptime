package org.coursera.naptime.courier

import com.linkedin.data.schema.EnumDataSchema
import com.linkedin.data.template.DataTemplateUtil
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.Json

class SchemaInferenceTest extends AssertionsForJUnit {

  import CourierTestFixtures._
  import SchemaInference._

  @Test
  def testCourierCLass(): Unit = {
    val inferred = SchemaInference.inferSchema[MockRecord]
    val expected = Json.parse(MockRecord.SCHEMA_JSON)
    assert(inferred === expected)
  }

  @Test
  def testCourierEnum(): Unit = {
    val inferred = SchemaInference.inferSchema[CourierEnum.CourierEnum]
    val expected = Json.parse(CourierEnum.SCHEMA_JSON)
    assert(inferred === expected)
  }

  @Test
  def testWithPrimitives(): Unit = {
    assert(inferSchema[WithPrimitives] ===
      Json.parse(
        """{
          |  "name": "WithPrimitives",
          |  "namespace": "org.coursera.naptime.courier",
          |  "type": "record",
          |  "fields": [
          |    {"name": "int", "type": "int"}
          |  ]
          |}
        """.stripMargin))
  }

  @Test
  def testWithOptional(): Unit = {
    assert(inferSchema[WithOptional] ===
      Json.parse(
        """{
          |  "name": "WithOptional",
          |  "namespace": "org.coursera.naptime.courier",
          |  "type": "record",
          |  "fields": [
          |    {"name": "int", "type": "int", "optional": true}
          |  ]
          |}
          |""".stripMargin))
  }

  @Test
  def testWithRecord(): Unit = {
    assert(inferSchema[WithRecord] ===
      Json.parse(
        """{
          |  "name": "WithRecord",
          |  "namespace": "org.coursera.naptime.courier",
          |  "type": "record",
          |  "fields": [
          |    {
          |      "name": "record",
          |      "type": {
          |        "name": "Simple",
          |        "namespace": "org.coursera.naptime.courier",
          |        "type": "record",
          |        "fields": [
          |           {"name": "message", "type": "string"}
          |        ]
          |      }
          |    }
          |  ]
          |}
          |
        """.stripMargin))
  }

  @Test
  def testWithArrays(): Unit = {
    assert(inferSchema[WithArrays] ===
      Json.parse(
        """{
          |  "name": "WithArrays",
          |  "namespace": "org.coursera.naptime.courier",
          |  "type": "record",
          |  "fields": [
          |    {
          |      "name": "ints",
          |      "type": {"type": "array", "items": "int"}
          |    }
          |  ]
          |}
        """.stripMargin))
  }

  @Test
  def testWithMaps(): Unit = {
    assert(inferSchema[WithMaps] ===
      Json.parse(
        """{
          |  "name": "WithMaps",
          |  "namespace": "org.coursera.naptime.courier",
          |  "type": "record",
          |  "fields": [
          |    {
          |      "name": "ints",
          |      "type": {"type": "map", "values": "int"}
          |    }
          |  ]
          |}
        """.stripMargin))
  }

  @Test
  def testWithTypedKeyMaps(): Unit = {
    assert(inferSchema[WithTypedKeyMaps] ===
      Json.parse(
        """{
          |  "name": "WithTypedKeyMaps",
          |  "namespace": "org.coursera.naptime.courier",
          |  "type": "record",
          |  "fields": [
          |    {
          |      "name": "ints",
          |      "type": {"type": "map", "keys": "int", "values": "int"}
          |    }
          |  ]
          |}
        """.stripMargin))
  }

  @Test
  def testTypedDefinition(): Unit = {
    assert(inferSchema[TypedDefinition] ===
      Json.parse(
        """{
          |  "name": "TypedDefinition",
          |  "namespace": "org.coursera.naptime.courier",
          |  "type": "typeref",
          |  "ref": [
          |    {
          |      "name": "MemberOne",
          |      "namespace": "org.coursera.naptime.courier",
          |      "type": "record",
          |      "fields": []
          |    },
          |    {
          |      "name": "MemberTwo",
          |      "namespace": "org.coursera.naptime.courier",
          |      "type": "record",
          |      "fields": []
          |    }
          |  ],
          |  "typedDefinition": {
          |    "org.coursera.naptime.courier.MemberOne": "memberOne",
          |    "org.coursera.naptime.courier.MemberTwo": "memberTwo"
          |  }
          |}
        """.stripMargin))
  }

  @Test
  def enumTypes(): Unit = {
    assert(inferSchema[enums.Enum1] ===
      Json.parse(
        """{
          |  "name": "Enum1",
          |  "namespace": "org.coursera.naptime.courier.enums",
          |  "type": "enum",
          |  "symbols": ["SYMBOL_1", "SYMBOL_2"]
          |}
        """.stripMargin))

    assert(inferSchema[Enum2.Enum2] ===
      Json.parse(
        """{
          |  "name": "Enum2",
          |  "namespace": "org.coursera.naptime.courier",
          |  "type": "enum",
          |  "symbols": ["SYMBOL_A", "SYMBOL_B"]
          |}
        """.stripMargin))
  }
}


case class WithPrimitives(int: Int)
case class WithOptional(int: Option[Int])

case class Simple(message: String)
case class WithRecord(record: Simple)
case class WithArrays(ints: Seq[Int])
case class WithMaps(ints: Map[String, Int])
case class WithTypedKeyMaps(ints: Map[Int, Int])

sealed trait TypedDefinition
case class MemberOne() extends TypedDefinition
case class MemberTwo() extends TypedDefinition

package object enums {
  object Enum1 extends Enumeration {
    val SUMBOL_1 = Value("SYMBOL_1")
    val SUMBOL_2 = Value("SYMBOL_2")
  }

  type Enum1 = Enum1.Value
}

object Enum2 extends Enumeration {
  val SUMBOL_A = Value("SYMBOL_A")
  val SUMBOL_B = Value("SYMBOL_B")
  type Enum2 = Enum2.Value
}

object CourierEnum extends Enumeration {
  val SCHEMA_JSON =
    """
      |{
      |  "name": "CourierEnum",
      |  "namespace": "org.coursera.naptime.courier",
      |  "type": "enum",
      |  "symbols": [ "SYMBOL_X", "SYMBOL_Y" ]
      |}
      |""".stripMargin
  val SCHEMA = DataTemplateUtil.parseSchema(SCHEMA_JSON).asInstanceOf[EnumDataSchema]

  val SUMBOL_X = Value("SYMBOL_X")
  val SUMBOL_Y = Value("SYMBOL_Y")
  type CourierEnum = CourierEnum.Value
}
