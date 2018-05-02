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

package org.coursera.naptime.ari.graphql

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ari.graphql.schema.NaptimeTopLevelResourceField
import org.coursera.naptime.ari.graphql.schema.SchemaErrors
import org.coursera.naptime.ari.graphql.schema.SchemaMetadata
import org.coursera.naptime.ari.graphql.schema.WithSchemaErrors
import org.coursera.naptime.schema.Resource
import sangria.schema.Context
import sangria.schema.Schema
import sangria.schema.Value
import sangria.schema.Field
import sangria.schema.ObjectType

class SangriaGraphQlSchemaBuilder(resources: Set[Resource], schemas: Map[String, RecordDataSchema])
    extends StrictLogging {

  val schemaMetadata = SchemaMetadata(resources, schemas)

  /**
   * Generates a GraphQL schema for the provided set of resources to this class
   * Returns a "root" object that has one field available for each Naptime Resource provided.
   *
   * @return a Sangria GraphQL Schema with all resources defined,
   *         and a list of errors that came up while generating the schema
   */
  def generateSchema(): WithSchemaErrors[Schema[SangriaGraphQlContext, DataMap]] = {
    val topLevelResourceObjectsAndErrors = resources.map { resource =>
      val lookupTypeAndErrors =
        NaptimeTopLevelResourceField.generateLookupTypeForResource(resource, schemaMetadata)
      val fields = lookupTypeAndErrors.data.flatMap { resourceObject =>
        if (resourceObject.fields.nonEmpty) {
          Some(
            Field.apply[SangriaGraphQlContext, DataMap, DataMap, Any](
              NaptimeTopLevelResourceField.formatResourceTopLevelName(resource),
              resourceObject,
              resolve = (_: Context[SangriaGraphQlContext, DataMap]) => {
                Value(new DataMap())
              }
            ))
        } else {
          None
        }
      }
      lookupTypeAndErrors.copy(data = fields)
    }

    val topLevelResourceObjects =
      topLevelResourceObjectsAndErrors.flatMap(_.data)
    val schemaErrors = topLevelResourceObjectsAndErrors.foldLeft(SchemaErrors.empty)(_ ++ _.errors)

    val dedupedResources =
      topLevelResourceObjects.groupBy(_.name).map(_._2.head).toList
    val rootObject = ObjectType[SangriaGraphQlContext, DataMap](
      name = "root",
      description = "Top-level accessor for Naptime resources",
      fields = dedupedResources)

    WithSchemaErrors(Schema(rootObject), schemaErrors)
  }
}
