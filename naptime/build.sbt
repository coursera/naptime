name := "naptime"

libraryDependencies ++= Seq(
  courierRuntime,
  governator,
  NaptimeBuild.guice,
  guiceMultibindings,
  jodaTime,
  jodaConvert,
  playJson,
  playJsonJoda,
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
