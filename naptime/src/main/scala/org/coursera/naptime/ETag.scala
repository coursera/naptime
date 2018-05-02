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

/**
 * Resources should always use weak validators because Naptime does not guarantee byte-equality
 * between responses. See [[ETag.Weak]].
 */
sealed trait ETag

object ETag {

  def apply(weakValidator: String): Weak = Weak(weakValidator)

  implicit val stringKeyFormat: StringKeyFormat[ETag] = {
    StringKeyFormat
      .unimplementedFormat[ETag]
      .orFormat[Weak]
      .orFormat[Strong]
  }

  /**
   * @param weakValidator tag without surrounding `"`s; these are added by Naptime
   */
  case class Weak(weakValidator: String) extends ETag {
    // See https://tools.ietf.org/html/rfc7232#section-2.3.
    require(!weakValidator.contains("\"\\"), """Characters '"' and '\' not allowed in ETags""")
  }

  object Weak {

    private[this] val weakValidatorRegex = "W/\"(.*)\"".r

    implicit val stringKeyFormat: StringKeyFormat[Weak] = {
      def from(s: String): Option[Weak] = s match {
        case weakValidatorRegex(tag) => Some(Weak(tag))
        case _                       => None
      }
      StringKeyFormat.delegateFormat(from, weak => s"""W/"${weak.weakValidator}"""")
    }

  }

  /**
   * @param strongValidator tag without surrounding `"`s; these are added by Naptime
   */
  @deprecated(message = "Use weak validators; see `ETag`'s Scaladoc", since = "0.1.6")
  case class Strong(strongValidator: String) extends ETag {
    // See https://tools.ietf.org/html/rfc7232#section-2.3.
    require(!strongValidator.contains("\"\\"), """Characters '"' and '\' not allowed in ETags""")
  }

  object Strong {

    private[this] val strongValidatorRegex = "\"(.*)\"".r

    implicit val stringKeyFormat: StringKeyFormat[Strong] = {
      def from(s: String): Option[Strong] = s match {
        case strongValidatorRegex(tag) => Some(Strong(tag))
        case _                         => None
      }
      StringKeyFormat.delegateFormat(from, strong => s""""${strong.strongValidator}"""")
    }

  }

}
