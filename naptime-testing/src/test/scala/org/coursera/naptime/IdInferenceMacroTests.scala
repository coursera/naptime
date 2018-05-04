package org.coursera.naptime

import java.util.UUID

import akka.stream.Materializer
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.common.jsonformat.JsonFormats.Implicits.dateTimeFormat
import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.resources.CourierCollectionResource
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.coursera.naptime.router2.Router
import org.joda.time.DateTime
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites

import scala.util.Try
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object IdInferenceMacroTests {
  sealed trait CourseId
  object CourseId {
    implicit val stringKeyFormat = StringKeyFormat.unimplementedFormat[CourseId]
    implicit val keyFormat = KeyFormat.idAsStringOnly

    def apply(idString: String): CourseId = {
      Try(idString.toInt).map(LegacyCourseId).getOrElse(NewCourseId(idString))
    }
  }
  case class LegacyCourseId(id: Int) extends CourseId
  case class NewCourseId(id: String) extends CourseId

  class CourseResource(
      implicit override val executionContext: ExecutionContext,
      override val materializer: Materializer)
      extends CourierCollectionResource[CourseId, Course] {
    override def resourceName: String = "courses"
    def getAll = Nap.getAll(ctx => ???)
  }

  object CourseResource {
    val routerBuilder = Router.build[CourseResource]
  }

  case class UserId(id: Int)
  object UserId {
    implicit val stringKeyFormat: StringKeyFormat[UserId] =
      StringKeyFormat.caseClassFormat(apply, unapply)
    implicit val keyFormat: KeyFormat[UserId] = KeyFormat.idAsPrimitive(apply, unapply)
  }

  sealed trait MembershipId {
    def userId: UserId
    def courseId: CourseId
  }

  object MembershipId {
    implicit val stringKeyFormat = StringKeyFormat.unimplementedFormat[MembershipId]
    implicit val keyFormat: KeyFormat[MembershipId] = {
      val writes = OWrites[MembershipId] { membershipId =>
        Json.obj("userId" -> membershipId.userId, "courseId" -> membershipId.courseId)
      }
      KeyFormat.idAsStringWithFields(writes)
    }
    def apply(userId: UserId, courseId: CourseId): MembershipId = ???
  }

  case class CourseGrade(score: Double, issued: DateTime)

  object CourseGrade {
    implicit val jsonFormat = Json.format[CourseGrade]
  }

  case class Membership(enrolledTimestamp: Option[DateTime], grade: Option[CourseGrade])

  object Membership {
    implicit val jsonFormat = Json.format[Membership]
  }

  class MembershipResource(
      implicit val executionContext: ExecutionContext,
      val materializer: Materializer)
      extends TopLevelCollectionResource[MembershipId, Membership] {
    override def keyFormat: KeyFormat[KeyType] = MembershipId.keyFormat
    override implicit def resourceFormat: OFormat[Membership] = Membership.jsonFormat
    override def resourceName: String = "memberships"
    implicit val fields = Fields

    def getAll = Nap.getAll(ctx => ???)
  }
  object MembershipResource {
    val routerBuilder = Router.build[MembershipResource]
  }

  case class PaymentId(id: UUID)
  object PaymentId {
    implicit val stringKeyFormat: StringKeyFormat[PaymentId] =
      StringKeyFormat.caseClassFormat(apply, unapply)
    implicit val keyFormat: KeyFormat[PaymentId] = KeyFormat.idAsPrimitive(apply, unapply)
  }

  class PaymentResource(
      implicit val executionContext: ExecutionContext,
      val materializer: Materializer)
      extends TopLevelCollectionResource[PaymentId, Membership] {
    override def keyFormat: KeyFormat[KeyType] = PaymentId.keyFormat
    override implicit def resourceFormat: OFormat[Membership] = Membership.jsonFormat
    override def resourceName: String = "payments"
    implicit val fields = Fields

    def getAll = Nap.getAll(ctx => ???)
  }
  object PaymentResource {
    val routerBuilder = Router.build[PaymentResource]
  }
}

class IdInferenceMacroTests extends AssertionsForJUnit {

  @Test
  def coursesTypesGeneration(): Unit = {
    val types = IdInferenceMacroTests.CourseResource.routerBuilder.types
    assert(3 === types.size, s"$types")
    val resourceType = types
      .find(_.key == "org.coursera.naptime.IdInferenceMacroTests.CourseResource.Model")
      .getOrElse {
        assert(false, s"Could not find merged type in types list $types")
        ???
      }
    assert(!resourceType.value.hasError)
    assert(resourceType.value.isComplex)
    assert(resourceType.value.getType === DataSchema.Type.RECORD)
    assert(resourceType.value.isInstanceOf[RecordDataSchema])
    assert(resourceType.value.asInstanceOf[RecordDataSchema].getFields.size() === 4)
  }

  @Test
  def membershipsTypesGeneration(): Unit = {
    val types = IdInferenceMacroTests.MembershipResource.routerBuilder.types
    assert(3 === types.size, s"$types")
    val resourceType = types
      .find(_.key == "org.coursera.naptime.IdInferenceMacroTests.MembershipResource.Model")
      .getOrElse {
        assert(false, s"Could not find merged type in types list $types")
        ???
      }
    assert(!resourceType.value.hasError)
    assert(resourceType.value.isComplex)
    assert(resourceType.value.getType === DataSchema.Type.RECORD)
    assert(resourceType.value.isInstanceOf[RecordDataSchema])
    assert(resourceType.value.asInstanceOf[RecordDataSchema].getFields.size() === 5)
  }

  @Test
  def paymentsTypesGeneration(): Unit = {
    val types = IdInferenceMacroTests.PaymentResource.routerBuilder.types
    // There's a merged model and a body model, but no key model because we can't infer that
    assert(2 === types.size, s"$types")
    val resourceType = types
      .find(_.key == "org.coursera.naptime.IdInferenceMacroTests.PaymentResource.Model")
      .getOrElse {
        assert(false, s"Could not find merged type in types list $types")
        ???
      }
    assert(!resourceType.value.hasError)
    assert(resourceType.value.isComplex)
    assert(resourceType.value.getType === DataSchema.Type.RECORD)
    assert(resourceType.value.isInstanceOf[RecordDataSchema])
    assert(resourceType.value.asInstanceOf[RecordDataSchema].getFields.size() === 3)
    assert(
      resourceType.value
        .asInstanceOf[RecordDataSchema]
        .getFields
        .asScala
        .exists(_.getName == "id"))
  }
}
