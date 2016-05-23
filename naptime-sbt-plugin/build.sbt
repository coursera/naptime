// Adhere to the naming conventions for sbt plugins defined at:
// http://www.scala-sbt.org/0.13/docs/Plugins-Best-Practices.html
name := "sbt-naptime"

// In order to support the scripted plugin, use a snapshot version.
version := version.value + "-SNAPSHOT"

sbtPlugin := true

scalaVersion := "2.10.6"

scalaBinaryVersion := "2.10"

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](scalaInstance)

buildInfoPackage := "sbtbuildinfo"

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.4")

licenses += ("Apache-2", url("https://opensource.org/licenses/Apache-2.0"))

description := "API Framework for developer productivity. http://coursera.github.io/naptime/"

publishMavenStyle := false

bintrayRepository := "sbt-plugins"

bintrayOrganization := Some("coursera")
