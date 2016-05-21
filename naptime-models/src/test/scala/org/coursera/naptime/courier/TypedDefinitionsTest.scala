package org.coursera.naptime.courier

import org.coursera.naptime.courier.TestTypedDefinition.TestTypedDefinitionAlphaMember
import org.coursera.naptime.courier.TestTypedDefinition.TestTypedDefinitionBetaMember
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class TypedDefinitionsTest extends AssertionsForJUnit {
  @Test
  def typeNameForInstance(): Unit = {
    val alphaMember = TestTypedDefinition.TestTypedDefinitionAlphaMember(TestTypedDefinitionAlpha())
    assertResult("alpha")(TypedDefinitions.typeName(alphaMember))

    val betaMember = TestTypedDefinition.TestTypedDefinitionBetaMember(TestTypedDefinitionBeta())
    assertResult("beta")(TypedDefinitions.typeName(betaMember))
  }

  @Test
  def typeNameForClass(): Unit = {
    assertResult("alpha")(TypedDefinitions.typeName(TestTypedDefinitionAlphaMember))
    assertResult("beta")(TypedDefinitions.typeName(TestTypedDefinitionBetaMember))
  }

}
