name := "simple"

routesGenerator := InjectedRoutesGenerator

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  "org.coursera.courier" %% "courier-runtime" % "2.0.2",
  cache
)

org.coursera.courier.sbt.CourierPlugin.courierSettings
