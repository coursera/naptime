package org.coursera.naptime.courier.validation

import com.linkedin.data.it.Predicate
import com.linkedin.data.it.Predicates
import com.linkedin.data.schema.validation.{ValidationOptions => PegasusValidationOptions}

case class ValidationOptions(
    coercionMode: CoercionMode = CoercionMode.NORMAL,
    requiredMode: RequiredMode = RequiredMode.CAN_BE_ABSENT_IF_HAS_DEFAULT,
    treatOptional: Predicate = Predicates.alwaysFalse()) {
  def toPegasus: PegasusValidationOptions = {
    new PegasusValidationOptions(requiredMode.toPegasus, coercionMode.toPegasus)
  }
}
