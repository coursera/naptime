package org.coursera.naptime.courier

import org.coursera.naptime.courier.Exceptions.ReadException
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString

class CourierUtilsTest extends AssertionsForJUnit {

  // ---------------------------------------------------------------------------
  // destructureFlatTypedDefinitionJsObject
  // ---------------------------------------------------------------------------

  @Test
  def destructureFlatTypedDefinition_happyPath_extractsTypeNameAndBody(): Unit = {
    val obj = JsObject(Seq("typeName" -> JsString("alpha"), "field1" -> JsString("val1")))
    val (typeName, body) = CourierUtils.destructureFlatTypedDefinitionJsObject(obj)
    assertResult("alpha")(typeName)
    assertResult(JsObject(Seq("field1" -> JsString("val1"))))(body)
  }

  @Test
  def destructureFlatTypedDefinition_missingTypeName_throwsReadException(): Unit = {
    val obj = JsObject(Seq("field1" -> JsString("val1")))
    intercept[ReadException] {
      CourierUtils.destructureFlatTypedDefinitionJsObject(obj)
    }
  }

  @Test
  def destructureFlatTypedDefinition_typeNameNotString_throwsReadException(): Unit = {
    val obj = JsObject(Seq("typeName" -> JsNumber(42), "field1" -> JsString("val1")))
    intercept[ReadException] {
      CourierUtils.destructureFlatTypedDefinitionJsObject(obj)
    }
  }

  @Test
  def destructureFlatTypedDefinition_onlyTypeNameField_returnsEmptyBody(): Unit = {
    val obj = JsObject(Seq("typeName" -> JsString("myType")))
    val (typeName, body) = CourierUtils.destructureFlatTypedDefinitionJsObject(obj)
    assertResult("myType")(typeName)
    assertResult(JsObject(Seq.empty))(body)
  }

  // ---------------------------------------------------------------------------
  // destructureTypedDefinitionJsObject
  // ---------------------------------------------------------------------------

  @Test
  def destructureTypedDefinition_happyPath(): Unit = {
    val innerJson = JsObject(Seq("id" -> JsString("abc")))
    val obj = JsObject(Seq("typeName" -> JsString("alpha"), "definition" -> innerJson))
    val (typeName, definition) = CourierUtils.destructureTypedDefinitionJsObject(obj)
    assertResult("alpha")(typeName)
    assertResult(innerJson)(definition)
  }

  @Test
  def destructureTypedDefinition_missingTypeName_throwsReadException(): Unit = {
    val obj = JsObject(Seq("definition" -> JsObject(Seq.empty)))
    intercept[ReadException] {
      CourierUtils.destructureTypedDefinitionJsObject(obj)
    }
  }

  @Test
  def destructureTypedDefinition_missingDefinition_throwsReadException(): Unit = {
    val obj = JsObject(Seq("typeName" -> JsString("alpha")))
    intercept[ReadException] {
      CourierUtils.destructureTypedDefinitionJsObject(obj)
    }
  }
}
