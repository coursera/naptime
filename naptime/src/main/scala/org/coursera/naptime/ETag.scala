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

import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.common.stringkey.StringKeyFormat.Implicits.OrFormat

sealed trait ETag

object ETag {

  implicit val stringKeyFormat: StringKeyFormat[ETag] = {
    StringKeyFormat.unimplementedFormat[ETag]
      .orFormat[Strong]
      .orFormat[Weak]
  }

  /**
   * TODO(josh): Should Naptime support this at all? Play JSON serialization doesn't allow
   * byte-level response consistency, so using strong ETags may not be appropriate for Naptime
   * APIs (since they all return Play JSON objects).
   *
   * @param tag tag without surrounding `"`s; these are added by Naptime
   */
  case class Strong(tag: String) extends ETag

  object Strong {
    private[this] val regex = "\"(.*)\"".r
    implicit val stringKeyFormat: StringKeyFormat[Strong] = {
      def from(s: String): Option[Strong] = s match {
        case regex(tag) => Some(Strong(tag))
        case _ => None
      }
      StringKeyFormat.delegateFormat(from, strong => s""""${strong.tag}"""")
    }
  }

  /**
   * @param tag tag without surrounding `"`s; these are added by Naptime
   */
  case class Weak(tag: String) extends ETag

  object Weak {
    private[this] val regex = "W/\"(.*)\"".r
    implicit val stringKeyFormat: StringKeyFormat[Weak] = {
      def from(s: String): Option[Weak] = s match {
        case regex(tag) => Some(Weak(tag))
        case _ => None
      }
      StringKeyFormat.delegateFormat(from, weak => s"""W/"${weak.tag}"""")
    }
  }

}
