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

import org.coursera.naptime.ari.Request
import play.api.libs.json.JsObject
import play.api.mvc.RequestHeader

/**
 * The GraphQlParser represents the GraphQL segment of the Naptime ARI presentation layer.
 * This segment is responsible for converting a GraphQL query (represented as a string) into a
 * common [[org.coursera.naptime.ari.Request]] class that can be parsed and evaluated by the ARI engine.
 */
trait GraphQlParser {

  def parse(request: String, variables: JsObject, requestHeader: RequestHeader): Option[Request]

}
