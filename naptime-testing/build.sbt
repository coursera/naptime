name := "naptime-tests"

javacOptions in Test ++= Seq("-source", "1.8", "-target", "1.8")

libraryDependencies ++= Seq(
  scalaLogging,
  junitCompile,
  junitInterface,
  scalatestCompile,
  playTestCompile,
  mockitoCompile,
  "org.scalatestplus" %% "junit-4-13" % "3.2.19.0" % "test",
  "org.scalatestplus" %% "mockito-4-11" % "3.2.18.0" % "test",
  "com.chuusai" %% "shapeless" % "2.3.2" % "test" // Added for illTyped macro.
)

dependencyOverrides += playJson

// Courier data binding generator
org.coursera.courier.sbt.CourierPlugin.courierSettings
