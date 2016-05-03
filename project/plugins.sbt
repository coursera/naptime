// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.4")

// Add the coursera sbt plugin bintray repository for the courier-sbt-plugin until it gets mirrored
// into the community sbt plugin repository.
resolvers += Resolver.bintrayRepo("coursera", "sbt-plugins")

// Courier binding generator plugin
addSbtPlugin("org.coursera.courier" % "courier-sbt-plugin" % "2.0.2")
