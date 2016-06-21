package org.coursera.naptime.actions

import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.naptime.model.KeyFormat

case class DomainId(id: Slug)

object DomainId {

  implicit val stringKeyFormat: StringKeyFormat[DomainId] =
    StringKeyFormat.caseClassFormat(apply, unapply)

  implicit val keyFormat: KeyFormat[DomainId] = KeyFormat.idAsStringOnly

}
