package org.coursera.naptime.ari

import javax.inject.Inject

import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.schema.Resource

/**
 * Implements a default schema provider for local-only ARI operation.
 *
 * @param naptimeRoutes The locally available naptime routes.
 */
class LocalSchemaProvider @Inject() (naptimeRoutes: NaptimeRoutes) extends SchemaProvider with StrictLogging {
  private[this] val resourceSchemas: Map[ResourceName, Resource] = naptimeRoutes.schemaMap.flatMap {
    case (_, schema) if schema.parentClass.isEmpty => // TODO: handle sub resources
      val resourceName = ResourceName(schema.name, version = schema.version.getOrElse(0L).toInt)
      Some(resourceName -> schema)
    case (_, schema) =>
      logger.warn(s"Cannot handle nested resource $schema")
      None
  }

  private[this] val mergedTypes = naptimeRoutes.routerBuilders.flatMap(_.types.map(_.tuple))
    .filter(_._2.isInstanceOf[RecordDataSchema])
    .map(tuple => tuple._1 -> tuple._2.asInstanceOf[RecordDataSchema]).toMap

  override def resourceSchema(resourceName: ResourceName): Option[Resource] = resourceSchemas.get(resourceName)

  override def mergedType(resourceName: ResourceName): Option[RecordDataSchema] = {
    resourceSchema(resourceName).flatMap { schema =>
      mergedTypes.get(schema.mergedType)
    }
  }
}
