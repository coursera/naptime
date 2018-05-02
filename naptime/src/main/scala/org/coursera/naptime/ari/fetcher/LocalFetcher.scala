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
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.actions.RestAction
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.FetcherError
import org.coursera.naptime.ari.Request
import org.coursera.naptime.router2.NaptimeRoutes
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Headers

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Executes data requests against local Naptime resources (requires the use of Engine2 engines).
 *
 * @param naptimeRoutes The routing data structures required for handling requests.
 */
class LocalFetcher @Inject()(naptimeRoutes: NaptimeRoutes) extends FetcherApi with StrictLogging {

  private[this] val schemas = naptimeRoutes.routerBuilders.map(_.schema)
  private[this] val models =
    naptimeRoutes.routerBuilders.flatMap(_.types).map(_.tuple).toMap

  private[this] val routers = naptimeRoutes.buildersToRouters.map {
    case (builder, router) =>
      naptimeRoutes.className(builder) -> router
  }

  override def data(request: Request, isDebugMode: Boolean)(
      implicit executionContext: ExecutionContext): Future[FetcherResponse] = {
    val resourceSchemaOpt = schemas.find { resourceSchema =>
      // TODO: Handle nested resources.
      resourceSchema.name == request.resource.topLevelName &&
      resourceSchema.version.contains(request.resource.version)
    }
    val queryString =
      request.arguments.toMap.mapValues(arg => List(stringifyArg(arg)))
    val url = s"/api/${request.resource.identifier}?" +
      queryString
        .map { case (key, value) => key + "=" + value.mkString(",") }
        .mkString("&")
    (for {
      resourceSchema <- resourceSchemaOpt
      router <- routers.get(resourceSchema.className)

      path = s"/${request.resource.identifier}"
      fakePlayRequestTarget = request.requestHeader.target
        .withUriString(request.resource.identifier)
        .withQueryString(queryString)
      // We need to make a Headers object that does not have have Content-Type or Content-Length,
      // because Content-Type and Content-Length headers cause the handler to attempt to parse the body and fail.
      // The request.requestHeader.headers is of type play.core.server.akkahttp.AkkaHeadersWrapper
      // which always adds back the Content-Type and Content-Length of the original request
      // so we have to make our own clean play.api.mvc.Headers headers.
      fakePlayRequestHeaders = Headers(request.requestHeader.headers.headers: _*)
        .remove("Content-Type", "Content-Length")
      fakePlayRequest = request.requestHeader
        .withMethod("GET")
        .withTarget(fakePlayRequestTarget)
        .withHeaders(fakePlayRequestHeaders)
        .withBody(())
      // TODO: handle header filtering more properly
      handler <- router.routeRequest(path, fakePlayRequest)
    } yield {
      logger.info(
        s"Making local request to ${request.resource.identifier} / ${fakePlayRequest.queryString}")
      val taggedRequest = handler.tagRequest(fakePlayRequest)
      handler match {
        case naptimeAction: RestAction[_, _, _, _, _, _] =>
          naptimeAction
            .localRun(fakePlayRequest, request.resource)
            .map(response => Right(response.copy(url = Some(url))))
            .recoverWith {
              case actionException: NaptimeActionException =>
                Future.successful(
                  Left(FetcherError(actionException.httpCode, actionException.toString, Some(url))))
              case e: Throwable => throw e
            }
        case _ =>
          val msg = "Handler was not a RestAction, or Get attempted"
          logger.error(msg)
          Future.successful(Left(FetcherError(404, msg, Some(url))))
      }
    }).getOrElse {
      val msg = s"Unknown resource: ${request.resource}"
      logger.warn(msg)
      Future.successful(Left(FetcherError(404, msg, Some(url))))
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
