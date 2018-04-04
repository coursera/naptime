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

package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.schema.Resource

case class SchemaMetadata(resources: Set[Resource], schemas: Map[String, RecordDataSchema]) {

  def getResource(resourceName: ResourceName): Resource = {
    resources
      .find { resource =>
        ResourceName.fromResource(resource) == resourceName
      }
      .getOrElse {
        throw new RuntimeException(s"Cannot find resource with name $resourceName")
      }
  }

  def getResourceOpt(resourceName: ResourceName): Option[Resource] = {
    resources.find { resource =>
      ResourceName.fromResource(resource) == resourceName
    }
  }

  def getSchema(resource: Resource): Option[RecordDataSchema] = {
    schemas.get(resource.mergedType).flatMap(Option(_))
  }
}
