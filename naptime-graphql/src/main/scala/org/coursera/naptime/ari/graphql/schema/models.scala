package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.schema.Context

case class ParentContext(parentContext: Context[SangriaGraphQlContext, DataMap])
