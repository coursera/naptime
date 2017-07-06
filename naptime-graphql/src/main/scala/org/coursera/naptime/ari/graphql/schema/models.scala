package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.schema.Context

case class ParentContext(parentContext: Context[SangriaGraphQlContext, DataMap])

case class ParentModel(
    resourceName: ResourceName,
    value: DataMap,
    schema: RecordDataSchema)

trait ElementWithParent[T] {
  val element: T
  val parentModel: ParentModel
}

case class DataMapWithParent(element: DataMap, parentModel: ParentModel) extends ElementWithParent[DataMap]

case class AnyWithParent(element: Any, parentModel: ParentModel) extends ElementWithParent[Any]
