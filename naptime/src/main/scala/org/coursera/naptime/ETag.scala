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
 * Note: Naptime always uses weak validators because the framework does not guarantee byte-equality
 * between responses.
 *
 * @param weakValidator tag without surrounding `"`s; these are added by Naptime
 */
case class ETag(weakValidator: String)

object ETag {

  private[this] val weakValidatorRegex = "W/\"(.*)\"".r

  implicit val stringKeyFormat: StringKeyFormat[ETag] = {
    def from(s: String): Option[ETag] = s match {
      case weakValidatorRegex(tag) => Some(ETag(tag))
      case _ => None
    }
    StringKeyFormat.delegateFormat(from, eTag => s"""W/"${eTag.weakValidator}"""")
  }

}
