// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.24")

// Courier binding generator plugin
addSbtPlugin("org.coursera.courier" % "courier-sbt-plugin" % "2.1.4")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "1.1")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

// Add build information for the Scaladoc plugin.
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.3.1")

libraryDependencies += { "org.scala-sbt" % "scripted-plugin" % sbtVersion.value }

// addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.5.1")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.5.1-2-g8b57b53")

addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.15")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.7")

addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "2.112")

