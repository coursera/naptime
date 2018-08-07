package resources

import javax.inject.Inject
import javax.inject.Singleton

import akka.stream.Materializer
import org.coursera.example.Instructor
import org.coursera.naptime.Fields
import org.coursera.naptime.FinderGraphQLRelation
import org.coursera.naptime.GetGraphQLRelation
import org.coursera.naptime.Ok
import org.coursera.naptime.ResourceName
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.CourierCollectionResource
import stores.InstructorStore

import scala.concurrent.ExecutionContext

@Singleton
class InstructorsResource @Inject() (
    instructorStore: InstructorStore)(implicit ec: ExecutionContext, mat: Materializer)
  extends CourierCollectionResource[Int, Instructor] {

  override def resourceName = "instructors"
  override def resourceVersion = 1
  override implicit lazy val Fields: Fields[Instructor] = BaseFields
    .withGraphQLRelations(
      "courses" -> FinderGraphQLRelation(
        resourceName = ResourceName("courses", 1),
        finderName = "byInstructor",
        arguments = Map("instructorId" -> "$id")),
      "partner" -> GetGraphQLRelation(
        resourceName = ResourceName("partners", 1),
        id = "$partnerId"))

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
