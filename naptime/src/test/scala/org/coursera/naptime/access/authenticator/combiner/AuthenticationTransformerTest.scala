/*
 * Copyright 2024 Coursera Inc.
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

package org.coursera.naptime.access.authenticator.combiner

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

/**
 * Tests for [[AuthenticationTransformer]].
 *
 * The `function` factory method was rewritten for Scala 2.13 compatibility:
 * `PartialFunction(f)` (removed in 2.13) → explicit `new PartialFunction[I, O]`
 * anonymous class.  These tests verify the replacement behaves identically.
 */
class AuthenticationTransformerTest extends AssertionsForJUnit {

  // ─── AuthenticationTransformer.apply ─────────────────────────────────────────

  @Test
  def apply_withMatchingInput_callsPartialFunction(): Unit = {
    val transformer = AuthenticationTransformer[Option[Int], Int] { case Some(n) => n * 2 }
    val pf = transformer.partial
    assert(pf.isDefinedAt(Some(5)))
    assertResult(10)(pf(Some(5)))
  }

  @Test
  def apply_withNonMatchingInput_isNotDefined(): Unit = {
    val transformer = AuthenticationTransformer[Option[Int], Int] { case Some(n) => n }
    assert(!transformer.partial.isDefinedAt(None))
  }

  // ─── AuthenticationTransformer.function ──────────────────────────────────────
  // This is the code path that was rewritten for Scala 2.13: PartialFunction(f)
  // no longer exists; an explicit anonymous class is used instead.

  @Test
  def function_isDefinedAtAllInputs(): Unit = {
    val transformer = AuthenticationTransformer.function[String, Int](_.length)
    val pf = transformer.partial
    assert(pf.isDefinedAt("hello"))
    assert(pf.isDefinedAt(""))
  }

  @Test
  def function_appliesTransformation(): Unit = {
    val transformer = AuthenticationTransformer.function[String, Int](_.length)
    assertResult(5)(transformer.partial("hello"))
    assertResult(0)(transformer.partial(""))
  }

  @Test
  def function_withNullInput_doesNotThrow(): Unit = {
    val transformer = AuthenticationTransformer.function[String, String](Option(_).getOrElse(""))
    assertResult("")(transformer.partial(null))
  }

  // ─── AuthenticationTransformer.identityTransformer ───────────────────────────

  @Test
  def identityTransformer_passesValueThrough(): Unit = {
    val transformer = AuthenticationTransformer.identityTransformer[Int]
    assertResult(42)(transformer.partial(42))
  }

  @Test
  def identityTransformer_isDefinedAtAll(): Unit = {
    val transformer = AuthenticationTransformer.identityTransformer[String]
    assert(transformer.partial.isDefinedAt("anything"))
    assert(transformer.partial.isDefinedAt(null))
  }

  @Test
  def identityTransformer_forCaseClass_passesThrough(): Unit = {
    case class UserId(value: Int)
    val transformer = AuthenticationTransformer.identityTransformer[UserId]
    val id = UserId(99)
    assertResult(id)(transformer.partial(id))
  }
}
