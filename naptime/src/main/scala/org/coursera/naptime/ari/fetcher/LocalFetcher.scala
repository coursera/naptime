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

package org.coursera.naptime.ari.fetcher

import javax.inject.Inject

import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.actions.RestAction
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.schema.Resource
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import scala.concurrent.Future

/**
 * Executes data requests against local Naptime resources (requires the use of Engine2 engines).
 *
 * @param naptimeRoutes The routing data structures required for handling requests.
 */
class LocalFetcher @Inject() (
    naptimeRoutes: NaptimeRoutes)
  extends FetcherApi with StrictLogging {

  private[this] val schemas = naptimeRoutes.routerBuilders.map(_.schema)
  private[this] val models = naptimeRoutes.routerBuilders.flatMap(_.types).map(_.tuple).toMap

  private[this] val routers = naptimeRoutes.buildersToRouters.map { case (builder, router) =>
    naptimeRoutes.className(builder) -> router
  }

  override def data(request: Request): Future[Response] = {
    if (request.topLevelRequests.length != 1) {
      val msg = s"Too many top level requests passed to LocalFetcher: $request"
      logger.error(msg)
      Future.failed(new IllegalArgumentException(msg))
    } else if (request.topLevelRequests.exists(_.selection.name == "get")) {
      val msg = s"Gets are not supported in the LocalFetcher: $request"
      logger.error(msg)
      Future.failed(new UnsupportedOperationException(msg))
    } else {
      val topLevelRequest = request.topLevelRequests.head
      val resourceSchemaOpt = schemas.find { resourceSchema =>
        // TODO: Handle nested resources.
        resourceSchema.name == topLevelRequest.resource.topLevelName &&
          resourceSchema.version.contains(topLevelRequest.resource.version)
      }
      (for {
        resourceSchema <- resourceSchemaOpt
        router <- routers.get(resourceSchema.className)
        argMap = topLevelRequest.selection.args.toMap
        queryString = argMap.mapValues(arg => List(stringifyArg(arg)))
        path = s"/${topLevelRequest.resource.identifier}"
        fakePlayRequest = request.requestHeader.copy(
          method = "GET", // TODO: handle non-read-only request types.
          uri = topLevelRequest.resource.identifier, // Warning: uri is not consistent with queryString
          queryString = queryString,
          headers = request.requestHeader.headers.remove("content-type")) // TODO: handle header filtering more properly
        handler <- router.routeRequest(path, fakePlayRequest)
      } yield {
        val taggedRequest = handler.tagRequest(fakePlayRequest)
        handler match {
          case naptimeAction: RestAction[_, _, _, _, _, _] =>
            naptimeAction.localRun(fakePlayRequest,
              ResourceName(resourceSchema.name, resourceSchema.version.map(_.toInt).getOrElse(0)),
              topLevelRequest)
          case _ =>
            val msg = "Handler was not a RestAction"
            logger.error(msg)
            Future.failed(new IllegalArgumentException(msg))
        }
      }).getOrElse {
        val msg = s"Unknown resource: ${topLevelRequest.resource}"
        logger.warn(msg)
        Future.failed(new IllegalArgumentException(msg))
      }
    }
  }

  private[this] def stringifyArg(value: JsValue): String = {
    value match {
      case JsArray(arrayElements) =>
        arrayElements.map(stringifyArg).mkString(",")
      case stringValue: JsString =>
        stringValue.as[String]
      case number: JsNumber =>
        number.toString
      case boolean: JsBoolean =>
        boolean.toString
      case jsObject: JsObject =>
        Json.stringify(jsObject)
      case JsNull =>
        ""
    }
  }
}
