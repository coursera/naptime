
/*
 Copyright 2015 Coursera Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.coursera.naptime.sbt

import sbt._
import Keys._

object Sonatype {

  val Settings: Seq[Def.Setting[_]] = Seq(
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org"
      if (version.value.trim.endsWith("SNAPSHOT")) {
        Some("snapshots" at s"$nexus/content/repositories/snapshots")
      } else {
        Some("releases" at s"$nexus/service/local/staging/deploy/maven2")
      }
    },
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },

    pomExtra := {
      <url>http://github.com/coursera/naptime</url>
        <licenses>
          <license>
            <name>Apache 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:coursera/naptime.git</url>
          <connection>scm:git:git@github.com:coursera/naptime.git</connection>
        </scm>
        <developers>
          <developer>
            <id>saeta</id>
            <name>Brennan Saeta</name>
          </developer>
          <developer>
            <id>Daniel Chia</id>
            <name>danchia</name>
          </developer>
          <developer>
            <id>Nick Dellamaggiore</id>
            <name>nick</name>
          </developer>
          <developer>
            <id>Josh Newman</id>
            <name>josh</name>
          </developer>
        </developers>
    }
  )

}
