package org.coursera.naptime.sbt

object StringEscaper {
  def escapeJson(unescaped: String): String = {
    unescaped
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\b", "\\b")
      .replace("\f", "\\f")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
  }

  def escapeHtml(unescaped: String): String = {
    unescaped
      .replace("&", "&amp;")
      .replace("<", "&lt")
      .replace(">", "&gt")
      .replace("\"", "&quot;")
      .replace("\'", "&apos;")
  }
}
