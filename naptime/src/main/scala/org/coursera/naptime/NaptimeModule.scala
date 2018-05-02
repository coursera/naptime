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

package org.coursera.naptime

import com.google.inject.AbstractModule
import com.google.inject.Binder
import com.linkedin.data.schema.DataSchema
import net.codingwell.scalaguice.ScalaMapBinder
import net.codingwell.scalaguice.ScalaModule
import net.codingwell.scalaguice.ScalaMultibinder
import org.coursera.naptime.resources.CollectionResource
import org.coursera.naptime.router2.ResourceRouterBuilder
import org.coursera.naptime.router2.Router

import scala.collection.immutable
import scala.reflect.macros.blackbox
import scala.language.experimental.macros
import scala.reflect.ClassTag

/*
 * Work around implementation limitation. See:
 * http://stackoverflow.com/questions/17557057/how-to-solve-implementation-restriction-trait-accesses-protected-method
 */
private[naptime] abstract class BinderExposer extends AbstractModule {
  protected[this] def exposedBinder() = binder()
}

/**
 * Inherit from this trait to bind resources and schema types.
 *
 * Naptime builds upon Guice to discover and find all resources in the service, as well
 * as to discover the reverse mappings from high-level class types to their wire
 * representations.
 */
trait NaptimeModule extends BinderExposer with ScalaModule {

  private[this] def resourceBinder =
    NaptimeModuleHelpers.multibinder(exposedBinder())
  private[this] def schemaTypesBinder =
    NaptimeModuleHelpers.schemaTypesBinder(exposedBinder())

  protected[this] def bindResourceRouterBuilder(
      resourceRouterBuilder: ResourceRouterBuilder): Unit = {
    resourceBinder.addBinding.toInstance(resourceRouterBuilder)
  }

  /**
   * Register a resource with Guice, and builds the router.
   *
   * Example:
   * {{{
   *   override def configure(): Unit = {
   *     bindResource[MyResource]
   *   }
   * }}}
   *
   * @tparam ResourceClass The resource to bind.
   */
  protected[this] def bindResource[ResourceClass <: CollectionResource[_, _, _]]: Unit =
    macro NaptimeModuleHelpers.bindResourceImpl[ResourceClass]

  /**
   * Set a binding from a high-level data type to the wire representation.
   *
   * Example:
   * {{{
   * override def configure(): Unit = {
   *   bindSchemaType[java.util.Date](
   *     DataSchemaUtil.dataSchemaTypeToPrimitiveDataSchema(DataSchema.Type.LONG))
   * }
   * }}}
   * @tparam Type The type to register.
   */
  protected[this] def bindSchemaType[Type](schema: DataSchema)(
      implicit ct: ClassTag[Type]): Unit = {
    schemaTypesBinder.addBinding(ct.runtimeClass.getName).toInstance(schema)
  }
}

private[naptime] object NaptimeModuleHelpers {

  /**
   * Constructs a ScalaMultibinder used for binding resources given a binder.
   *
   * Note: the binder is actually for the ResourceRouterBuilders and not for either the
   * ResourceRouters, nor for the Resources themselves.
   */
  private[naptime] def multibinder(binder: Binder) =
    ScalaMultibinder.newSetBinder[ResourceRouterBuilder](binder)

  private[naptime] def schemaTypesBinder(binder: Binder) =
    ScalaMapBinder.newMapBinder[String, DataSchema](binder)

  /**
   * Macro implementation for the [[NaptimeModule.bindResource]] function.
   */
  def bindResourceImpl[Resource <: CollectionResource[_, _, _]](c: blackbox.Context)(
      implicit wtt: c.WeakTypeTag[Resource]): c.Tree = {
    import c.universe._
    val routerObject = typeOf[Router.type]
    q"""bindResourceRouterBuilder($routerObject.build[$wtt])
       requireBinding(classOf[$wtt])"""
  }

}

/**
 * Install this singleton module into your service to configure Naptime defaults to ensure a
 * successful boot.
 *
 * {{{
 *   install(NaptimeModule)
 * }}}
 *
 * Note: as of writing this comment, there is no need to install this module in your service for
 * correct operation.
 */
object NaptimeModule extends ScalaModule {
  type SchemaTypeOverrides = immutable.Map[String, DataSchema]

  /**
   * Sets default bindings.
   */
  override def configure(): Unit = {
    // Register the multi-bindings in order to prevent guice errors if no resource is bound.
    NaptimeModuleHelpers.multibinder(binder())
    NaptimeModuleHelpers.schemaTypesBinder(binder())
  }
}
