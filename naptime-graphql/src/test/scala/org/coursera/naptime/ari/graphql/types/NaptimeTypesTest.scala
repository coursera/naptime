package org.coursera.naptime.ari.graphql.types

import com.linkedin.data.DataMap
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import sangria.ast.StringValue
import sangria.validation.ValueCoercionViolation

class NaptimeTypesTest extends AssertionsForJUnit {

  private val dataMapType = NaptimeTypes.DataMapType

  // -------------------------------------------------------------------------
  // coerceUserInput
  // -------------------------------------------------------------------------

  @Test
  def coerceUserInput_validJsonString_returnsRight(): Unit = {
    val result = dataMapType.coerceUserInput("""{"key":"value"}""")
    result match {
      case Right(dm: DataMap) => assert(dm.get("key") === "value")
      case other              => fail(s"Expected Right(DataMap) but got $other")
    }
  }

  @Test
  def coerceUserInput_invalidJson_returnsViolation(): Unit = {
    val result = dataMapType.coerceUserInput("not valid json at all")
    result match {
      case Left(_: ValueCoercionViolation) => // expected
      case other                           => fail(s"Expected Left(violation) but got $other")
    }
  }

  @Test
  def coerceUserInput_nonString_returnsViolation(): Unit = {
    val result = dataMapType.coerceUserInput(42)
    result match {
      case Left(_: ValueCoercionViolation) => // expected
      case other                           => fail(s"Expected Left(violation) but got $other")
    }
  }

  @Test
  def coerceUserInput_nullInput_returnsViolation(): Unit = {
    val result = dataMapType.coerceUserInput(null)
    result match {
      case Left(_: ValueCoercionViolation) => // expected
      case other                           => fail(s"Expected Left(violation) but got $other")
    }
  }

  // -------------------------------------------------------------------------
  // coerceInput (from AST StringValue)
  // -------------------------------------------------------------------------

  @Test
  def coerceInput_validStringValue_returnsRight(): Unit = {
    val astValue = StringValue("""{"foo":"bar"}""")
    val result = dataMapType.coerceInput(astValue)
    result match {
      case Right(dm: DataMap) => assert(dm.get("foo") === "bar")
      case other              => fail(s"Expected Right(DataMap) but got $other")
    }
  }

  @Test
  def coerceInput_invalidStringValue_returnsViolation(): Unit = {
    val astValue = StringValue("not json")
    val result = dataMapType.coerceInput(astValue)
    result match {
      case Left(_: ValueCoercionViolation) => // expected
      case other                           => fail(s"Expected Left(violation) but got $other")
    }
  }

  @Test
  def coerceInput_nonStringAstNode_returnsViolation(): Unit = {
    val astValue = sangria.ast.IntValue(42)
    val result = dataMapType.coerceInput(astValue)
    result match {
      case Left(_: ValueCoercionViolation) => // expected
      case other                           => fail(s"Expected Left(violation) but got $other")
    }
  }

  // -------------------------------------------------------------------------
  // coerceOutput
  // -------------------------------------------------------------------------

  @Test
  def coerceOutput_returnsDataMapAsIs(): Unit = {
    val dm = new DataMap()
    dm.put("x", "y")
    val result = dataMapType.coerceOutput(dm, Set.empty)
    assert(result === dm)
  }

  // -------------------------------------------------------------------------
  // DataMapCoercionViolation
  // -------------------------------------------------------------------------

  @Test
  def dataMapCoercionViolation_hasExpectedMessage(): Unit = {
    assert(NaptimeTypes.DataMapCoercionViolation.errorMessage === "DataMap value expected")
  }

  // -------------------------------------------------------------------------
  // DataMapType metadata
  // -------------------------------------------------------------------------

  @Test
  def dataMapType_hasName(): Unit = {
    assert(dataMapType.name === "DataMap")
  }

  @Test
  def dataMapType_hasDescription(): Unit = {
    assert(dataMapType.description.isDefined)
  }
}
