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

package org.coursera.naptime.actions

import akka.stream.Materializer
import org.coursera.common.concurrent.Futures
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.Fields
import org.coursera.naptime.PaginationConfiguration
import org.coursera.naptime.RestContext
import org.coursera.naptime.RestError
import org.coursera.naptime.RestResponse
import org.coursera.naptime.access.HeaderAccessControl
import play.api.libs.json.OFormat
import play.api.mvc.BodyParser

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Helper class to control the creation of the rest action using either an asynchronous or a
 * synchronous function. Use either the `apply` function or the `async` function to create a
 * [[RestAction]] which can then handle requests.
 */
class RestActionBodyBuilder[
    RACType,
    AuthType,
    BodyType,
    ResourceKeyType,
    ResourceType,
    ResponseType](
    auth: HeaderAccessControl[AuthType],
    bodyParser: BodyParser[BodyType],
    errorHandler: PartialFunction[Throwable, RestError])(
    implicit keyFormat: KeyFormat[ResourceKeyType],
    resourceFormat: OFormat[ResourceType],
    ec: ExecutionContext,
    mat: Materializer) { self =>

  type CategoryEngine =
    RestActionCategoryEngine[RACType, ResourceKeyType, ResourceType, ResponseType]
  type BuiltAction =
    RestAction[RACType, AuthType, BodyType, ResourceKeyType, ResourceType, ResponseType]

  def apply(fn: RestContext[AuthType, BodyType] => RestResponse[ResponseType])(
      implicit category: CategoryEngine,
      fields: Fields[ResourceType],
      paginationConfiguration: PaginationConfiguration): BuiltAction = {

    async(ctx => Future.successful(fn(ctx)))
  }

  def apply(fn: => RestResponse[ResponseType])(
      implicit category: CategoryEngine,
      fields: Fields[ResourceType],
      paginationConfiguration: PaginationConfiguration): BuiltAction = {

    async(_ => Futures.immediate(fn))
  }

  def async(fn: => Future[RestResponse[ResponseType]])(
      implicit category: CategoryEngine,
      fields: Fields[ResourceType],
      paginationConfiguration: PaginationConfiguration): BuiltAction = {

    async(_ => fn)
  }

  def async(fn: RestContext[AuthType, BodyType] => Future[RestResponse[ResponseType]])(
      implicit category: CategoryEngine,
      fields: Fields[ResourceType],
      _paginationConfiguration: PaginationConfiguration): BuiltAction = {

    new RestAction[RACType, AuthType, BodyType, ResourceKeyType, ResourceType, ResponseType] {
      override def restAuth = auth
      override def restBodyParser = bodyParser
      override def restEngine = category
      override def fieldsEngine = fields
      override def paginationConfiguration = _paginationConfiguration
      override def errorHandler: PartialFunction[Throwable, RestError] =
        self.errorHandler
      override val keyFormat = self.keyFormat
      override val resourceFormat = self.resourceFormat
      override val executionContext = ec
      override val materializer = mat

      override def apply(
          context: RestContext[AuthType, BodyType]): Future[RestResponse[ResponseType]] =
        Futures.safelyCall(fn(context))
    }
  }
}
