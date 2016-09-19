package stores

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton

import org.coursera.example.Course
import org.coursera.naptime.model.Keyed

@Singleton
class CourseStore {
  @volatile
  var courseStore = Map.empty[String, Course]
  val nextId = new AtomicInteger(0)

  courseStore = courseStore + (
    "ml" -> Course(
      instructors = List("andrew-ng"),
      partners = List("stanford"),
      slug = "machine-learning",
      name = "Machine Learning",
      description = ""),
    "pgm" -> Course(
      instructors = List("barb-oakley"),
      partners = List("ucsd"),
      slug = "learning-how-to-learn",
      name = "Learning How to Learn",
      description = ""))

  def get(id: String) = courseStore.get(id)

  def create(course: Keyed[String, Course]): Unit = {
    courseStore = courseStore + (course.key -> course.value)
  }

  def all() = courseStore
}
