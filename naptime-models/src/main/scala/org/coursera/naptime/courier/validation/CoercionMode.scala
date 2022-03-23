/*
  Copyright (c) 2021 Coursera, Inc.

  This file has been modified by Coursera, Inc. to loosen
  validation of enums.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

/*
   Copyright (c) 2012 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
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
