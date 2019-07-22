/*
 * Copyright 2018 Coursera Inc.
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

package org.coursera.naptime.actions

import akka.stream.Materializer
import org.coursera.naptime.RestError
import org.coursera.naptime.access.HeaderAccessControl
import org.coursera.naptime.model.KeyFormat
import play.api.Application
import play.api.libs.json.OFormat
import play.api.mvc.BodyParser

import scala.concurrent.ExecutionContext

/**
 * See [[RestActionBuilder]].
 *
 * This builder can hold body-dependent authorization definitions. In order to do this, the
 * builder must forbid changes to body type, and does so by omitting body-related builder methods.
 */
class DefinedBodyTypeRestActionBuilder[
    RACType,
    AuthType,
    BodyType,
    ResourceKeyType,
    ResourceType,
    ResponseType] private[actions] (
    authGeneratorOrAuth: AuthGenerator[BodyType, AuthType],
    bodyParser: BodyParser[BodyType],
    errorHandler: PartialFunction[Throwable, RestError])(
    implicit keyFormat: KeyFormat[ResourceKeyType],
    resourceFormat: OFormat[ResourceType],
    application: Application)
    extends RestActionBuilderTerminators[
      RACType,
      AuthType,
      BodyType,
      ResourceKeyType,
      ResourceType,
      ResponseType] {

  /**
   * Like [[RestActionBuilder.auth]], but with a body-aware generator function.
   */
  def auth[NewAuthType](authGenerator: BodyType => HeaderAccessControl[NewAuthType])
    : DefinedBodyTypeRestActionBuilder[
      RACType,
      NewAuthType,
      BodyType,
      ResourceKeyType,
      ResourceType,
      ResponseType] =
    new DefinedBodyTypeRestActionBuilder(authGenerator, bodyParser, errorHandler)

  /**
   * Adds an error handling function to allow exceptions to generate custom errors.
   *
   * Note: all of the partial functions are stacked, with later functions getting an earlier crack
   * at an exception to handle it.
   *
   * @param errorHandler Error handling partial function.
   * @return the immutable RestActionBuilder to be used to build the naptime resource action.
   */
  def catching(
      errorHandler: PartialFunction[Throwable, RestError]): DefinedBodyTypeRestActionBuilder[
    RACType,
    AuthType,
    BodyType,
    ResourceKeyType,
    ResourceType,
    ResponseType] =
    new DefinedBodyTypeRestActionBuilder(
      authGeneratorOrAuth,
      bodyParser,
      errorHandler.orElse(this.errorHandler))

  /**
   * Set the response type.
   * TODO: is this necessary?
   */
  def returning[NewResponseType](): DefinedBodyTypeRestActionBuilder[
    RACType,
    AuthType,
    BodyType,
    ResourceKeyType,
    ResourceType,
    NewResponseType] =
    new DefinedBodyTypeRestActionBuilder(authGeneratorOrAuth, bodyParser, errorHandler)

  override protected def bodyBuilder[Category, Response](): BodyBuilder[Category, Response] = {
    new RestActionBodyBuilder[
      Category,
      AuthType,
      BodyType,
      ResourceKeyType,
      ResourceType,
      Response](authGeneratorOrAuth, bodyParser, errorHandler)
  }

}
