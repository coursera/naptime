/*
 * Copyright 2016 Coursera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt._
import sbt.Keys
import play.sbt.PlayFilters
import play.sbt.PlayAkkaHttpServer
import play.sbt.PlayLayoutPlugin
import play.sbt.PlayScala

object NaptimeBuild {

  val playVersion = "2.8.21"
  val playJsonVersion = "2.9.4"
  val courierVersion = "3.0.2"

  lazy val testSettings: Seq[Setting[_]] = Seq(
    Keys.testFrameworks := Seq(sbt.TestFrameworks.JUnit),
    Keys.testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-q", "-a"))

  def configure(project: Project): Project = {
    project
      .enablePlugins(PlayScala)
      .disablePlugins(PlayLayoutPlugin, PlayFilters, PlayAkkaHttpServer)
      .settings(testSettings)
      .settings(org.coursera.naptime.sbt.Sonatype.settings)
  }

}
