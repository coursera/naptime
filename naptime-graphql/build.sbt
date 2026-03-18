import NaptimeBuild._
import NamedDependencies._

name := "naptime-graphql"

libraryDependencies ++= Seq(
  courierRuntime,
  playJson,
  sangria,
  sangriaSlowLog,
  scalaLogging,
  junit,
  junitInterface,
  scalatest,
  scalatestPlusJunit,
  scalatestPlusMockito,
  mockito
)

org.coursera.courier.sbt.CourierPlugin.courierSettings
