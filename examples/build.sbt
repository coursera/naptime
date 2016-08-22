name := "examples"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  courierRuntime,
  cache,
  sangria,
  sangriaPlayJson
)

dependencyOverrides += playJson

org.coursera.courier.sbt.CourierPlugin.courierSettings

sourceDirectories in (Compile, TwirlKeys.compileTemplates) := (unmanagedSourceDirectories in Compile).value
