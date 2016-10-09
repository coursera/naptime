name := "naptime"

libraryDependencies ++= Seq(
  courierRuntime,
  governator,
  guice,
  guiceMultibindings,
  jodaTime,
  jodaConvert,
  playJson,
  scalaGuice,
  scalaLogging,
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  junit,
  junitInterface,
  scalatest,
  mockito
)

dependencyOverrides += playJson

org.coursera.courier.sbt.CourierPlugin.courierSettings

// Disable deprecation warnings entirely so they don't turn into errors.
scalacOptions := scalacOptions.value.filterNot(_.startsWith("-deprecation"))
scalacOptions ++= Seq("-deprecation:false", "-Xfatal-warnings")
