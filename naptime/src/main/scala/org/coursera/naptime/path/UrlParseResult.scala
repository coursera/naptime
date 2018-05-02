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
 * Effectively an Option[(Option[String], T)] used for partially parsing URLs for PathKeys.
 *
 * @tparam T The type we are supposed to parse from the url.
 */
sealed trait UrlParseResult[+T] {
  def isEmpty: Boolean
  def getOrElse[B >: T](other: => B): B = {
    if (isEmpty) {
      other
    } else {
      get
    }
  }

  @inline final def filter(cond: T => Boolean): UrlParseResult[T] = {
    if (isEmpty) {
      this
    } else {
      if (cond(get)) {
        this
      } else {
        ParseFailure
      }
    }
  }

  @inline final def map[B](f: T => B): UrlParseResult[B] = {
    if (isEmpty) {
      ParseFailure
    } else {
      ParseSuccess(getUrl, f(get))
    }
  }

  @inline final def flatMap[B](f: (Option[String], T) => UrlParseResult[B]) = {
    if (isEmpty) {
      ParseFailure
    } else {
      f(getUrl, get)
    }
  }

  protected[this] def get: T
  protected[this] def getUrl: Option[String]
}

/**
 * Indicates the request did not match / did not parse successfully.
 */
case object ParseFailure extends UrlParseResult[Nothing] {
  override def isEmpty: Boolean = true

  protected[this] override def get =
    throw new NoSuchElementException("ParseFailure.get")

  protected[this] override def getUrl =
    throw new NoSuchElementException("ParseFailure.getUrl")
}

// TODO: consider returning an offset into the original URL to avoid all the string copying.
final case class ParseSuccess[+T](restOfUrl: Option[String], elem: T) extends UrlParseResult[T] {
  override def isEmpty: Boolean = false

  protected[this] override def get: T = elem

  protected[this] override def getUrl: Option[String] = restOfUrl
}
