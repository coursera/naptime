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

package org.coursera.naptime

import org.coursera.naptime.access.HeaderAccessControl
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.coursera.naptime.router2._
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.libs.json.OFormat
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext

case class CustomAuth()

object CustomAuthorizer extends HeaderAccessControl[CustomAuth] {
  override def run(requestHeader: RequestHeader)(implicit executionContext: ExecutionContext) = ???
  override private[naptime] def check(authInfo: CustomAuth) = ???
}

class AuthorizedResource(implicit val application: Application)
    extends TopLevelCollectionResource[String, Item] {

  override def keyFormat: KeyFormat[String] = KeyFormat.stringKeyFormat

  override implicit def resourceFormat: OFormat[Item] = Item.jsonFormat

  override def resourceName: String = "items"

  implicit val fields = Fields.withDefaultFields("name")

  def get(id: String) =
    Nap
      .auth(CustomAuthorizer)
      .get { ctx =>
        ???
      }

}

object AuthorizedResource {
  val routerBuilder = Router.build[AuthorizedResource]
}

class AuthMacroTest extends AssertionsForJUnit with MockitoSugar with ResourceTestImplicits {

  val schema = AuthorizedResource.routerBuilder.schema

  @Test
  def get(): Unit = {
    val handler = schema.handlers.find(_.name === "get").get
    assert(handler.authType === Some("org.coursera.naptime.CustomAuth"))
  }

}
