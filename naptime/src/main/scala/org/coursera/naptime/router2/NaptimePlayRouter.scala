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
import javax.inject.Singleton

import com.google.inject.Injector
import com.linkedin.data.schema.DataSchema
import com.netflix.governator.annotations.WarmUp
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.ResourceKind
import org.coursera.naptime.schema
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.routing
import play.api.routing.Router.Routes

import scala.annotation.tailrec
import scala.collection.immutable

@Singleton
private[naptime] case class NaptimeRoutes @Inject()(
    injector: Injector,
    routerBuilders: immutable.Set[ResourceRouterBuilder]) {

  /**
   * Helper function to filter out those resource router builders that don't provide schemas.
   */
  def hasDefinedSchema(routerBuilder: ResourceRouterBuilder): Boolean = {
    try {
      routerBuilder.schema
      true
    } catch {
      case e: scala.NotImplementedError =>
        false
    }
  }

  def className(routerBuilder: ResourceRouterBuilder): String = {
    // Scala nests classes sometimes using a `$` instead of a `.`, but the JVM does not. :-)
    routerBuilder.resourceClass().getName.replace("$", ".")
  }

  lazy val schemaMap = routerBuilders
    .filter(hasDefinedSchema)
    .toList
    .map { routerBuilder =>
      className(routerBuilder) -> routerBuilder.schema
    }
    .toMap

  // TODO(saeta): Check to ensure there are not 2 resources with the same name / version.
  lazy val buildersToRouters = routerBuilders.map { routerBuilder =>
    val resourceClass = routerBuilder.resourceClass()
    routerBuilder -> routerBuilder.build(injector.getInstance(resourceClass))
  }.toMap

  lazy val routers = buildersToRouters.values.toList
}

/**
 * Handles routing for Naptime resources in an idiomatic fashion for Play projects.
 *
 * To use this router, include in your routes file something like:
 * {{{
 *   # Include Naptime resources
 *   ->    /api                  org.coursera.naptime.router2.PlayNaptimeRouter
 * }}}
 *
 * Requests matching the prefix for naptime resources will then be routed appropriately.
 *
 * @param naptimeRoutes Collects the common data structures useful for request routing, including the
 *                      router builders, as well as the routers themselves.
 * @param prefix The prefix path under which the resources should be served (in the example above:
 *               `/api`).
 */
