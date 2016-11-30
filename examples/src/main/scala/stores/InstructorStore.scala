package stores

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton

import org.coursera.example.Instructor
import org.coursera.naptime.model.Keyed

@Singleton
class InstructorStore {
  @volatile
  var instructorStore = Map.empty[String, Instructor]
  val nextId = new AtomicInteger(0)

  instructorStore = instructorStore + (
    "andrew-ng" -> Instructor(
      partner = "stanford",
      name = "Andrew Ng",
      photoUrl = ""),
    "barb-oakley" -> Instructor(
      partner = "ucsd",
      name = "Barb Oakley",
      photoUrl = ""))


  def get(id: String) = instructorStore.get(id)

  def create(instructor: Keyed[String, Instructor]): Unit = {
    instructorStore = instructorStore + (instructor.key -> instructor.value)
  }

  def all() = instructorStore
}
