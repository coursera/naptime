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
  mockito
)

org.coursera.courier.sbt.CourierPlugin.courierSettings
