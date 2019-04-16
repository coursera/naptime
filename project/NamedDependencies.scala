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
import sbt.Keys._

trait PluginVersionProvider {
  def playVersion: String
  def playJsonVersion: String
  def courierVersion: String
}

trait NamedDependencies { this: PluginVersionProvider =>
  val courierRuntime = "org.coursera.courier" %% "courier-runtime" % courierVersion
  val courscala = "org.coursera" %% "courscala" % "0.1.3"
  val governator = "com.netflix.governator" % "governator" % "1.10.5"
  val guice = "com.google.inject" % "guice" % "4.1.0"
  val guiceMultibindings = "com.google.inject.extensions" % "guice-multibindings" % "4.1.0"
  val jodaConvert = "org.joda" % "joda-convert" % "1.9.2"
  val jodaTime = "joda-time" % "joda-time" % "2.9.9"
  val playJson = "com.typesafe.play" %% "play-json" % playJsonVersion
  val playTestCompile = ("com.typesafe.play" %% "play-test" % playVersion)
    .excludeAll(new ExclusionRule(organization="org.specs2"))
  val sangria = "org.sangria-graphql" %% "sangria" % "1.4.2"
  val sangriaSlowLog = "org.sangria-graphql" %% "sangria-slowlog" % "0.1.8"
  val scalaGuice = "net.codingwell" %% "scala-guice" % "4.1.1"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2"
  val opentracing = "io.opentracing" % "opentracing-api" % "0.32.0"

  // Test dependencies
  val junitCompile = "junit" % "junit" % "4.11"
  val junit = junitCompile % "test"
  val junitInterface = "com.novocode" % "junit-interface" % "0.11" % "test"
  val mockitoCompile = "org.mockito" % "mockito-all" % "1.9.5"
  val mockito = mockitoCompile % "test"
  val scalatestCompile = "org.scalatest" %% "scalatest" % "3.0.4"
  val scalatest = scalatestCompile % "test"

}
