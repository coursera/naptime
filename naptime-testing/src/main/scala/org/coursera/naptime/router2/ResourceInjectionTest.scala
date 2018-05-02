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

import javax.naming.ConfigurationException

import com.google.inject.Injector
import com.google.inject.ProvisionException
import com.typesafe.scalalogging.StrictLogging
import org.junit.Test

trait ResourceInjectionTest extends StrictLogging {
  def injector: Injector

  @Test
  def routerInjection(): Unit = {
    injector.getProvider(classOf[NaptimePlayRouter])
  }

  @Test
  def resourceInjection(): Unit = {
    val naptimePlayRouter = try {
      Some(injector.getInstance(classOf[NaptimePlayRouter]))
    } catch {
      case e: ConfigurationException =>
        logger.warn(s"No instance of 'NaptimePlayRouter' bound. Skipping router2 tests.", e)
        None
      case e: ProvisionException =>
        logger.error("Encountered an exception provisioning 'NaptimePlayRouter'.", e)
        None
    }

    for {
      router <- naptimePlayRouter
      resource <- router.naptimeRoutes.routerBuilders
    } {
      injector.getProvider(resource.resourceClass())
      logger.debug(s"Resource ${resource.resourceClass().getName} is injectable.")
    }
  }
}
