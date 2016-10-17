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

dependencyOverrides += playJson

// Courier data binding generator
org.coursera.courier.sbt.CourierPlugin.courierSettings

// Disable deprecation warnings entirely so they don't turn into errors.
scalacOptions := scalacOptions.value.filterNot(_.startsWith("-deprecation"))
scalacOptions ++= Seq("-deprecation:false", "-Xfatal-warnings")
