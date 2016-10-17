name := "naptime-models"

libraryDependencies ++= Seq(
  courierRuntime,
  courscala,
  playJson,
  scalaLogging,
  junitInterface,
  scalatest)

org.coursera.courier.sbt.CourierPlugin.courierSettings

// Disable deprecation warnings entirely so they don't turn into errors.
scalacOptions := scalacOptions.value.filterNot(_.startsWith("-deprecation"))
scalacOptions ++= Seq("-deprecation:false", "-Xfatal-warnings")
