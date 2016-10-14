package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.schema.Resource


case class SchemaMetadata(
    resources: Set[Resource],
    schemas: Map[String, RecordDataSchema]) {

  def getResource(resourceName: String): Resource = {
    resources.find { resource =>
      ResourceName(resource.name, resource.version.getOrElse(0L).toInt).identifier == resourceName
    }.getOrElse {
      throw new RuntimeException(s"Cannot find resource with name $resourceName")
    }
  }

  def getSchema(resource: Resource): Option[RecordDataSchema] = {
    schemas.get(resource.mergedType).flatMap(Option(_))
  }
}
