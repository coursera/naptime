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
 * An HList for parsers, usually built up automatically via the helper traits (TODO: FILL IN LINKS)
 *
 * @tparam ParseAsType The type this HList of parsers will parse a URL into.
 */
sealed trait PathKeyParser[+ParseAsType <: ParsedPathKey] {
  def parse(path: String): UrlParseResult[ParseAsType]

  /**
   * Concatenates a new parser on the front of the chain (nesting that parser underneath the others)
   * @param resourcePath The resource path (and parse information) for the new resource.
   * @tparam H The new resource has path parameters of this type.
   * @tparam TT This type parameter exists to work around the covariance/contravariance issues with
   *            the return type of this function.
   * @return The new chain.
   */
  def ::[H, TT >: ParseAsType <: ParsedPathKey](
      resourcePath: ResourcePathParser[H]): NestedPathKeyParser[H, TT] =
    new NestedPathKeyParser[H, TT](resourcePath, this)
}

/**
 * HCons for parsers - includes an extra type parameter because we care about both the type we will
 * parse as, as well as the type of the chain itself.
 *
 * @param head All information needed to parse from a URL something of the type H
 * @param tail The rest of the parser chain.
 * @tparam H The type the head of the chain will parse as.
 * @tparam TailParseAsType The tail of the chain will parse as this type
 */
final case class NestedPathKeyParser[+H, TailParseAsType <: ParsedPathKey](
    head: ResourcePathParser[H],
    tail: PathKeyParser[TailParseAsType])
    extends PathKeyParser[H ::: TailParseAsType] {

  override def toString = s"$head :: $tail"

  override def parse(path: String): UrlParseResult[H ::: TailParseAsType] = {
    parseFinalLevel(path) match {
      case ParseSuccess(restOfUrl, Some(h) ::: rest) =>
        ParseSuccess(restOfUrl, h ::: rest)
      case _ => ParseFailure

    }
  }

  def parseFinalLevel(path: String): UrlParseResult[Option[H] ::: TailParseAsType] = {
    tail.parse(path) match {
      case ParseFailure          => ParseFailure
      case ParseSuccess(None, _) => ParseFailure // Nothing left to parse; fail.
      case ParseSuccess(Some(urlThroughParent), tailParse) =>
        head.parseOptUrl(urlThroughParent) match {
          case ParseFailure => ParseFailure
          case ParseSuccess(restOfUrl, elem) =>
            ParseSuccess(restOfUrl, new :::(elem, tailParse))
        }
    }
  }
}

/**
 * The base / end of the PathKey HList-like datastructure.
 */
sealed trait RootPathParser extends PathKeyParser[RootParsedPathKey] {
  override def parse(path: String): UrlParseResult[RootParsedPathKey] =
    ParseSuccess(Some(path), RootParsedPathKey)
}

case object RootPathParser extends RootPathParser
