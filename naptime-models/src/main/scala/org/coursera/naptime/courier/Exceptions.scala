/*
 * Copyright 2017 Coursera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.coursera.naptime.courier

import java.io.IOException

object Exceptions {

  // TODO(mkovacs): consider moving away from IOException
  sealed class SerializationException(val message: String) extends IOException(message)

  class ReadException(override val message: String) extends SerializationException(message)
  class WriteException(override val message: String) extends SerializationException(message)

}
