package stores

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton

import com.linkedin.data.DataMap
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.example.AnyData
import org.coursera.example.Course
import org.coursera.example.CourseMetadata
import org.coursera.naptime.model.Keyed

import scala.collection.JavaConverters._

@Singleton
class CourseStore {
  @volatile
  var courseStore = Map.empty[String, Course]
  val nextId = new AtomicInteger(0)

  courseStore = courseStore + (
    "ml" -> Course(
      instructors = List("andrew-ng"),
      partner = "stanford",
      slug = "machine-learning",
      name = "Machine Learning",
      description = Some("Machine learning is the science of getting computers to act without being explicitly programmed."),
      extraData = AnyData(new DataMap(
        Map("firstModuleId" -> "wrh7vtpj").asJava),
        DataConversion.SetReadOnly),
      courseMetadata = CourseMetadata(
        certificateInstructor = "andrew-ng")),
    "lhtl" -> Course(
      instructors = List("barb-oakley"),
      partner = "ucsd",
      slug = "learning-how-to-learn",
      name = "Learning How to Learn",
      description = None,
      extraData = AnyData(new DataMap(
        Map("recentEnrollments" -> new Integer(1000)).asJava),
        DataConversion.SetReadOnly),
      courseMetadata = CourseMetadata(
        certificateInstructor = "andrew-ng")))

  def get(id: String) = courseStore.get(id)

  def create(course: Keyed[String, Course]): Unit = {
    courseStore = courseStore + (course.key -> course.value)
  }

  def all() = courseStore
}
