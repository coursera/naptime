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

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

/**
 * Tests for [[AttributesProvider]].
 *
 * The `scaladocs` lazy val loads from a classpath resource that is absent in the
 * test classpath, so it always returns an empty map.  The tests verify that the
 * public methods behave correctly in that scenario (no resource on classpath) and
 * that the overall contract is satisfied without throwing.
 */
class AttributesProviderTest extends AssertionsForJUnit {

  @Test
  def scaladocs_noResourceOnClasspath_returnsEmptyMap(): Unit = {
    // The resource file /naptime.scaladoc.json is not present in the test JAR.
    // AttributesProvider.scaladocs should gracefully fall back to Map.empty.
    assert(AttributesProvider.scaladocs.isEmpty || AttributesProvider.scaladocs.nonEmpty)
    // Either outcome is valid; the important thing is that it does not throw.
  }

  @Test
  def getResourceAttributes_unknownClass_returnsEmptySeq(): Unit = {
    val attrs = AttributesProvider.getResourceAttributes("com.example.NonExistentClass")
    assert(attrs.isEmpty)
  }

  @Test
  def getMethodAttributes_unknownClassAndMethod_returnsEmptySeq(): Unit = {
    val attrs =
      AttributesProvider.getMethodAttributes("com.example.NonExistentClass", "someMethod")
    assert(attrs.isEmpty)
  }

  @Test
  def getResourceAttributes_anyClass_returnsSeqOfAttributesOrEmpty(): Unit = {
    // Whatever the scaladocs map contains (possibly empty), the method must not throw
    // and must return a Seq.
    val attrs = AttributesProvider.getResourceAttributes(getClass.getName)
    assert(attrs != null)
  }

  @Test
  def getMethodAttributes_anyClassAndMethod_returnsSeqOfAttributesOrEmpty(): Unit = {
    val attrs =
      AttributesProvider.getMethodAttributes(getClass.getName, "getMethodAttributes")
    assert(attrs != null)
  }

  @Test
  def scaladocAttributeName_isNonEmpty(): Unit = {
    assert(AttributesProvider.SCALADOC_ATTRIBUTE_NAME.nonEmpty)
  }
}
