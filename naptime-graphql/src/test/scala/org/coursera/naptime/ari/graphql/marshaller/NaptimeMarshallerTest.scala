package org.coursera.naptime.ari.graphql.marshaller

import com.linkedin.data.DataMap
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json._
import sangria.marshalling.ScalarValueInfo

class NaptimeMarshallerTest extends AssertionsForJUnit {

  import NaptimeMarshaller._

  // -------------------------------------------------------------------------
  // PlayJsonResultMarshaller
  // -------------------------------------------------------------------------

  private val m = PlayJsonResultMarshaller

  @Test
  def emptyMapNode_returnsEmptyBuilder(): Unit = {
    val builder = m.emptyMapNode(Seq("a", "b"))
    assert(builder != null)
  }

  @Test
  def addMapNodeElem_addsKeyValue(): Unit = {
    val builder = m.emptyMapNode(Seq("key"))
    m.addMapNodeElem(builder, "key", JsString("value"), optional = false)
    val node = m.mapNode(builder)
    assert(node.as[JsObject].value("key") === JsString("value"))
  }

  @Test
  def mapNodeFromSeq_buildsObject(): Unit = {
    val node = m.mapNode(Seq("x" -> JsNumber(1), "y" -> JsBoolean(true)))
    assert(node === Json.obj("x" -> 1, "y" -> true))
  }

  @Test
  def arrayNode_buildsJsArray(): Unit = {
    val arr = m.arrayNode(Vector(JsString("a"), JsNumber(2)))
    assert(arr === JsArray(Seq(JsString("a"), JsNumber(2))))
  }

  @Test
  def optionalArrayNodeValue_someValue(): Unit = {
    assert(m.optionalArrayNodeValue(Some(JsString("hi"))) === JsString("hi"))
  }

  @Test
  def optionalArrayNodeValue_noneReturnsNull(): Unit = {
    assert(m.optionalArrayNodeValue(None) === JsNull)
  }

  @Test
  def scalarNode_string(): Unit = {
    assert(m.scalarNode("hello", "String", Set.empty) === JsString("hello"))
  }

  @Test
  def scalarNode_boolean(): Unit = {
    assert(m.scalarNode(true, "Boolean", Set.empty) === JsBoolean(true))
  }

  @Test
  def scalarNode_int(): Unit = {
    assert(m.scalarNode(42, "Int", Set.empty) === JsNumber(42))
  }

  @Test
  def scalarNode_long(): Unit = {
    assert(m.scalarNode(100L, "Long", Set.empty) === JsNumber(100))
  }

  @Test
  def scalarNode_double(): Unit = {
    assert(m.scalarNode(3.14, "Float", Set.empty) === JsNumber(3.14))
  }

  @Test
  def scalarNode_bigInt(): Unit = {
    assert(m.scalarNode(BigInt(999), "BigInt", Set.empty) === JsNumber(BigDecimal(999)))
  }

  @Test
  def scalarNode_bigDecimal(): Unit = {
    assert(m.scalarNode(BigDecimal(1.5), "BigDecimal", Set.empty) === JsNumber(1.5))
  }

  @Test
  def scalarNode_dataMap_returnsJsValue(): Unit = {
    val dm = new DataMap()
    dm.put("k", "v")
    // Should not throw; DataMap -> JsValue via NaptimeSerializer
    val result = m.scalarNode(dm, "DataMap", Set.empty[ScalarValueInfo])
    assert(result != null)
  }

  @Test
  def scalarNode_unsupportedType_throwsIllegalArgumentException(): Unit = {
    intercept[IllegalArgumentException] {
      m.scalarNode(List(1, 2, 3), "Unknown", Set.empty)
    }
  }

  @Test
  def enumNode_returnsJsString(): Unit = {
    assert(m.enumNode("FOO", "MyEnum") === JsString("FOO"))
  }

  @Test
  def nullNode_isJsNull(): Unit = {
    assert(m.nullNode === JsNull)
  }

  @Test
  def renderCompact_producesString(): Unit = {
    val json = Json.obj("a" -> 1)
    assert(m.renderCompact(json) === """{"a":1}""")
  }

  @Test
  def renderPretty_producesFormattedString(): Unit = {
    val json = Json.obj("a" -> 1)
    assert(m.renderPretty(json).contains("\"a\""))
  }

  // -------------------------------------------------------------------------
  // PlayJsonInputUnmarshaller
  // -------------------------------------------------------------------------

