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
