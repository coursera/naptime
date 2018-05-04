package org.coursera.naptime.model

import org.coursera.common.jsonformat.JsonFormats
import org.coursera.naptime.model.KeyFormatTest.MembershipId
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.libs.json.Reads
import org.coursera.common.jsonformat.OrFormats.OrReads
import org.coursera.common.stringkey.StringKeyFormat

object KeyFormatTest {
  case class MembershipId(userId: Long, courseId: String)
  object MembershipId {

    implicit val stringKeyFormat: StringKeyFormat[MembershipId] = {
      StringKeyFormat.caseClassFormat((apply _).tupled, unapply)
    }

    val reads: Reads[MembershipId] =
      Json.reads[MembershipId].orReads(JsonFormats.stringKeyFormat[MembershipId])

    val keyFormat =
      KeyFormat.withFallbackReads(reads)(KeyFormat.idAsStringWithFields(Json.format[MembershipId]))
  }
}

class KeyFormatTest extends AssertionsForJUnit {

  @Test
  def testWithComplexReads(): Unit = {
    val oldSerialization = Json.obj("userId" -> 12345L, "courseId" -> "machine-learning")

    val newSerialization = JsString("12345~machine-learning")

    val expected = JsSuccess(MembershipId(12345L, "machine-learning"))

    assert(expected === MembershipId.keyFormat.reads(oldSerialization))
    assert(expected === MembershipId.keyFormat.reads(newSerialization))
  }
}
