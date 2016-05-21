package org.coursera.naptime.sbt

import StringEscaper.escapeJson

object ScaladocSerializer {
  def serialize(docs: Map[String, String]): String = {
    "{" +
      docs
        .map { case (key, value) =>
          "\"" + escapeJson(key) + "\":{\"body\":\"" + escapeJson(value) + "\"}"
        }
        .mkString(",") +
      "}"
  }
}