  private val u = PlayJsonInputUnmarshaller

  @Test
  def getRootMapValue_existingKey(): Unit = {
    val obj = Json.obj("foo" -> "bar")
    assert(u.getRootMapValue(obj, "foo") === Some(JsString("bar")))
  }

  @Test
  def getRootMapValue_missingKey(): Unit = {
    val obj = Json.obj("foo" -> "bar")
    assert(u.getRootMapValue(obj, "missing") === None)
  }

  @Test
  def isListNode_array(): Unit = {
    assert(u.isListNode(JsArray(Seq.empty)))
  }

  @Test
  def isListNode_nonArray(): Unit = {
    assert(!u.isListNode(JsString("x")))
  }

  @Test
  def getListValue_returnsElements(): Unit = {
    val arr = JsArray(Seq(JsNumber(1), JsNumber(2)))
    assert(u.getListValue(arr).toList === List(JsNumber(1), JsNumber(2)))
  }

  @Test
  def isMapNode_object(): Unit = {
    assert(u.isMapNode(Json.obj("a" -> 1)))
  }

  @Test
  def isMapNode_nonObject(): Unit = {
    assert(!u.isMapNode(JsString("x")))
  }

  @Test
  def getMapValue_existingKey(): Unit = {
    val obj = Json.obj("x" -> 5)
    assert(u.getMapValue(obj, "x") === Some(JsNumber(5)))
  }

  @Test
  def getMapKeys_returnsKeys(): Unit = {
    val obj = Json.obj("a" -> 1, "b" -> 2)
    assert(u.getMapKeys(obj).toSet === Set("a", "b"))
  }

  @Test
  def isDefined_nonNull(): Unit = {
    assert(u.isDefined(JsString("x")))
  }

  @Test
  def isDefined_null(): Unit = {
    assert(!u.isDefined(JsNull))
  }

  @Test
  def getScalarValue_boolean(): Unit = {
    assert(u.getScalarValue(JsBoolean(false)) === false)
  }

  @Test
  def getScalarValue_number(): Unit = {
    val result = u.getScalarValue(JsNumber(42))
    // returns either BigInt or BigDecimal depending on exact value
    assert(result == BigInt(42) || result == BigDecimal(42))
  }

  @Test
  def getScalarValue_string(): Unit = {
    assert(u.getScalarValue(JsString("hello")) === "hello")
  }

  @Test
  def getScalarValue_invalidType_throws(): Unit = {
    intercept[IllegalStateException] {
      u.getScalarValue(JsNull)
    }
  }

  @Test
  def getScalaScalarValue_delegatesToGetScalarValue(): Unit = {
    assert(u.getScalaScalarValue(JsBoolean(true)) === true)
  }

  @Test
  def isEnumNode_jsString(): Unit = {
    assert(u.isEnumNode(JsString("FOO")))
  }

  @Test
  def isEnumNode_nonString(): Unit = {
    assert(!u.isEnumNode(JsNumber(1)))
  }

  @Test
  def isScalarNode_boolean(): Unit = {
    assert(u.isScalarNode(JsBoolean(true)))
  }

  @Test
  def isScalarNode_number(): Unit = {
    assert(u.isScalarNode(JsNumber(1)))
  }

  @Test
  def isScalarNode_string(): Unit = {
    assert(u.isScalarNode(JsString("a")))
  }

  @Test
  def isScalarNode_null_returnsFalse(): Unit = {
    assert(!u.isScalarNode(JsNull))
  }

  @Test
  def isVariableNode_alwaysFalse(): Unit = {
    assert(!u.isVariableNode(JsString("x")))
  }

  @Test
  def getVariableName_throws(): Unit = {
    intercept[IllegalArgumentException] {
      u.getVariableName(JsString("x"))
    }
  }

  @Test
  def render_producesJsonString(): Unit = {
    assert(u.render(JsString("hello")) === "\"hello\"")
  }

  // -------------------------------------------------------------------------
  // PlayJsonInputParser
  // -------------------------------------------------------------------------

  @Test
  def inputParser_validJson(): Unit = {
    val result = PlayJsonInputParser.parse("""{"a":1}""")
    assert(result.isSuccess)
  }

  @Test
  def inputParser_invalidJson(): Unit = {
    val result = PlayJsonInputParser.parse("not json")
    assert(result.isFailure)
  }

  // -------------------------------------------------------------------------
  // playJsonToInput implicit
  // -------------------------------------------------------------------------

