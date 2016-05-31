name := "naptime-tests"

libraryDependencies ++= Seq(
  scalaLogging,
  junitCompile,
  junitInterface,
  scalatestCompile,
  playTestCompile,
  mockitoCompile,
  "com.chuusai" %% "shapeless" % "2.2.5" % "test" // Added for illTyped macro.
)

// Courier data binding generator
org.coursera.courier.sbt.CourierPlugin.courierSettings
