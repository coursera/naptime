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
import com.typesafe.scalalogging.StrictLogging
import org.coursera.common.stringkey.StringKey
import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.naptime.path.ParseFailure
import org.coursera.naptime.path.ParseSuccess
import org.coursera.naptime.resources.CollectionResource
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.EssentialAction
import play.api.mvc.RequestHeader
import play.api.mvc.RequestTaggingHandler
import play.api.mvc.Result
import play.api.mvc.Results

import scala.concurrent.Future
import scala.language.existentials

class NestingCollectionResourceRouter[CollectionResourceType <: CollectionResource[_, _, _]](
    val resourceInstance: CollectionResourceType)
    extends ResourceRouter
    with StrictLogging {

  override type ResourceClass = CollectionResourceType

  /**
   * Helper method to convert a path key to the ancestor keys.
   *
   * @param pathKey The path key to convert.
   */
  protected[this] def pathToAncestor(
      pathKey: resourceInstance.PathKey): resourceInstance.AncestorKeys = {
    pathKey.tail
  }

  /**
   * Helper method to convert an opt path key to the ancestor keys.
   *
   * @param pathKey The opt path key to convert.
   * @return
   */
  protected[this] def optPathToAncestor(
      pathKey: resourceInstance.OptPathKey): resourceInstance.AncestorKeys = {
    pathKey.tail
  }

  /**
   * Constructs a Map used to tag the request.
   *
   * Note: because of a limitation of the mocking framework, this code gracefully handles when
   * [[resourceInstance.getClass]] returns null.
   *
   * @param methodName The name of the scala method invoked to handle this request.
   * @return
   */
  protected[this] def mkRequestTags(methodName: String): Map[String, String] = {
    Map(
      Router.NAPTIME_RESOURCE_NAME ->
        Option(resourceInstance.getClass).map(_.getName).getOrElse("nullClass"),
      Router.NAPTIME_METHOD_NAME -> methodName)
  }

  override def routeRequest(path: String, requestHeader: RequestHeader): Option[RouteAction] = {
    resourceInstance.optParse(path) match {
      case ParseFailure | ParseSuccess(Some(_), _) =>
        None // This request is not for us.
      case ParseSuccess(None, pathKeyOpt) =>
        // If the head of the list is defined, convert to the PathKey, else remain as an OptPathKey
        // Note: we centralize here the casting required to get the compiler to believe us.
        val pathKey: Either[resourceInstance.OptPathKey, resourceInstance.PathKey] =
          if (pathKeyOpt.head.isDefined) {
            Right(
              (pathKeyOpt.head.get ::: pathKeyOpt.tail)
                .asInstanceOf[resourceInstance.PathKey])
          } else {
            Left(pathKeyOpt)
          }
        Some(buildHandler(requestHeader, pathKey))
      case null => // Test mocking error.
        logger.error(s"Match error routing request $requestHeader with resource $resourceInstance")
        throw new MatchError(null)
    }
  }

  private[this] def buildHandler(
      requestHeader: RequestHeader,
      pathKey: Either[resourceInstance.OptPathKey, resourceInstance.PathKey]): RouteAction = {
    requestHeader.method match {
      case "GET"           => buildGetHandler(requestHeader, pathKey)
      case "POST"          => buildPostHandler(requestHeader, pathKey)
      case "PUT"           => buildPutHandler(requestHeader, pathKey)
      case "DELETE"        => buildDeleteHandler(requestHeader, pathKey)
      case "PATCH"         => buildPatchHandler(requestHeader, pathKey)
      case unknown: String => errorRoute(s"Unknown HTTP method '$unknown'.")
    }
  }

  private[this] def buildGetHandler(
      requestHeader: RequestHeader,
      pathKey: Either[resourceInstance.OptPathKey, resourceInstance.PathKey]): RouteAction = {
    if (pathKey.isRight) {
      executeGet(requestHeader, pathKey.right.get)
    } else {
      val optPathKey = pathKey.left.get
      requestHeader.queryString
        .get("q")
        .map { queryStr =>
          if (queryStr.isEmpty) {
            errorRoute("Must provide a finder name.")
          } else if (queryStr.length != 1) {
            errorRoute("Must provide only one finder name.")
          } else {
            executeFinder(requestHeader, optPathKey, queryStr.head)
          }
        }
        .getOrElse {
          requestHeader.queryString
            .get("ids")
            .map { queryStr =>
              if (queryStr.isEmpty) {
                errorRoute("Must provide an 'ids' query parameter.")
              } else if (queryStr.length != 1) {
                errorRoute("Must provide only one 'ids' query parameter.")
              } else {
                val idsOrError = parseIds[resourceInstance.KeyType](
                  queryStr.head,
                  resourceInstance.keyFormat.stringKeyFormat)
                idsOrError.right.map { ids =>
                  // Note: we have to cast to get the Scala compiler to believe us, even though
                  // Intellij sees this cast as redundant.
                  executeMultiGet(
                    requestHeader,
                    optPathKey,
                    ids.asInstanceOf[Set[resourceInstance.KeyType]])
                }.merge
              }
            }
            .getOrElse {
              executeGetAll(requestHeader, optPathKey)
            }
        }
    }
  }

  private[this] def buildPostHandler(
      requestHeader: RequestHeader,
      pathKey: Either[resourceInstance.OptPathKey, resourceInstance.PathKey]): RouteAction = {
    if (pathKey.isLeft) {
      requestHeader.queryString
        .get("action")
        .map { queryStr =>
          if (queryStr.isEmpty) {
            errorRoute("Must provide an action name.")
          } else if (queryStr.length == 1) {
            executeAction(requestHeader, pathKey.left.get, queryStr.head)
          } else {
            errorRoute("Must provide only one action name.")
          }
        }
        .getOrElse {
          executeCreate(requestHeader, pathKey.left.get)
        }
    } else {
      errorRoute("Post only to the collection resource, not individual elements.")
    }
  }

  private[this] def buildPutHandler(
      requestHeader: RequestHeader,
      pathKey: Either[resourceInstance.OptPathKey, resourceInstance.PathKey]): RouteAction = {
    pathKey.right.toOption
      .map { pathKey =>
        executePut(requestHeader, pathKey)
      }
      .getOrElse {
        idRequired
      }
  }

  private[this] def buildDeleteHandler(
      requestHeader: RequestHeader,
      pathKey: Either[resourceInstance.OptPathKey, resourceInstance.PathKey]): RouteAction = {
    pathKey.right.toOption
      .map { pathKey =>
        executeDelete(requestHeader, pathKey)
      }
      .getOrElse {
        idRequired
      }
  }

  private[this] def buildPatchHandler(
      requestHeader: RequestHeader,
      pathKey: Either[resourceInstance.OptPathKey, resourceInstance.PathKey]): RouteAction = {
    pathKey.right.toOption
      .map { pathKey =>
        executePatch(requestHeader, pathKey)
      }
      .getOrElse {
        idRequired
      }
  }

  protected[this] def executeGet(
      requestHeader: RequestHeader,
      pathKey: resourceInstance.PathKey): RouteAction = {
    errorRoute("'get' not implemented")
  }

  protected[this] def executeMultiGet(
      requestHeader: RequestHeader,
      optPathKey: resourceInstance.OptPathKey,
      ids: Set[resourceInstance.KeyType]): RouteAction = {
    errorRoute("'multi-get' not implemented")
  }

  protected[this] def executeGetAll(
      requestHeader: RequestHeader,
      optPathKey: resourceInstance.OptPathKey): RouteAction = {
    errorRoute("'get-all' not implemented")
  }

  protected[this] def executeFinder(
      requestHeader: RequestHeader,
      optPathKey: resourceInstance.OptPathKey,
      finderName: String): RouteAction = {
    // TODO(saeta): watch out for injection attacks!
    errorRoute(s"finder '$finderName' not implemented")
  }

  protected[this] def executeCreate(
      requestHeader: RequestHeader,
      optPathKey: resourceInstance.OptPathKey): RouteAction = {
    errorRoute("'create' not implemented")
  }

  protected[this] def executePut(
      requestHeader: RequestHeader,
      pathKey: resourceInstance.PathKey): RouteAction = {
    errorRoute("'put' not implemented")
  }

  protected[this] def executeDelete(
      requestHeader: RequestHeader,
      pathKey: resourceInstance.PathKey): RouteAction = {
    errorRoute("'delete' not implemented")
  }

  protected[this] def executePatch(
      requestHeader: RequestHeader,
      pathKey: resourceInstance.PathKey): RouteAction = {
    errorRoute("'patch' not implemented")
  }

  protected[this] def executeAction(
      requestHeader: RequestHeader,
      optPathKey: resourceInstance.OptPathKey,
      actionName: String): RouteAction = {
    // TODO(saeta): watch out for injection attacks!
    errorRoute(s"action '$actionName' not implemented")
  }

  // TODO(saeta): Support populating the Allow header for more useful error responses.
  protected[this] def errorRoute(
      msg: String,
      statusCode: Int = Status.METHOD_NOT_ALLOWED): RouteAction =
    NestingCollectionResourceRouter.errorRoute(resourceInstance.getClass, msg, statusCode)

  /**
   * Helper function to parse ids.
   *
   * It is not private[this] for testing purposes.
   *
   * @param queryString The query string to parse into ids.
   * @param parser The string key format for the key type [[T]]
   * @tparam T The type of keys we are parsing. Note: we use a type parameter to help the scala
   *           compiler correctly infer the types.
   * @return either a Left(error) or a Right(Set(ids))
   */
  private[naptime] def parseIds[T](
      queryString: String,
      parser: StringKeyFormat[T]): Either[RouteAction, Set[T]] = {
    var error: Option[RouteAction] = None
    // TODO(saeta): check length of idStrings to make sure it's not too long. (Potential DoS.)
    val idStrings = queryString.split("(?<!\\\\),")
    val ids = idStrings.flatMap { idStr =>
      val parsed = parser.reads(StringKey(idStr))
      if (parsed.isEmpty) {
        error = Some(errorRoute(s"Could not parse key '$idStr'")) // TODO: truncate if too long.
      }
      parsed
    }.toSet
    error.toLeft(ids)
  }

  private[this] val idRequired = errorRoute("Requires ID in path as a path parameter.")
}

object NestingCollectionResourceRouter {
  private[naptime] def errorRoute(
      resourceClass: Class[_],
      msg: String,
      statusCode: Int = Status.BAD_REQUEST): RouteAction = {

    new EssentialAction with RequestTaggingHandler {
      override def apply(request: RequestHeader): Accumulator[ByteString, Result] = {
        Accumulator(Sink.ignore.mapMaterializedValue { _ =>
          // TODO(saeta): use standardized error response format.
          Future.successful(Results.Status(statusCode)(Json.obj("msg" -> s"Routing error: $msg")))
        })
      }

      override def tagRequest(request: RequestHeader): RequestHeader =
        request.copy(tags = request.tags + (Router.NAPTIME_RESOURCE_NAME -> resourceClass.getName))
    }
  }
}
