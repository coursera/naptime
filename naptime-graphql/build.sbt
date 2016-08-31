name := "naptime-graphql"

libraryDependencies ++= Seq(
  courierRuntime,
  playJson,
  sangria,
  sangriaPlayJson,
  scalaLogging,
  junit,
  junitInterface,
  scalatest,
  mockito
)

dependencyOverrides += playJson

org.coursera.courier.sbt.CourierPlugin.courierSettings
