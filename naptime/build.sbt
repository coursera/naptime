import NaptimeBuild._
import NamedDependencies._

name := "naptime"

libraryDependencies ++= Seq(
  courierRuntime,
  governator,
  guiceDep,
  guiceMultibindingsDep,
  jodaTime,
  jodaConvert,
  playJson,
  scalaGuice,
  scalaLogging,
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  junit,
  junitInterface,
  scalatest,
  scalatestPlusJunit,
  scalatestPlusMockito,
  mockito
)

org.coursera.courier.sbt.CourierPlugin.courierSettings
