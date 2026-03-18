import NaptimeBuild._
import NamedDependencies._

name := "naptime-models"

libraryDependencies ++= Seq(
  courierRuntime,
  courscala,
  playJson,
  scalaLogging,
  junitInterface,
  scalatest,
  scalatestPlusJunit)

org.coursera.courier.sbt.CourierPlugin.courierSettings
