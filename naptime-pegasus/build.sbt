name := "naptime-pegasus"

// Android also depends on this code, so it must be pure Java.

crossPaths := false

autoScalaLibrary := false

// For Android, we always publish 1.7, so the below must be enabled before calling `publish`.
// TODO(jbetz): Figure out a way to conditionally enable the below when publishing.

// fork in Compile := true

// javacOptions in Compile ++= Seq("-source", "1.7", "-target", "1.7")

// javaHome in Compile := Some(file("/Library/Java/JavaVirtualMachines/jdk1.7.0_75.jdk/Contents/Home"))

libraryDependencies ++= Seq(
  "com.linkedin.pegasus" % "data" % "2.6.0",
  junit,
  "com.novocode" % "junit-interface" % "0.11" % "test->default")

// Disable deprecation warnings entirely so they don't turn into errors.
scalacOptions := scalacOptions.value.filterNot(_.startsWith("-deprecation"))
scalacOptions ++= Seq("-deprecation:false", "-Xfatal-warnings")
