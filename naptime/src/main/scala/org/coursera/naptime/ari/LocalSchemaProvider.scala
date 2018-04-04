/*
 * Copyright 2016 Coursera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.coursera.naptime.ari

import javax.inject.Inject

import com.linkedin.data.schema.DataSchema
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
class LocalSchemaProvider @Inject()(naptimeRoutes: NaptimeRoutes)
    extends SchemaProvider
    with StrictLogging {

  private[this] val resourceSchemaMap: Map[ResourceName, Resource] =
    naptimeRoutes.schemaMap.flatMap {
      // TODO: handle sub resources
      case (_, schema)
          if schema.parentClass.isEmpty ||
            schema.parentClass.contains("org.coursera.naptime.resources.RootResource") =>
        val resourceName = ResourceName(schema.name, version = schema.version.getOrElse(0L).toInt)
        Some(resourceName -> schema)

      case (_, schema) =>
        logger.warn(s"Cannot handle nested resource $schema")
        None
    }

  private[this] val mergedTypes = naptimeRoutes.routerBuilders
    .flatMap(_.types.map(_.tuple))
    .filter(_._2.isInstanceOf[RecordDataSchema])
    .map(tuple => tuple._1 -> tuple._2.asInstanceOf[RecordDataSchema])
    .toMap

  override val fullSchema: FullSchema =
    FullSchema(
      naptimeRoutes.schemaMap.values.toSet,
      naptimeRoutes.routerBuilders.flatMap(_.types.map(_.value)))

  override def mergedType(resourceName: ResourceName): Option[RecordDataSchema] = {
    resourceSchemaMap.get(resourceName).flatMap { schema =>
      mergedTypes.get(schema.mergedType)
    }
  }
}
