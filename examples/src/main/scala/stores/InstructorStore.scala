package stores

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton

import org.coursera.example.Instructor
import org.coursera.naptime.model.Keyed

@Singleton
class InstructorStore {
  @volatile
  var instructorStore = Map.empty[Int, Instructor]
  val nextId = new AtomicInteger(0)

  instructorStore = instructorStore + (
    1 -> Instructor(
      partner = "stanford",
      name = "Andrew Ng",
      photoUrl = ""),
    2 -> Instructor(
      partner = "ucsd",
      name = "Barb Oakley",
      photoUrl = ""))


  def get(id: Int) = instructorStore.get(id)

  def create(instructor: Keyed[Int, Instructor]): Unit = {
    instructorStore = instructorStore + (instructor.key -> instructor.value)
  }

  def all() = instructorStore
}
