/*
   Copyright (c) 2021 Coursera Inc.

   Copyright (c) 2012 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
       http://www.apache.org/licenses/LICENSE-2.0

   This file has been modified by Coursera Inc. to loosen
   validation of enums.
 */

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
