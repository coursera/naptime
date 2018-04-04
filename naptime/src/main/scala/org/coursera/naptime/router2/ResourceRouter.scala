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

import com.linkedin.data.schema.DataSchema
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.schema.Resource
import play.api.mvc.RequestHeader

import scala.collection.immutable

/**
 * Use me to build a router that can be used to route requests.
 */
trait ResourceRouterBuilder {

  /**
   * The resource class this router handles.
   */
  type ResourceClass

  /**
   * The class of the resource. (Used to request an instance from the DI framework.)
   *
   * @return The class of the resource class.
   */
  def resourceClass(): Class[ResourceClass]

  def build(resourceInstance: ResourceClass): ResourceRouter

  /**
   * Defines the resource's schema.
   */
  def schema: Resource

  def types: immutable.Seq[Keyed[String, DataSchema]]

  // TODO(saeta): Include the collection of newly defined types (e.g. asymmetric types)

  /** Internal method used to construct broken instances of the resource to call methods upon it.
   *
   * @return An instance of [[ResourceClass]] that has _NOT_ had its constructor called.
   */
  protected[this] lazy val stubInstance: ResourceClass = {
    import sun.reflect.ReflectionFactory

    val rf = ReflectionFactory.getReflectionFactory
    val objConstructor = classOf[AnyRef].getDeclaredConstructor()
    val instanceStubConstructor =
      rf.newConstructorForSerialization(resourceClass(), objConstructor)
    resourceClass().cast(instanceStubConstructor.newInstance())
  }
}

/**
 * The core interface for the router. Implement this interface in order to be able to route your
 * requests.
 *
 * Note: typically, a macro implements these functions for individual resources by subclassing one
 * of the types below.
 */
trait ResourceRouter {

  /**
   * The resource class this router handles.
   */
  type ResourceClass

  /**
   * If the request is for this resource, return a RouteAction. For alternate resources, return None
   *
   * Note: by returning `Some[RouteAction]`, that does not mean that there is a valid finder,
   * action, or that all query parameters were parsed correctly.
   *
   * @param path The (modified) request path to use for routing request.
   * @param requestHeader The request to be handled (for routing & query parameter parsing)
   * @return Either the Play! action to handle the request, or None indicating this request is for a
   *         different resource.
   */
  def routeRequest(path: String, requestHeader: RequestHeader): Option[RouteAction]

  // TODO(saeta): prevent people outside of naptime from calling non-test fns (similar to Await)
  // TODO(saeta): add ability to test routeRequest & associated functions.
}
