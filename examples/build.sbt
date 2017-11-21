name := "examples"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  courierRuntime,
  ehcache,
  sangria
)

org.coursera.courier.sbt.CourierPlugin.courierSettings

sourceDirectories in (Compile, TwirlKeys.compileTemplates) := (unmanagedSourceDirectories in Compile).value
