package org.coursera.naptime.sbt

import sbtbuildinfo.BuildInfo

object ScalaLibraryLocator {
  def getPath: Option[String] = {
    val pattern = "library jar: ([^,]+)".r
    pattern.findFirstMatchIn(BuildInfo.scalaInstance).map(_.group(1))
  }
}
