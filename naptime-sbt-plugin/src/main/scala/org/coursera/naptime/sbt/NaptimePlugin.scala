package org.coursera.naptime.sbt

import sbt._
import sbt.Keys._

object NaptimePlugin extends AutoPlugin {
  object autoImport {
    val naptimeScaladocFile = settingKey[File]("Resource file for Naptime scaladocs.")
    val naptimeScaladoc = taskKey[Seq[File]]("Generate Naptime docs.")
    val naptimeFileNameRegex = settingKey[String]("Filter the files parsed for Scaladocs.")
  }
  import autoImport._

  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = play.sbt.PlayScala



  override lazy val projectSettings = List(
    naptimeFileNameRegex := "*Resource.scala",
    naptimeScaladocFile in Compile := (resourceManaged in Compile).value / "naptime.scaladoc.json",
    naptimeScaladoc in Compile := {
      val finder = (scalaSource in Compile).value ** naptimeFileNameRegex.value
      val scaldocs = ScaladocExtractor
        .analyze(finder.get)
        .mapValues(comment => comment.body.blocks.map(CommentRenderer.renderBlock).mkString)
      val serializedScaladocs = ScaladocSerializer.serialize(scaldocs)

      val file = (naptimeScaladocFile in Compile).value
      IO.write(file, serializedScaladocs)
      List(file)
    },
    resourceGenerators in Compile <+= naptimeScaladoc in Compile
  )
}
