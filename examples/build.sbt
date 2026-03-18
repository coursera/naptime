import NamedDependencies._

name := "examples"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  courierRuntime,
  ehcache,
  sangria
)

org.coursera.courier.sbt.CourierPlugin.courierSettings

Compile / TwirlKeys.compileTemplates / sourceDirectories := (Compile / unmanagedSourceDirectories).value
