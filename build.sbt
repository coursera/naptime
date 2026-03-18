import NaptimeBuild._
import NamedDependencies._

ThisBuild / scalaVersion := "2.13.12"

ThisBuild / crossScalaVersions := Seq("2.12.19", "2.13.12")

ThisBuild / organization := "org.coursera.naptime"

// Allow scala-xml eviction: Play/Twirl/scalatest pull in different minor versions.
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

lazy val root = project
  .in(file("."))
  .settings(org.coursera.naptime.sbt.Sonatype.settings)
  .aggregate(naptime, graphql, models, testing, pegasus)

lazy val naptime = configure(project)
  .in(file("naptime"))
  .dependsOn(models)

lazy val graphql = configure(project)
  .in(file("naptime-graphql"))
  .dependsOn(naptime % "test->test;compile->compile")

lazy val models = configure(project)
  .in(file("naptime-models"))
  .dependsOn(pegasus)

lazy val testing = configure(project)
  .in(file("naptime-testing"))
  .dependsOn(naptime % "test->test;compile->compile")

lazy val pegasus = project
  .in(file("naptime-pegasus"))
  .settings(testSettings)
  .settings(org.coursera.naptime.sbt.Sonatype.settings)

lazy val examples = configure(project)
  .in(file("examples"))
  .dependsOn(naptime, testing, graphql)
