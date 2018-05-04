package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.schema.Context

case class ParentContext(parentContext: Context[SangriaGraphQlContext, DataMap])

case class ParentModel(resourceName: ResourceName, value: DataMap, schema: RecordDataSchema)

case class DataMapWithParent(
    element: DataMap,
    parentModel: ParentModel,
    sourceUrl: Option[String] = None)
