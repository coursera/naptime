// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.4")

// Add the coursera sbt plugin bintray repository for the courier-sbt-plugin until it gets mirrored
// into the community sbt plugin repository.
resolvers += Resolver.bintrayRepo("coursera", "sbt-plugins")

// Courier binding generator plugin
addSbtPlugin("org.coursera.courier" % "courier-sbt-plugin" % "2.1.1")

// From http://www.scala-sbt.org/0.13/docs/Testing-sbt-plugins.html#step+3%3A+src%2Fsbt-test
sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("org.coursera.naptime" % "sbt-naptime" % x)
  case _ => sys.error("""|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}
