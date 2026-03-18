// Play 2.8 sbt plugin (requires Akka 2.6, supports Scala 2.12 + 2.13)
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.21")

// Allow eviction of scala-xml: Play/Twirl/scalariform pull in different minor versions.
// SBT 1.9 enforces eviction by default; we permit it here since 2.x is binary-compatible with 1.x.
libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

// Courier binding generator plugin (our upgraded version)
addSbtPlugin("org.coursera.courier" % "courier-sbt-plugin" % "3.0.2")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.10.0")

addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.11")

addSbtPlugin("com.github.sbt" % "sbt-jacoco" % "3.4.0")
