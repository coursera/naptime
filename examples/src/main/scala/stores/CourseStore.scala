package stores

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton

import com.linkedin.data.DataMap
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.example.AnyData
import org.coursera.example.CertificateCourseMetadata
import org.coursera.example.Course
import org.coursera.example.DegreeCourseMetadata
import org.coursera.naptime.model.Keyed

import scala.collection.JavaConverters._

@Singleton
class CourseStore {
  @volatile
  var courseStore = Map.empty[String, Course]
  val nextId = new AtomicInteger(0)

  courseStore = courseStore + (
    "ml" -> Course(
      instructorIds = List(1),
      partnerId = "stanford",
      slug = "machine-learning",
      name = "Machine Learning",
      description = Some("Machine learning is the science of getting computers to act without being explicitly programmed."),
      extraData = AnyData.build(new DataMap(
        Map("firstModuleId" -> "wrh7vtpj").asJava),
        DataConversion.SetReadOnly),
      courseMetadata = CertificateCourseMetadata(
        certificateInstructorId = 1)),
    "lhtl" -> Course(
      instructorIds = List(2),
      partnerId = "ucsd",
      slug = "learning-how-to-learn",
      name = "Learning How to Learn",
      description = None,
      extraData = AnyData.build(new DataMap(
        Map("recentEnrollments" -> new Integer(1000)).asJava),
        DataConversion.SetReadOnly),
      courseMetadata = DegreeCourseMetadata(
        degreeCertificateName = "iMBA")))

  def get(id: String) = courseStore.get(id)

  def create(course: Keyed[String, Course]): Unit = {
    courseStore = courseStore + (course.key -> course.value)
  }

  def all() = courseStore
}
