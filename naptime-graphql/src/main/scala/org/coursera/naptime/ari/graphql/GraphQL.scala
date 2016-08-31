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

package org.coursera.naptime.ari.graphql

import javax.inject.Inject

import org.coursera.naptime.ari.EngineApi
import play.api.mvc.Action

/**
 * An (stub) implementation of a GraphQL Play endpoint based on the Naptime automatic resource inclusion engine.
 * @param engine The automatic resource inclusion engine that fetches data.
 */
class GraphQL @Inject() (engine: EngineApi) {

  def handleRequest = Action.async { request =>
    ???
  }
}
