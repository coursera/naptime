package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.Types.Relations
import org.coursera.naptime.schema.GraphQLRelationAnnotation
import com.linkedin.data.schema.RecordDataSchema.Field

import scala.collection.JavaConverters._

object GraphQLRelation {

  def parse(field: Field): Option[GraphQLRelationAnnotation] = {
    field.getProperties.asScala.get(Relations.RELATION_PROPERTY_NAME).map { d =>
      GraphQLRelationAnnotation.build(d.asInstanceOf[DataMap], DataConversion.SetReadOnly)
    }
  }
}
