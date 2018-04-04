package org.coursera.naptime

import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.junit._
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.Json

case class TestResource(name: String, description: String, author: Int)

object TestResource {
  implicit val format = Json.format[TestResource]

  def mk(id: Int): Keyed[Int, TestResource] = {
    Keyed(id, apply(s"TestResource$id", s"A test resource $id", id + 10))
  }
}

case class FakeUser(name: String, profile: String, almaMater: Int)

object FakeUser {
  implicit val format = Json.format[FakeUser]
}

case class University(name: String)

object University {
  implicit val format = Json.format[University]
}

class JsonUtilitiesTest extends AssertionsForJUnit {
  val obj = Json.obj("id" -> 123, "name" -> "hogwarts", "location" -> "platform 9 3/4")
  val fields = QueryFields(Set("id", "name"), Map.empty)

  @Test
  def simpleFilter() {
    val filtered = JsonUtilities.filterJsonFields(obj, fields)
    assert(filtered.fields.size === 2)
    assert(filtered.fields.map(_._1) === Seq("id", "name"))
  }

  @Test
  def ignoreUnknownNames() {
    val filtered = JsonUtilities.filterJsonFields(obj, QueryFields(Set("id", "foo"), Map.empty))
    assert(filtered.fields.size === 1)
    assert(filtered.fields.map(_._1) === Seq("id"))
  }

  @Test
  def outputsObjects() {
    val objs = (0 to 2).map(TestResource.mk).toSeq
    val jsonified = JsonUtilities.outputSeq(objs, QueryFields(Set("id"), Map.empty))
    assert("""[{"id":0},{"id":1},{"id":2}]""" === Json.stringify(Json.toJson(jsonified)))
  }

  @Test
  def metaLinksSimple(): Unit = {
    val fields = Fields[TestResource].withRelated("author" -> ResourceName("user", 1))
    val meta = JsonUtilities.formatLinksMeta(
      QueryIncludes(Set("author"), Map.empty),
      QueryFields.empty,
      fields,
      Ok(()))
    assert(meta === Json.obj("elements" -> Json.obj("author" -> "user.v1")))
  }

  @Test
  def metaLinksRecursed(): Unit = {
    val fields = Fields[TestResource].withRelated("author" -> ResourceName("user", 1))
    val userFields = Fields[FakeUser].withRelated("almaMater" -> ResourceName("university", 3))
    val result = Ok(()).withRelated(ResourceName("user", 1), Seq.empty[Keyed[Int, FakeUser]])(
      FakeUser.format,
      KeyFormat.intKeyFormat,
      userFields)
    val meta = JsonUtilities.formatLinksMeta(
      QueryIncludes(Set("author"), Map(ResourceName("user", 1) -> Set("almaMater"))),
      QueryFields(Set("author"), Map(ResourceName("user", 1) -> Set("almaMater"))),
      fields,
      result)
    val expected = Json.obj(
      "elements" -> Json.obj("author" -> "user.v1"),
      "user.v1" -> Json.obj("almaMater" -> "university.v3")
    )
    assert(meta === expected)
  }

  @Test
  def formatIncludes(): Unit = {
    val fields = Fields[TestResource].withRelated("author" -> ResourceName("user", 1))
    val userFields = Fields[FakeUser].withDefaultFields("name")
    val queryIncludes = QueryIncludes(Set("author"), Map.empty)

    val fakeUser = FakeUser("bob", "good", 1)
    val ok = Ok(()).withRelated(ResourceName("user", 1), List(Keyed(1, fakeUser)))(
      FakeUser.format,
      KeyFormat.intKeyFormat,
      userFields)

    val includesResult = JsonUtilities.formatIncludes(ok, QueryFields.empty, queryIncludes, fields)

    val expected = Some(
      Json.obj(
        "user.v1" -> Json.arr(
          Json.obj("id" -> 1, "name" -> "bob")
        )))
    assert(expected === includesResult)
  }

  @Test
  def formatTransitiveIncludes(): Unit = {
    val userResource = ResourceName("user", 1)
    val universityResource = ResourceName("universities", 1)
    implicit val fields = Fields[TestResource].withRelated("author" -> userResource)
    implicit val userFields =
      Fields[FakeUser].withDefaultFields("name").withRelated("almaMater" -> universityResource)
    implicit val uniFields = Fields[University].withDefaultFields("name")
    val queryIncludes = QueryIncludes(Set("author"), Map(userResource -> Set("almaMater")))

    val ok = Ok(())
      .withRelated(userResource, List(Keyed(1, FakeUser("bob", "good", 5))))
      .withRelated(universityResource, List(Keyed(5, University("ecole polytechnique"))))

    val includesResult = JsonUtilities.formatIncludes(ok, QueryFields.empty, queryIncludes, fields)

    val expected = Some(
      Json.obj(
        "user.v1" -> Json.arr(Json.obj("id" -> 1, "name" -> "bob")),
        "universities.v1" -> Json.arr(Json.obj("name" -> "ecole polytechnique", "id" -> 5))))
    assert(expected === includesResult)
  }

  @Test
  def formatIncludesNotRequested(): Unit = {
    val fields = Fields[TestResource].withRelated("author" -> ResourceName("user", 1))
    val userFields = Fields[FakeUser].withDefaultFields("name")
    val queryIncludes = QueryIncludes(Set("nonexistent"), Map.empty)

    val fakeUser = FakeUser("bob", "good", 1)
    val ok = Ok(()).withRelated(ResourceName("user", 1), List(Keyed(1, fakeUser)))(
      FakeUser.format,
      KeyFormat.intKeyFormat,
      userFields)

    val includesResult = JsonUtilities.formatIncludes(ok, QueryFields.empty, queryIncludes, fields)

    val expected = Some(Json.obj())

    assert(expected === includesResult)
  }

}