@Singleton
case class NaptimePlayRouter(
    private[naptime] val naptimeRoutes: NaptimeRoutes,
    private[naptime] val prefix: String)
    extends play.api.routing.Router
    with StrictLogging {

  @Inject
  def this(naptimeRoutes: NaptimeRoutes) = this(naptimeRoutes, "")

  /**
   * Defer to [[handlerFor]] instead of the other way around for performance reasons.
   *
   * It is better to have the true implementation in `handlerFor` where we can route a request once
   * than to implement the partial function here, and have handlerFor call [[isDefinedAt]] and then
   * [[apply]], which would result in request routing and URL parsing occuring twice for a single
   * request when it wouldn't need to.
   */
  override lazy val routes: Routes = Function.unlift(handlerFor)

  override def withPrefix(prefix: String): routing.Router =
    copy(prefix = prefix)

  /**
   * Includes the Naptime resources into Play's dev mode not-found handler that lists all routes.
   */
  override lazy val documentation: Seq[(String, String, String)] = {
    def handlerKindToHttpMethod(kind: HandlerKind): String = {
      kind match {
        case HandlerKind.GET | HandlerKind.MULTI_GET | HandlerKind.GET_ALL | HandlerKind.FINDER =>
          "GET"
        case HandlerKind.CREATE | HandlerKind.ACTION =>
          "POST"
        case HandlerKind.UPSERT =>
          "PUT"
        case HandlerKind.DELETE =>
          "DELETE"
        case HandlerKind.PATCH =>
          "PATCH"
        case HandlerKind.$UNKNOWN =>
          kind.toString
      }
    }

    def shouldUsePathWithKey(kind: HandlerKind): Boolean = {
      kind match {
        case HandlerKind.GET | HandlerKind.UPSERT | HandlerKind.DELETE | HandlerKind.PATCH =>
          true
        case HandlerKind.CREATE | HandlerKind.MULTI_GET | HandlerKind.GET_ALL | HandlerKind.FINDER |
            HandlerKind.ACTION =>
          false
        case HandlerKind.$UNKNOWN =>
          false
      }
    }

    /**
     * Computes the URL path to access the resource.
     *
     * @param resourceSchema The resource to compute.
     * @return (PathWithoutKeys, PathWithKeySuffix)
     */
    def computeFullPath(resourceSchema: schema.Resource): (String, String) = {
      val hasNonRootParent = resourceSchema.parentClass.isDefined &&
        !resourceSchema.parentClass.contains("org.coursera.naptime.resources.RootResource")
      val previous = if (hasNonRootParent) {
        try {
          computeFullPath(naptimeRoutes.schemaMap(resourceSchema.parentClass.get))._2
        } catch {
          case e: NoSuchElementException =>
            logger.error(
              s"Problem computing schema for resource ${resourceSchema.className}. " +
                s"Parent class ${resourceSchema.parentClass} not found in schema map keys: " +
                s"${naptimeRoutes.schemaMap.keys}",
              e
            )
            prefix
        }
      } else {
        prefix
      }
      val resourceName = if (hasNonRootParent) {
        s"${resourceSchema.name}"
      } else {
        s"${resourceSchema.name}.v${resourceSchema.version.getOrElse("???")}"
      }
      val path = s"$previous/$resourceName"
      resourceSchema.kind match {
        case ResourceKind.SINGLETON =>
          path -> path
        case ResourceKind.COLLECTION =>
          path -> s"$path/$$id" // TODO: add key type information
        case ResourceKind.$UNKNOWN =>
          path -> "unknown" // this is definitely wrong, let me know what's the right one in code review. -- Zhaojun
      }
    }

    for {
      routerBuilder <- naptimeRoutes.routerBuilders.toList
      if naptimeRoutes.hasDefinedSchema(routerBuilder)
      (path, pathWithKey) = computeFullPath(routerBuilder.schema)
      handler <- routerBuilder.schema.handlers.sortBy(h => handlerOrder(h.kind))
    } yield {
      val method =
        s"${handlerKindToHttpMethod(handler.kind)} --- ${handler.kind}"
      val documentationPath = if (shouldUsePathWithKey(handler.kind)) {
        pathWithKey
      } else {
        if (handler.kind == HandlerKind.FINDER) {
          s"$path?q=${handler.name}"
        } else if (handler.kind == HandlerKind.ACTION) {
          s"$path?action=${handler.name}"
        } else {
          path
        }
      }
      val queryParamInfo = for {
        param <- handler.parameters
      } yield {
        val default = Option(param.data().get("default"))
        default
          .map { default =>
            s"${param.name}: ${param.`type`} = $default"
          }
          .getOrElse {
            s"${param.name}: ${param.`type`}"
          }
      }
      val baseMethod =
        s"[NAPTIME] ${routerBuilder.schema.className}.${handler.name}"
      val scalaMethod = if (queryParamInfo.isEmpty) {
        baseMethod
      } else {
        s"$baseMethod${queryParamInfo.mkString("(", ", ", ")")}"
      }
      (method, documentationPath, scalaMethod)
    }
  }

  private[this] val handlerOrder = Seq[HandlerKind](
    HandlerKind.GET,
    HandlerKind.MULTI_GET,
    HandlerKind.GET_ALL,
    HandlerKind.CREATE,
    HandlerKind.UPSERT,
    HandlerKind.DELETE,
    HandlerKind.PATCH,
    HandlerKind.FINDER,
    HandlerKind.ACTION
  ).zipWithIndex.toMap

  /**
   * Route the request to one of the naptime resources, invoking the (macro-generated) router.
   *
   * @param request The request to route.
   * @return If this is a naptime request for one of the routers, return the handler, otherwise None
   */
  override def handlerFor(request: RequestHeader): Option[Handler] = {
    if (request.path.startsWith(prefix)) {
      val path = request.path.substring(prefix.length)

      /**
       * Helper method to find the first resource that matches the request and route appropriately.
       *
       * @param resourceRouters The registered resource routers.
       * @param requestHeader The request to route. (It's a parameter to avoid closure allocation.)
       * @return Some(RouteAction) if the request corresponds to a registered resource, else None
       */
      @tailrec
      def routeRequestHelper(
          resourceRouters: immutable.Seq[ResourceRouter],
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

      routeRequestHelper(naptimeRoutes.routers, request, path)
    } else {
      None
    }
  }

  /**
   * Forces the initialization of the internals of the router.
   *
   * Note: in order to support Naptime's use without governator, everything must work without
   * relying on governator calling this function. If used without governator, everything must be
   * initialized upon object construction or on the first request.
   */
  @WarmUp
  private[this] def warmUp(): Unit = {
    naptimeRoutes.routers
    naptimeRoutes.schemaMap
    documentation
  }
}
