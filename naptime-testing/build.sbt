import NaptimeBuild._
import NamedDependencies._

name := "naptime-tests"

libraryDependencies ++= Seq(
  scalaLogging,
  junitCompile,
  junitInterface,
  scalatestCompile,
  playTestCompile,
  mockitoCompile,
  scalatestPlusJunit,
  scalatestPlusMockito,
  "com.chuusai" %% "shapeless" % "2.3.10" % "test" // Added for illTyped macro.
)

// Courier data binding generator
org.coursera.courier.sbt.CourierPlugin.courierSettings
