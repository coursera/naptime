package resources

import javax.inject.Inject
import javax.inject.Singleton

import org.coursera.example.Partner
import org.coursera.naptime.Ok
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.resources.CourierCollectionResource
import stores.PartnerStore

@Singleton
class PartnersResource @Inject() (
    partnerStore: PartnerStore)
  extends CourierCollectionResource[String, Partner] {

  override def resourceName = "partners"
  override def resourceVersion = 1
  implicit val fields = Fields

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
