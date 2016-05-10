name := "examples"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  courierRuntime,
  cache
)

org.coursera.courier.sbt.CourierPlugin.courierSettings
