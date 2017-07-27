package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.Types.Relations
import org.coursera.naptime.schema.ReverseRelationAnnotation
import com.linkedin.data.schema.RecordDataSchema.Field

import scala.collection.JavaConverters._

object ReverseRelation {

  def parse(field: Field): Option[ReverseRelationAnnotation] = {
    field.getProperties.asScala.get(Relations.REVERSE_PROPERTY_NAME).map { d =>
      ReverseRelationAnnotation.build(d.asInstanceOf[DataMap], DataConversion.SetReadOnly)
    }
  }
}
