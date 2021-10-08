package org.coursera.naptime.courier.validation

import com.linkedin.data.schema.validation.{CoercionMode => PegasusCoercionMode}

sealed trait CoercionMode {
  def toPegasus: PegasusCoercionMode
}

object CoercionMode {
  case object NORMAL extends CoercionMode {
    override def toPegasus: PegasusCoercionMode = PegasusCoercionMode.NORMAL
  }
  case object STRING_TO_PRIMITIVE extends CoercionMode {
    override def toPegasus: PegasusCoercionMode = PegasusCoercionMode.STRING_TO_PRIMITIVE
  }
}
