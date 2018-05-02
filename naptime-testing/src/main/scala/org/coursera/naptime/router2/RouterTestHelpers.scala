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

import org.coursera.naptime.resources.CollectionResource
import org.mockito.Mockito.when

trait RouterTestHelpers {

  def setupParentMockCalls[T <: CollectionResource[_, _, _]](mock: T, instanceImpl: T): Unit = {
    when(mock.resourceName).thenReturn(instanceImpl.resourceName)
    when(mock.resourceVersion).thenReturn(instanceImpl.resourceVersion)
    when(mock.pathParser)
      .thenReturn(instanceImpl.pathParser.asInstanceOf[mock.PathParser])
  }

  def assertRouted(resource: CollectionResource[_, _, _], urlFragment: String): Unit = {
    assert(
      !resource.optParse(urlFragment).isEmpty,
      s"Resource: ${resource.getClass.getName} did not accept url fragment: $urlFragment")
  }

  def assertNotRouted(resource: CollectionResource[_, _, _], urlFragment: String): Unit = {
    assert(
      resource.optParse(urlFragment).isEmpty,
      s"Resource: ${resource.getClass.getName} did accept url fragment: $urlFragment")
  }
}
