package org.coursera.naptime.courier.validation

import com.linkedin.data.schema.validation.{RequiredMode => PegasusRequiredMode}

sealed trait RequiredMode {
  def toPegasus: PegasusRequiredMode
}

object RequiredMode {
  case object CAN_BE_ABSENT_IF_HAS_DEFAULT extends RequiredMode {
    override def toPegasus: PegasusRequiredMode = PegasusRequiredMode.CAN_BE_ABSENT_IF_HAS_DEFAULT
  }
  case object FIXUP_ABSENT_WITH_DEFAULT extends RequiredMode {
    override def toPegasus: PegasusRequiredMode = PegasusRequiredMode.FIXUP_ABSENT_WITH_DEFAULT
  }
}
