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

import javax.inject.Inject

import com.google.inject.Injector
import org.coursera.naptime.resources.CollectionResource
import play.api.mvc.RequestHeader

import scala.annotation.tailrec
import scala.collection.immutable
import language.experimental.macros

object Router {
  def build[T <: CollectionResource[_, _, _]]: ResourceRouterBuilder =
    macro MacroImpls.build[T]

  /**
   * Key for the [[RequestHeader.tags]] map filled in with the resource's class name
   */
  val NAPTIME_RESOURCE_NAME = "NAPTIME_RESOURCE_NAME"

  /**
   * Key for the [[RequestHeader.tags]] map filled in with the name of the invoked method.
   */
  val NAPTIME_METHOD_NAME = "NAPTIME_METHOD_NAME"
}

/**
 * The macro-based router for Naptime. This should be instantiated in the Play! application's Global
 * and hooked into the Global's `onRouteRequest` method.
 *
 * @param injector The Guice injector to provide the resource instances.
 * @param resourceRouterBuilders Builders that create ResourceRouters that will handle all routing.
 */
class Router @Inject()(
    injector: Injector,
    resourceRouterBuilders: immutable.Seq[ResourceRouterBuilder]) {
  // Build the resource routers.
  private[this] val resourceRouters = resourceRouterBuilders.map { builder =>
    val resourceClass = builder.resourceClass()
    val instance = injector.getInstance(resourceClass)
    builder.build(instance)
  }

  // TODO(saeta): consider adding a WarmUp method to ensure each resource has been warmed up.

  // TODO(saeta): add check to ensure there are not 2 resources with the same name & version.

  // TODO(saeta): augment the RouteActions with the ability to accept the related resources for
  // automatic resource inclusion.

  // TODO(saeta): add timing information to Router initialization + add logging.

  /**
   * Returns the handler to handle the request.
   *
   * Note: instead of returning a generic Handler, we enforce returning a standard `EssentialAction`
   * with `RequestTaggingHandler`.
   *
   * @param requestHeader The request to be routed and handled.
   * @return The naptime action with tagging capabilities.
   */
  def onRouteRequest(requestHeader: RequestHeader): Option[RouteAction] = {
    if (requestHeader.path.startsWith("/api")) {
      val path = requestHeader.path.substring("/api".length)

      /**
       * Helper function to find the first resource that matches the request and route appropriately
       *
       * @param resourceRouters The registered resource routers.
       * @param requestHeader The request to route.
       * @return Some(RouteAction) if the request corresponds to a registered resource, else None
       */
      @tailrec
      def routeRequestHelper(
          resourceRouters: Seq[ResourceRouter],
          requestHeader: RequestHeader,
          path: String): Option[RouteAction] = {
        if (resourceRouters.isEmpty) {
          None
        } else {
          val first = resourceRouters.head
          val firstResult = first.routeRequest(path, requestHeader)
          if (firstResult.isDefined) {
            firstResult
          } else {
            routeRequestHelper(resourceRouters.tail, requestHeader, path)
          }
        }
      }

      routeRequestHelper(resourceRouters, requestHeader, path)
    } else {
      None
    }
  }

  // TODO(saeta): add additional functionality (i.e. listing all resources / metadata, etc.)

  // TODO(saeta): performance test the new router implementation & do reasonable optimizations.
}
