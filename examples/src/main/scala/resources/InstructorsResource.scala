package resources

import javax.inject.Inject
import javax.inject.Singleton

import org.coursera.example.Instructor
import org.coursera.naptime.Fields
import org.coursera.naptime.FinderReverseRelation
import org.coursera.naptime.Ok
import org.coursera.naptime.ResourceName
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.CourierCollectionResource
import stores.InstructorStore

@Singleton
class InstructorsResource @Inject() (
    instructorStore: InstructorStore)
  extends CourierCollectionResource[Int, Instructor] {

  override def resourceName = "instructors"
  override def resourceVersion = 1
  override implicit lazy val Fields: Fields[Instructor] = BaseFields
    .withReverseRelations(
      "courses" -> FinderReverseRelation(
        resourceName = ResourceName("courses", 1),
        finderName = "byInstructor",
        arguments = Map("instructorId" -> "$id")))

  def get(id: Int) = Nap.get { context =>
    OkIfPresent(id, instructorStore.get(id))
  }

  def multiGet(ids: Set[Int]) = Nap.multiGet { context =>
    Ok(instructorStore.all()
      .filter(instructor => ids.contains(instructor._1))
      .map { case (id, instructor) => Keyed(id, instructor) }.toList)
  }

  def getAll() = Nap.getAll { context =>
    Ok(instructorStore.all().map { case (id, instructor) => Keyed(id, instructor) }.toList)
  }

}
