package org.coursera.naptime

import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.couriertests.ExpectedMergedCourse
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.CourierCollectionResource
import org.coursera.naptime.router2.Router
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

/**
 * This test suite uses Courier to exercise advanced use cases for Naptime.
 */
object NestedMacroCourierTests {

  class CoursesResource extends CourierCollectionResource[String, Course] {
    override def resourceName: String = "courses"

    override implicit lazy val Fields: Fields[Course] = BaseFields.withRelated("instructors" -> ResourceName("instructors", 1))

    /**
     * This `var` can be overridden to help fake out the implementation in particular functions.
     */
    var relatedFunction: Option[String => List[String]] = None

    private[this] def fallbackRelatedFunction(id: String): List[String] = {
      id match {
        case "abc" => List("123")
        case "xyz" => List.empty
        case "qrs" => List("456", "789")
        case _ => List.empty
      }
    }

    private[this] def makeCourse(id: String): Keyed[String, Course] = {
      val relatedInstructors = relatedFunction.getOrElse(fallbackRelatedFunction _)(id)

      Keyed(id, Course(s"course-$id", s"$id description", relatedInstructors))
    }

    def get(id: String) = Nap.get { ctx =>
      Ok(makeCourse(id))
    }

    def multiGet(ids: Set[String]) = Nap.multiGet { ctx =>
      Ok(ids.map(id => makeCourse(id)).toList)
    }

    def search(query: Option[String]) = Nap.finder { ctx =>
      val ids = query.map(List(_)).getOrElse(List("abc", "qrs"))
      Ok(ids.map(id => makeCourse(id)))
    }

    def byInstructor(instructorId: String) = Nap.finder { ctx =>
      val ids = instructorId match {
        case "123" => List("xyz")
        case "456" => List("xyz", "abc")
        case "789" => List("qrs")
        case _ => List.empty
      }
      Ok(ids.map(id => makeCourse(id)))
    }
  }

  class InstructorsResource extends CourierCollectionResource[String, Instructor] {
    override def resourceName: String = "instructors"

    def multiGet(ids: Set[String]) = Nap.multiGet { ctx =>
      ???
    }

  }

  val courseRouter = Router.build[CoursesResource]
  val instructorRouter = Router.build[InstructorsResource]
}

class NestedMacroCourierTests extends AssertionsForJUnit {

  @Test
  def checkCoursesMergedType(): Unit = {
    val types = NestedMacroCourierTests.courseRouter.types
    assert(3 === types.size, s"Got $types")
    val mergedTypeOpt = types.find(_.key == "org.coursera.naptime.NestedMacroCourierTests.CoursesResource.Model")
    assert(mergedTypeOpt.isDefined)
    val mergedType = mergedTypeOpt.get
    assert(!mergedType.value.hasError)
    assert(mergedType.value.getType === DataSchema.Type.RECORD)
    val mergedValueRecord = mergedType.value.asInstanceOf[RecordDataSchema]
    assert(4 === mergedValueRecord.getFields.size(), mergedValueRecord)
    val instructorsField = mergedValueRecord.getField("instructors")
    assert(null != instructorsField, instructorsField)
    assert(null != instructorsField.getProperties)
    val typesProperty = instructorsField.getProperties.get(Types.Relations.PROPERTY_NAME)
    assert(null != typesProperty)
    assert(typesProperty.isInstanceOf[String])
    assert(typesProperty === "instructors.v1")
  }

  @Test
  def checkMergedCourseModelSchema(): Unit = {
    val types = NestedMacroCourierTests.courseRouter.types
    val mergedType = types.find(_.key == "org.coursera.naptime.NestedMacroCourierTests.CoursesResource.Model").get
    assert(ExpectedMergedCourse.SCHEMA.getFields === mergedType.value.asInstanceOf[RecordDataSchema].getFields)
  }
}
