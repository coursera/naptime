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

import javax.inject.Inject

import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ari.FullSchema
import org.coursera.naptime.ari.SchemaProvider
import sangria.schema.Field
import sangria.schema.ObjectType
import sangria.schema.Schema
import sangria.schema.StringType

import scala.util.control.NonFatal

/**
 * Provides GraphQL schemas for other components of the ARI+GraphQL system.
 */
trait GraphqlSchemaProvider {
  def schema: Schema[org.coursera.naptime.ari.graphql.SangriaGraphQlContext, Any]
}

/**
 * A GraphQL Schema Provider implementation.
 *
 * We compute the schema and cache it for performance reasons.
 *
 * Note: we assume that the schemaProvider can return different schemas over time. We also assume
 * that they change relatively slowly.
 *
 * Note: we rely on object identity to ensure an efficient set comparison. (This is a reasonably good
 * approach, because we assume immutable collections. Therefore we know we will never skip re-computing
 * when we should.
 *
 * Note: we avoid taking locks to avoid thread contention. We accept this at the cost of potentially
 * re-computing the schema multiple times upon schema change. Additionally, we do not use any volatile
 * variables, but instead rely on the JVM's guarantee that object references are atomically written.
 *
 * @param schemaProvider A schema provider.
 */
class DefaultGraphqlSchemaProvider @Inject() (schemaProvider: SchemaProvider)
  extends GraphqlSchemaProvider with StrictLogging {
  private[this] var fullSchema = FullSchema.empty
  private[this] var cachedSchema: Schema[SangriaGraphQlContext, Any] = DefaultGraphqlSchemaProvider.EMPTY_SCHEMA

  private[this] def recomputeSchema(latestSchema: FullSchema): Unit = {
    val typesMap = latestSchema.types.collect {
      case record: RecordDataSchema => record.getFullName -> record
    }.toMap

    val types = latestSchema.resources.flatMap { resource =>
      typesMap.get(resource.mergedType).map(resource.mergedType -> _).orElse {
        logger.warn(s"Did not find merged type `${resource.mergedType}` for resource " +
          s"${resource.name}.v${resource.version.getOrElse(0L)}")
        None
      }
    }.toMap
    try {
      val builder = new SangriaGraphQlSchemaBuilder(latestSchema.resources, types)
      val graphQlSchema =
        builder.generateSchema().asInstanceOf[Schema[SangriaGraphQlContext, Any]]
      fullSchema = latestSchema
      cachedSchema = graphQlSchema
    } catch {
      case NonFatal(e) =>
        logger.error(s"Could not build schema.", e)
        fullSchema = latestSchema
        // Note: we do not update cachedSchema, but instead retain the existing schema.
    }
  }

  private[this] def checkSchema(): Unit = {
    val latestSchema = schemaProvider.fullSchema
    // Check object identity for a cheap first check
    if (!(this.fullSchema eq latestSchema) && this.fullSchema != latestSchema) {
      recomputeSchema(latestSchema)
    }
  }

  override def schema: Schema[SangriaGraphQlContext, Any] = {
    checkSchema()
    cachedSchema
  }
}

object DefaultGraphqlSchemaProvider {
  val EMPTY_FIELDS = List(
    Field.apply[SangriaGraphQlContext, Any, Any, Any](
      "ArbitraryField",
      StringType,
      resolve = context => null))
  val EMPTY_SCHEMA = Schema[SangriaGraphQlContext, Any](query = ObjectType[SangriaGraphQlContext, Any](name = "root", fields = EMPTY_FIELDS))
}
