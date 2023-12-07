{
  val pluginVersion = Option(System.getProperty("plugin.version")) getOrElse {
    throw new RuntimeException("The system property 'plugin.version' is not defined. Specify this property using the scriptedLaunchOpts -D.")
  }
  addSbtPlugin("com.frugalmechanic" % "fm-sbt-s3-resolver" % pluginVersion)
}

libraryDependencies ++= Seq(
  // Java API for coursier for easier compat with 2.10 and 2.12
  "io.get-coursier" % "interface" % "1.0.6"
)
