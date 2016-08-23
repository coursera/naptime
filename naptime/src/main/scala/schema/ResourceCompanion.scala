package schema

import org.coursera.naptime.schema.Resource

object ResourceCompanion {

  def versionedName(resource: Resource): String = {
    s"${resource.name}.v${resource.version.getOrElse(0)}"
  }

}
