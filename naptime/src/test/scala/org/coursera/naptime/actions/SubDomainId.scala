package org.coursera.naptime.actions

import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.naptime.model.KeyFormat

case class SubdomainId(id: Slug)

object SubdomainId {

  implicit val stringKeyFormat: StringKeyFormat[SubdomainId] =
    StringKeyFormat.caseClassFormat(apply, unapply)

  implicit val keyFormat: KeyFormat[SubdomainId] = KeyFormat.idAsStringOnly

}
