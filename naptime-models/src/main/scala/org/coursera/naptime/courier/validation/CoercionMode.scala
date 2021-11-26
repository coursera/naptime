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