  @Test
  def playJsonToInput_works(): Unit = {
    val jsVal: JsString = JsString("test")
    val (value, unmarshaller) = implicitly[sangria.marshalling.ToInput[JsString, JsValue]].toInput(jsVal)
    assert(value === JsString("test"))
  }

  // -------------------------------------------------------------------------
  // playJsonWriterToInput — explicit invocation with non-JsValue type
  // (covers lines 119-120: the toInput method inside the anonymous class)
  // -------------------------------------------------------------------------

  @Test
  def playJsonWriterToInput_works(): Unit = {
    // JsValue has an implicit Writes[JsValue] via play's Identity writes
    implicit val jsWrites: Writes[JsValue] = (v: JsValue) => v
    val input = implicitly[sangria.marshalling.ToInput[JsValue, JsValue]]
    val (value, _) = input.toInput(JsString("hello"))
    assert(value === JsString("hello"))
  }

  @Test
  def playJsonWriterToInput_nonJsValueType_coversToInputMethod(): Unit = {
    // Use a non-JsValue type so playJsonWriterToInput (not playJsonToInput) is selected
    case class MyData(x: String)
    implicit val myWrites: Writes[MyData] = (d: MyData) => JsString(d.x)
    // Explicitly call playJsonWriterToInput to cover lines 119-120
    val input = NaptimeMarshaller.playJsonWriterToInput[MyData]
    val (value, _) = input.toInput(MyData("test"))
    assert(value === JsString("test"))
  }

  // -------------------------------------------------------------------------
  // playJsonReaderFromInput — explicit invocation to cover JsSuccess branch (line 135)
  // -------------------------------------------------------------------------

  @Test
  def playJsonReaderFromInput_successBranch_coversLine135(): Unit = {
    // Explicit call bypasses playJsonFromInput which would otherwise win for JsValue subtypes
    implicit val myReads: Reads[String] = Reads {
      case JsString(s) => JsSuccess(s)
      case _           => JsError("not a string")
    }
    val fromInput = NaptimeMarshaller.playJsonReaderFromInput[String]
    val node = JsString("hello").asInstanceOf[fromInput.marshaller.Node]
    val result = fromInput.fromResult(node)
    assert(result === "hello")
  }

  // -------------------------------------------------------------------------
  // playJsonFromInput implicit — JsObject subtype
  // -------------------------------------------------------------------------

  @Test
  def playJsonFromInput_works(): Unit = {
    // Use the concrete PlayJsonFromInput through JsObject
    val fromInput = implicitly[sangria.marshalling.FromInput[JsObject]]
    // marshaller.Node is JsValue; cast to satisfy path-dependent type
    val node = Json.obj("k" -> "v").asInstanceOf[fromInput.marshaller.Node]
    val result = fromInput.fromResult(node)
    assert(result === Json.obj("k" -> "v"))
  }

  // -------------------------------------------------------------------------
  // playJsonReaderFromInput — via Reads[JsValue] (identity)
  // -------------------------------------------------------------------------

  @Test
  def playJsonReaderFromInput_success(): Unit = {
    // Use a Reads[JsString] that succeeds
    implicit val jsStringReads: Reads[JsString] = Reads {
      case s: JsString => JsSuccess(s)
      case other       => JsError("not a string")
    }
    val fromInput = implicitly[sangria.marshalling.FromInput[JsString]]
    val node = JsString("world").asInstanceOf[fromInput.marshaller.Node]
    val result = fromInput.fromResult(node)
    assert(result === JsString("world"))
  }

  @Test
  def playJsonReaderFromInput_failure_throwsInputParsingError(): Unit = {
    // Case class type forces use of playJsonReaderFromInput (not playJsonFromInput)
    // We can't use case classes with Json.reads in local scope, so use a custom Reads type
    // that wraps Int and fails on any input
    case class IntWrapper(n: Int)
    // Manually create the FromInput using playJsonReaderFromInput
    implicit val intWrapperReads: Reads[IntWrapper] = Reads { js =>
      JsError(play.api.libs.json.JsPath \ "n" -> play.api.libs.json.JsonValidationError("always fails"))
    }
    val fromInput = NaptimeMarshaller.playJsonReaderFromInput[IntWrapper]
    val node = JsNumber(42).asInstanceOf[fromInput.marshaller.Node]
    intercept[sangria.marshalling.InputParsingError] {
      fromInput.fromResult(node)
    }
  }
}
