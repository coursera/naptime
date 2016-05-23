// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.4")

// Courier binding generator plugin
addSbtPlugin("org.coursera.courier" % "courier-sbt-plugin" % "2.0.2")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "1.1")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

// Add build information for the Scaladoc plugin.
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.3.1")

libraryDependencies += { "org.scala-sbt" % "scripted-plugin" % sbtVersion.value }
