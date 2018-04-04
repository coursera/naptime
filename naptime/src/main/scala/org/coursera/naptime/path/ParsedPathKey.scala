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

package org.coursera.naptime.path

/**
 * A specialized HList with the minimal features required for our purposes.
 *
 * Note, we use `:::` for concatenation to separate ourselves from normal lists which use `::`.
 */
sealed trait ParsedPathKey {
  final def :::[H](h: H): :::[H, this.type] = new :::(h, this)
}

/**
 * HCons - Add a new element (of arbitrary type) to the front of the list.
 *
 * @param h The parsed element we are holding.
 * @param t The rest of the HList.
 */
final case class :::[+H, +T <: ParsedPathKey](h: H, t: T) extends ParsedPathKey {
  override def toString: String = s"$h ::: $t"
  def head: H = h
  def key: H = h
  def tail: T = t

  def parentKey[H2, T2 <: ParsedPathKey](implicit ev: T <:< (H2 ::: T2)): H2 =
    ev(t).h
  def grandparentKey[H2, H3, T2 <: ParsedPathKey](implicit ev: T <:< (H2 ::: H3 ::: T2)): H3 =
    ev(t).t.head
}

/**
 * HNil - the end of a ParsedPathKey.
 */
sealed trait RootParsedPathKey extends ParsedPathKey
case object RootParsedPathKey extends RootParsedPathKey
