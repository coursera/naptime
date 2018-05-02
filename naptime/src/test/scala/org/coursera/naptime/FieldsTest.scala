package org.coursera.naptime

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.Json
import play.api.test.FakeRequest

import scala.util.Success

case class FieldsTestModel(a: String, b: Int, c: String)
object FieldsTestModel {
  implicit val format = Json.format[FieldsTestModel]
}

class FieldsTest extends AssertionsForJUnit {

  val sampleFields =
    Fields[FieldsTestModel].withDefaultFields("a").withRelated("author" -> ResourceName("user", 6))

  @Test
  def simpleComputeFields(): Unit = {
    testSuccess("/foo/bar?fields=c", QueryFields(Set("a", "c"), Map.empty))
  }

  @Test
  def testMissing(): Unit = {
    testSuccess("/foo/bar", QueryFields(Set("a"), Map.empty))
  }

  @Test
  def testNestedResources(): Unit = {
    testSuccess(
      "/foo/bar?fields=foo.v1(z)",
      QueryFields(Set("a"), Map(ResourceName("foo", 1) -> Set("z"))))
  }

  @Test
  def testNegatedResources(): Unit = {
    testSuccess("/foo/bar?fields=-a,b,c", QueryFields(Set("b", "c"), Map.empty))
  }

  @Test
  def testEmpty(): Unit = {
    testSuccess("/foo/bar?fields=", QueryFields(Set("a"), Map.empty))
  }

  @Test
  def handleImproperlyFormedRequest(): Unit = {
    testFailure("/foo/bar?fields=,a")
  }

  @Test
  def handleImproperlyFormedRequest2(): Unit = {
    testFailure("/foo/bar?fields=a.vb(a,b)")
  }

  @Test
  def headerFieldsOverride(): Unit = {
    val request = FakeRequest("GET", "/foo/bar").withHeaders(Fields.FIELDS_HEADER -> "ALL")
    val parsed = sampleFields.computeFields(request)

    assert(parsed === Success(AllFields))
  }

  private[this] def testSuccess(path: String, fields: QueryFields): Unit = {
    val request = FakeRequest("GET", path)
    val parsed = sampleFields.computeFields(request)
    assert(parsed === Success(fields))
  }

  private[this] def testFailure(path: String): Unit = {
    val request = FakeRequest("GET", path)
    val parsed = sampleFields.computeFields(request)
    assert(parsed.isFailure)
  }

  @Test
  def testSimpleRelated(): Unit = {
    assert(sampleFields.makeMetaRelationsMap(Set("author")) === Json.obj("author" -> "user.v6"))
  }

  @Test
  def multiRelated(): Unit = {
    val related = sampleFields
      .withRelated("comments" -> ResourceName("qaComments", 1, List("history")))
      .withRelated("relatedItem" -> ResourceName("relations", 203))
    assert(
      related.makeMetaRelationsMap(Set("author", "comments")) ===
        Json.obj("author" -> "user.v6", "comments" -> "qaComments.v1/history"))
  }

  @Test
  def repeatedFieldsInvalid(): Unit = {
    intercept[IllegalArgumentException] {
      Fields[FieldsTestModel]
        .withDefaultFields("a")
        .withDefaultFields("a")
    }
  }

  @Test
  def repeatedRelationsInvalid(): Unit = {
    intercept[IllegalArgumentException] {
      Fields[FieldsTestModel]
        .withRelated("author" -> ResourceName("user", 6))
        .withRelated("author" -> ResourceName("user", 1))
    }
  }
}
