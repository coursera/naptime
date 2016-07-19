/*
 * Copyright 2016 Coursera Inc.
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

package org.coursera.naptime.access

import org.coursera.naptime.NaptimeActionException

/**
 * Control access to an API using Naptime's DSL.
 *
 * Implementation note: This access control abstraction is independent of whether requests arrive
 * via HTTP or other means; until Naptime supports those other means, [[HeaderAccessControl]] is
 * the only access control type. When that's supported, this trait will likely have more methods
 * that make it more directly useful.
 *
 * @tparam A structured authentication result which is available to the API implementation after
 *           access control is successful
 */
trait AccessControl[A] {

  /**
   * Used for exercising access control configurations in resource tests.
   */
  private[naptime] def check(authInfo: A): Either[NaptimeActionException, A]

}
