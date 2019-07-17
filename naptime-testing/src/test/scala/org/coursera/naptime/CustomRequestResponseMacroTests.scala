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

import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.coursera.naptime.router2._
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class CustomRequest(request: String)

object CustomRequest {
  implicit val format: play.api.libs.json.OFormat[CustomRequest] =
    Json.format[CustomRequest]
}

case class CustomResponse(response: String)

object CustomResponse {
  implicit val format: play.api.libs.json.OFormat[CustomResponse] =
    Json.format[CustomResponse]
}

class AsymmetricResource(implicit val application: Application)
    extends TopLevelCollectionResource[String, Item] {

  override def keyFormat: KeyFormat[String] = KeyFormat.stringKeyFormat

  override implicit def resourceFormat: OFormat[Item] = Item.jsonFormat

  override def resourceName: String = "items"

  implicit val fields = Fields.withDefaultFields("name")

  // TODO amory: auth

  def update(id: String) =
    Nap
      .jsonBody[CustomRequest]
      .update { ctx =>
        ???
      }

  def create() =
    Nap
      .jsonBody[CustomRequest]
      .create { ctx =>
        ???
      }

  def action() =
    Nap
      .jsonBody[CustomRequest]
      .action[CustomResponse] { ctx =>
        ???
      }

  def delete(id: String) =
    Nap
      .jsonBody[CustomRequest]
      .delete { ctx =>
        ???
      }

}

object AsymmetricResource {
  val routerBuilder = Router.build[AsymmetricResource]
}

class CustomRequestResponseMacroTests
    extends AssertionsForJUnit
    with MockitoSugar
    with ImplicitTestApplication {

  val schema = AsymmetricResource.routerBuilder.schema

  @Test
  def updateTypes(): Unit = {
    val handler = schema.handlers.find(_.name === "update").get
    assert(handler.inputBodyType.contains("org.coursera.naptime.CustomRequest"))
    assert(handler.customOutputBodyType.isEmpty)
  }
  @Test
  def createTest(): Unit = {
    val handler = schema.handlers.find(_.name === "create").get
    assert(handler.inputBodyType.contains("org.coursera.naptime.CustomRequest"))
    assert(handler.customOutputBodyType.isEmpty)
  }

  @Test
  def actionTest(): Unit = {
    val handler = schema.handlers.find(_.name === "action").get
    assert(handler.inputBodyType.contains("org.coursera.naptime.CustomRequest"))
    assert(handler.customOutputBodyType.contains("org.coursera.naptime.CustomResponse"))
  }

  @Test
  def deleteTest(): Unit = {
    val handler = schema.handlers.find(_.name === "delete").get
    assert(handler.inputBodyType.contains("org.coursera.naptime.CustomRequest"))
    assert(handler.customOutputBodyType.isEmpty)
  }

}
