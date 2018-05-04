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

import org.coursera.common.stringkey.StringKey
import org.coursera.common.stringkey.StringKeyFormat

/**
 * Partially parses type `T` from a URL.
 */
trait ResourcePathParser[+T] {

  /**
   * Attempt to parse an element of type T from the URL as per the routing of the resource.
   * If the URL matches, return [[ParseSuccess]] with the rest of the URL, and the parsed element.
   * If the URL does not match, return [[ParseFailure]]
   *
   * @param url URL to parse from.
   * @return the result of attempted parsing.
   */
  def parseUrl(url: String): UrlParseResult[T]

  def parseOptUrl(url: String): UrlParseResult[Option[T]]
}

// TODO: migrate from StringKeyFormat to a UrlParseFormat or something.
case class CollectionResourcePathParser[+T](resourceName: String, resourceVersion: Int)(
    implicit stringFormat: StringKeyFormat[T])
    extends ResourcePathParser[T] {
  private[this] val REGEX = if (resourceVersion > 0) {
    s"/$resourceName.v$resourceVersion(/(([^/]+)(/.*)?)?)?".r
  } else {
    s"/$resourceName(/(([^/]+)(/.*)?)?)?".r
  }

  /**
   * Parse the url as if there were nesting / etc.
   * @param url The url to parse a `T` from.
   * @return Either a [[ParseSuccess]] or a [[ParseFailure]].
   */
  override def parseUrl(url: String): UrlParseResult[T] = {
    parseOptUrl(url).flatMap {
      case (rest, keyOpt) =>
        if (keyOpt.isDefined) {
          ParseSuccess(rest, keyOpt.get)
        } else {
          ParseFailure
        }
    }
  }

  /**
   * Parse the URL leaving the option for the key to be missing (i.e. in the case
   * that the path for the request is for a finder, or an action, instead of for an individual
   * element in the collection.)
   *
   * @param url The url to parse an optional `T` from.
   * @return The parsed result.
   */
  override def parseOptUrl(url: String): UrlParseResult[Option[T]] = {
    url match {
      case REGEX(_, _, keyNullable, restNullable) =>
        val rest = Option(restNullable).filter(_ != "").filter(_ != "/")
        Option(keyNullable)
          .filter(_.nonEmpty)
          .map { keyStr =>
            stringFormat
              .reads(StringKey(keyStr))
              .map { key =>
                ParseSuccess(rest, Some(key))
              }
              .getOrElse {
                ParseFailure
              }
          }
          .getOrElse(ParseSuccess(rest, None))
      case _ =>
        ParseFailure
    }
  }
}
