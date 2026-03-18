package org.coursera.naptime

import org.coursera.naptime.schema.HandlerArray
import org.coursera.naptime.schema.AttributeArray
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

class ResourceNameTest extends AssertionsForJUnit {

  @Test
  def simpleResourceNameIdentifier(): Unit = {
    assert(ResourceName("foo", 1).identifier === "foo.v1")
  }

  @Test
  def nestedResourceNameIdentifier(): Unit = {
    assert(ResourceName("fooBar", 1, List("history")).identifier === "fooBar.v1/history")
  }

  @Test
  def deeplyNestedResourceNameIdenfier(): Unit = {
    assert(
      ResourceName("fooBarBaz", 103, List("history", "author")).identifier ===
        "fooBarBaz.v103/history/author")
  }

  @Test
  def parseSimple(): Unit = {
    assert(ResourceName("foo", 1) === ResourceName.parse("foo.v1").get)
  }

  @Test
  def parseNested(): Unit = {
    assert(ResourceName("fooBar", 2, List("sub")) === ResourceName.parse("fooBar.v2/sub").get)
  }

  @Test
  def parseDeeplyNested(): Unit = {
    assert(
      ResourceName("fooBar", 3, List("sub", "superSub")) ===
        ResourceName.parse("fooBar.v3/sub/superSub").get)
  }

  @Test
  def fromResource_extractsNameAndVersion(): Unit = {
    // Exercises ResourceName.fromResource (line 468).
    val resource = Resource(
      kind = ResourceKind.COLLECTION,
      name = "courses",
      version = Some(3L),
      parentClass = None,
      keyType = "string",
      valueType = "org.coursera.Value",
      mergedType = "org.coursera.Merged",
      handlers = HandlerArray(),
      className = "org.coursera.CoursesResource",
      attributes = AttributeArray())
    val name = ResourceName.fromResource(resource)
    assert(name === ResourceName("courses", 3))
  }

  @Test
  def fromResource_withNoVersion_usesZero(): Unit = {
    val resource = Resource(
      kind = ResourceKind.COLLECTION,
      name = "items",
      version = None,
      parentClass = None,
      keyType = "string",
      valueType = "org.coursera.Value",
      mergedType = "org.coursera.Merged",
      handlers = HandlerArray(),
      className = "org.coursera.ItemsResource",
      attributes = AttributeArray())
    val name = ResourceName.fromResource(resource)
    assert(name === ResourceName("items", 0))
  }

  @Test
  def parse_invalidInput_returnsNone(): Unit = {
    // Exercises the `case _ => None` branch in ResourceName.parse.
    assert(ResourceName.parse("not-a-resource-name") === None)
    assert(ResourceName.parse("") === None)
  }
}
