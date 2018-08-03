package resources

import javax.inject.Inject
import javax.inject.Singleton

import akka.stream.Materializer
import org.coursera.example.Partner
import org.coursera.naptime.Fields
import org.coursera.naptime.MultiGetGraphQLRelation
import org.coursera.naptime.Ok
import org.coursera.naptime.ResourceName
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.CourierCollectionResource
import stores.PartnerStore

import scala.concurrent.ExecutionContext

@Singleton
class PartnersResource @Inject() (
    partnerStore: PartnerStore)(implicit ec: ExecutionContext, mat: Materializer)
  extends CourierCollectionResource[String, Partner] {

  override def resourceName = "partners"
  override def resourceVersion = 1
  override implicit lazy val Fields: Fields[Partner] = BaseFields
    .withGraphQLRelations(
      "instructors" -> MultiGetGraphQLRelation(
        resourceName = ResourceName("instructors", 1),
        ids = "$instructorIds"),
      "courses" -> MultiGetGraphQLRelation(
        resourceName = ResourceName("courses", 1),
        ids = "$courseIds"))

  def get(id: String) = Nap.get { context =>
    OkIfPresent(id, partnerStore.get(id))
  }

  def multiGet(ids: Set[String]) = Nap.multiGet { context =>
    Ok(partnerStore.all()
      .filter(partner => ids.contains(partner._1))
      .map { case (id, partner) => Keyed(id, partner) }.toList)
  }

  def getAll() = Nap.getAll { context =>
    Ok(partnerStore.all().map { case (id, partner) => Keyed(id, partner) }.toList)
  }

}
