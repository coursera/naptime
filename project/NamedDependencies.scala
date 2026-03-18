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

object NamedDependencies {
  import NaptimeBuild._

  val courierRuntime = "org.coursera.courier" %% "courier-runtime" % courierVersion
  val courscala = "org.coursera" %% "courscala" % "0.2.0"
  val governator = "com.netflix.governator" % "governator" % "1.10.5"
  // Prefixed with "dep" to avoid collision with Play's auto-imported `guice` value.
  val guiceDep = "com.google.inject" % "guice" % "4.2.3"
  val guiceMultibindingsDep = "com.google.inject.extensions" % "guice-multibindings" % "4.2.3"
  val jodaConvert = "org.joda" % "joda-convert" % "2.2.3"
  val jodaTime = "joda-time" % "joda-time" % "2.12.5"
  val playJson = "com.typesafe.play" %% "play-json" % playJsonVersion
  val playTestCompile = ("com.typesafe.play" %% "play-test" % playVersion)
    .excludeAll(ExclusionRule(organization = "org.specs2"))
  val sangria = "org.sangria-graphql" %% "sangria" % "2.1.6"
  val sangriaSlowLog = "org.sangria-graphql" %% "sangria-slowlog" % "2.0.2"
  val scalaGuice = "net.codingwell" %% "scala-guice" % "5.1.1"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"

  // Test dependencies
  val junitCompile = "junit" % "junit" % "4.13.2"
  val junit = junitCompile % "test"
  val junitInterface = "com.novocode" % "junit-interface" % "0.11" % "test"
  val mockitoCompile = "org.mockito" % "mockito-core" % "4.11.0"
  val mockito = mockitoCompile % "test"
  val scalatestCompile = "org.scalatest" %% "scalatest" % "3.2.19"
  val scalatest = scalatestCompile % "test"
  val scalatestPlusJunit = "org.scalatestplus" %% "junit-4-13" % "3.2.19.0" % "test"
  val scalatestPlusMockito = "org.scalatestplus" %% "mockito-4-11" % "3.2.18.0" % "test"
}
