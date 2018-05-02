package org.coursera.naptime

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

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
}
