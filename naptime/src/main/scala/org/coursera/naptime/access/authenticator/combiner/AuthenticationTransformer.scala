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

package org.coursera.naptime.access.authenticator.combiner

/**
 * Transforms authentication information to a new type, for use in combiners.
 * If transformers are available for `A`, `B`, and `C`, the combined authentication can use simple
 * type `D` instead of something like `(Option[A], Option[B], Option[C])`.
 *
 * Implementation note: This is a new `trait` instead of a type alias to prevent accidental
 * implicit use. It's sealed to control how `partial` is defined to make future refactorings easier
 * (maybe it should be a complete lifted function, etc.).
 */
sealed trait AuthenticationTransformer[-I, +O] {
  def partial: PartialFunction[I, O]
}

object AuthenticationTransformer {

  def apply[I, O](f: PartialFunction[I, O]): AuthenticationTransformer[I, O] = {
    new AuthenticationTransformer[I, O] {
      override def partial: PartialFunction[I, O] = f
    }
  }

  def function[I, O](f: I => O): AuthenticationTransformer[I, O] =
    apply(PartialFunction(f))

  implicit def identityTransformer[T]: AuthenticationTransformer[T, T] =
    function(identity)

}
