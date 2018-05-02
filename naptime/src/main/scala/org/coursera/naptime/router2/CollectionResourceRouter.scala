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

package org.coursera.naptime.router2

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import org.coursera.common.stringkey.StringKey
import org.coursera.common.stringkey.StringKeyFormat
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.EssentialAction
import play.api.mvc.RequestHeader
import play.api.mvc.RequestTaggingHandler
import play.api.mvc.Result
import play.api.mvc.Results
import play.api.mvc.request.RequestAttrKey

import scala.concurrent.Future
import scala.language.existentials

object CollectionResourceRouter {
  private[naptime] def errorRoute(msg: String, resourceClass: Class[_]): RouteAction =
    new EssentialAction with RequestTaggingHandler {
      override def apply(request: RequestHeader): Accumulator[ByteString, Result] = {
        Accumulator(Sink.ignore.mapMaterializedValue { _ =>
          // TODO(saeta): use standardized error response format.
          Future.successful(Results.BadRequest(Json.obj("msg" -> s"Routing error: $msg")))
        })
      }

      override def tagRequest(request: RequestHeader): RequestHeader =
        request.addAttr(
          RequestAttrKey.Tags,
          Map(Router.NAPTIME_RESOURCE_NAME -> resourceClass.getName))
    }

  case class StrictQueryParser[T](
      paramName: String,
      stringKeyFormat: StringKeyFormat[T],
      resourceClass: Class[_]) {

    def evaluate(rh: RequestHeader): Either[RouteAction, T] = {
      val queryStringResults = rh.queryString.get(paramName)
      if (queryStringResults.isEmpty || queryStringResults.get.isEmpty) {
        Left(errorRoute(s"Missing required parameter '$paramName'", resourceClass))
      } else if (queryStringResults.get.tail.isEmpty) {
        // Hack around broken iOS clients for namespace parameter. TODO(saeta): Remove on Feb 12
        val stringValue =
          if (paramName == "namespaces" &&
              queryStringResults.get.head.startsWith(",")) {
            queryStringResults.get.head.substring(1)
          } else {
            queryStringResults.get.head
          }
        val parsed = stringKeyFormat.reads(StringKey(stringValue))
        parsed
          .map { parsed =>
            Right(parsed)
          }
          .getOrElse {
            Left(
              errorRoute(s"Improperly formatted value for parameter '$paramName'", resourceClass))
          }
      } else {
        Left(errorRoute(s"Too many query parameters for '$paramName", resourceClass))
      }
    }
  }

  case class OptionalQueryParser[T](
      paramName: String,
      stringKeyFormat: StringKeyFormat[T],
      resourceClass: Class[_]) {
    def evaluate(rh: RequestHeader): Either[RouteAction, Option[T]] = {
      val queryStringResults = rh.queryString.get(paramName)
      if (queryStringResults.isEmpty || queryStringResults.get.isEmpty) {
        Right(None)
      } else if (queryStringResults.get.tail.isEmpty) {
        val stringValue = queryStringResults.get.head
        val parsed = stringKeyFormat.reads(StringKey(stringValue))
        parsed
          .map { parsed =>
            Right(Some(parsed))
          }
          .getOrElse {
            Left(
              errorRoute(s"Improperly formatted value for parameter '$paramName'", resourceClass))
          }
      } else {
        Left(errorRoute(s"Too many query parameters for '$paramName", resourceClass))
      }
    }
  }

  case class BooleanFlagParser(paramName: String, resourceClass: Class[_]) {
    def evaluate(rh: RequestHeader): Either[RouteAction, Boolean] = {
      val queryStringResults = rh.queryString.get(paramName)
      if (queryStringResults.isEmpty || queryStringResults.get.isEmpty) {
        Left(errorRoute(s"Missing required parameter '$paramName'", resourceClass))
      } else if (queryStringResults.get.tail.isEmpty) {
        val stringValue = queryStringResults.get.head
        stringValue match {
          case "true"  => Right(true)
          case "false" => Right(false)
          case unknown: String =>
            Left(
              errorRoute(
                s"Improperly formatted value for parameter '$paramName'." +
                  s" Expected 'true' or 'false' but found '$unknown'.",
                resourceClass))
        }
      } else {
        Left(errorRoute(s"Too many query parameters for '$paramName", resourceClass))
      }
    }
  }

  case class OptionBooleanFlagParser(paramName: String, resourceClass: Class[_]) {
    def evaluate(rh: RequestHeader): Either[RouteAction, Option[Boolean]] = {
      val queryStringResults = rh.queryString.get(paramName)
      if (queryStringResults.isEmpty || queryStringResults.get.isEmpty) {
        Right(None)
      } else if (queryStringResults.get.tail.isEmpty) {
        val stringValue = queryStringResults.get.head
        stringValue match {
          case "true"  => Right(Some(true))
          case "false" => Right(Some(false))
          case unknown: String =>
            Left(
              errorRoute(
                s"Improperly formatted value for parameter '$paramName'. " +
                  s"Expected 'true' or 'false' but found '$unknown'.",
                resourceClass))
        }
      } else {
        Left(errorRoute(s"Too many query parameters for '$paramName", resourceClass))
      }
    }
  }
  // TODO: build tolerant parser that doesn't error out the request if it can't parse
  // TODO: add a sequence parser that parses multiple copies of a flag.
}
