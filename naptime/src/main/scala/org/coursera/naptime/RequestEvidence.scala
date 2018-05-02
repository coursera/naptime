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

package org.coursera.naptime

import org.coursera.naptime.resources.Resource

import scala.annotation.implicitNotFound

/**
 * Marker trait to try and hide access to the underlying Play request.
 */
@implicitNotFound(
  """Please avoid interacting directly with the underlying request, as this may surprise API users.
     If you must use the request directly, mix in DangerousAccessToUnderlyingRequest.
  """)
@deprecated("This is not used in Naptime anymore.")
sealed trait RequestEvidence

private[naptime] object RequestEvidence extends RequestEvidence

trait DangerousAccessToUnderlyingRequest { this: Resource[_] =>
  protected[this] implicit def requestEvidence: RequestEvidence =
    RequestEvidence
}
