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

package org.coursera.naptime.exceptions

import play.api.mvc.Result

class NaptimeException(message: String = null, cause: Throwable = null)
    extends RuntimeException(message, cause)

class RoutingException(val message: String, val response: Result) extends NaptimeException(message)

class ResourceDefinitionException(val message: String) extends NaptimeException(message)
