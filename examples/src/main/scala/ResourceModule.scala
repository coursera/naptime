import org.coursera.naptime.NaptimeModule
import resources.UserStore
import resources.UserStoreImpl
import resources.UsersResource
import org.coursera.naptime.model.Keyed
import resources.CoursesResource
import resources.InstructorsResource
import resources.PartnersResource


class ResourceModule extends NaptimeModule {
  override def configure(): Unit = {
    bindResource[UsersResource]
    bindResource[CoursesResource]
    bindResource[InstructorsResource]
    bindResource[PartnersResource]
    bind[UserStore].to[UserStoreImpl]
  }
}
