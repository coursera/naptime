name := "naptime-graphql"

libraryDependencies ++= Seq(
  courierRuntime,
  playJson,
  sangria,
  scalaLogging,
  junit,
  junitInterface,
  scalatest,
  mockito
)

org.coursera.courier.sbt.CourierPlugin.courierSettings
