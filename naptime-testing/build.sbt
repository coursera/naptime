name := "naptime-tests"

libraryDependencies ++= Seq(
  scalaLogging,
  junitCompile,
  junitInterface,
  scalatestCompile,
  playTestCompile,
  mockitoCompile,
  "com.chuusai" %% "shapeless" % "2.3.2" % "test" // Added for illTyped macro.
)

dependencyOverrides += playJson

// Courier data binding generator
org.coursera.courier.sbt.CourierPlugin.courierSettings
