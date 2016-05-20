package org.coursera.naptime.courier

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class CourierSerializerTest extends AssertionsForJUnit {
  import CourierTestFixtures._

  @Test
  def testRecordTemplates(): Unit = {
    val mock = CourierSerializer.read[TypedDefinitionRecord](typedDefinitionJson)
    val roundTripped = CourierSerializer.write(mock)
    assert(
      CourierSerializer.read[TypedDefinitionRecord](roundTripped) ===
        CourierSerializer.read[TypedDefinitionRecord](typedDefinitionJson))
  }

  @Test
  def testUnionTemplates(): Unit = {
    val mock = CourierSerializer.readUnion[MockTyperefUnion](mockTyperefUnionJson)
    val roundTripped = CourierSerializer.writeUnion(mock)
    assert(
      CourierSerializer.readUnion[MockTyperefUnion](roundTripped) ===
        CourierSerializer.readUnion[MockTyperefUnion](mockTyperefUnionJson))
  }
}
