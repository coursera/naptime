name := "naptime-models"

libraryDependencies ++= Seq(
  courierRuntime,
  courscala,
  playJson,
  scalaLogging,
  junitInterface,
  scalatest)

org.coursera.courier.sbt.CourierPlugin.courierSettings
