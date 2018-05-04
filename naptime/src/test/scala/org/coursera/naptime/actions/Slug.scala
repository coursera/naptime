package org.coursera.naptime.actions

import org.coursera.common.jsonformat.JsonFormats
import org.coursera.common.stringkey.StringKeyFormat
import play.api.libs.json.Format

import scala.annotation.tailrec

/**
 * A somewhat-human-readable identifier. This will generally look like "what-is-machine-learning".
 * This is suitable for URLs, etc. which require unique but readable identifiers that may be
 * (very infrequently) changed.
 *
 *
 * This is a Coursera-defined notion of slug and you should not expect it to match any external standard.
 * Do not change the validation because lots of existing opencourse data expects the existing validation.
 */
case class Slug(string: String) {
  require(Slug.ValidRegex.pattern.matcher(string).matches, s"Slug not allowed: $string")
}

object Slug {

  implicit val stringKeyFormat: StringKeyFormat[Slug] =
    StringKeyFormat.delegateFormat[Slug, String](
      key =>
        try {
          Some(Slug(key))
        } catch {
          case e: IllegalArgumentException => None
      },
      _.string)

  implicit val format: Format[Slug] = JsonFormats.stringKeyFormat

  private val ValidRegex = """[a-z0-9\-]+""".r
  private val MaxSlugLength = 80

  /**
   * Method to create a URL friendly slug string from a given input string
   * Most of the logic for this has been copied from Play framework's Java
   * extension implementation here:
   * https://github.com/playframework/play1/blob/b835b790c795bddd7d41ac6a4a7a1eb6922fab2f/framework/src/play/templates/JavaExtensions.java#L368.
   */
  def slugify(input: String): Slug = {
    // Convert the unicode input to ascii. This ensures that non Latin based languages have
    // reasonable conversions.
    val slugString = input //Junidecode.unidecode(input)
      .replaceAll("([a-z])'s([^a-z])", "$1s$2") // Convert apostrophes.
      .replaceAll("[^a-zA-Z0-9]", "-") // Convert all non alphanumeric characters with hyphen.
      .replaceAll("-{2,}", "-") // Collapse multiple hyphens into one
      .stripPrefix("-")
      .stripSuffix("-")
      .toLowerCase

    val words = slugString.split("-").toList
    if (words.head.length() > MaxSlugLength) {
      Slug(words.head.take(MaxSlugLength))
    } else {
      @tailrec
      def buildTruncatedSlug(currentWord: String, wordList: List[String]): String = {
        wordList match {
          case firstWord :: tail =>
            val candidate = currentWord + "-" + firstWord
            if (candidate.length() > MaxSlugLength) {
              currentWord
            } else {
              buildTruncatedSlug(candidate, tail)
            }
          case Nil => currentWord
        }
      }
      Slug(buildTruncatedSlug(words.head, words.drop(1)))
    }
  }

}
