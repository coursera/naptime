name := "naptime-models"

libraryDependencies ++= Seq(
  courierRuntime,
  courscala,
  playJson,
  scalaLogging,
  junitInterface,
  junit,
  scalatest,
  scalatestPlusJunit,
  scalatestPlusMockito,
  mockito)

org.coursera.courier.sbt.CourierPlugin.courierSettings
