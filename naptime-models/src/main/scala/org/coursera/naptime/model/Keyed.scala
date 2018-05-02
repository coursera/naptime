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

package org.coursera.naptime.model

import play.api.libs.json.OWrites
import play.api.libs.json.Reads
import play.api.libs.json.__

case class Keyed[+Key, +Value](key: Key, value: Value) {

  def mapValue[T](f: Value => T): Keyed[Key, T] = copy(value = f(value))

  def tuple: (Key, Value) = (key, value)

}

object Keyed {

  def tupled[K, V]: Tuple2[K, V] => Keyed[K, V] = Function.tupled(apply)

  implicit def reads[Key, Value](
      implicit keyFormat: KeyFormat[Key],
      valueReads: Reads[Value]): Reads[Keyed[Key, Value]] = {

    import play.api.libs.functional.syntax._

    val builder = __.read[Key](keyFormat.format) and __.read[Value]
    builder(apply[Key, Value] _)
  }

  implicit def writes[Key, Value](
      implicit keyFormat: KeyFormat[Key],
      valueWrites: OWrites[Value]): OWrites[Keyed[Key, Value]] = {

    OWrites[Keyed[Key, Value]] {
      case Keyed(key, value) =>
        val keyObject = keyFormat.format.writes(key)
        val valueObject = valueWrites.writes(value)

        val conflictingKeys = keyObject.keys.intersect(valueObject.keys)
        require(conflictingKeys.isEmpty, s"Conflicting key and value fields: $conflictingKeys")

        keyObject ++ valueObject
    }
  }

}
