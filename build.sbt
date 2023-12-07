name := "fm-sbt-s3-resolver"

description := "SBT S3 Resolver Plugin"

scalacOptions := Seq(
  "-encoding", "UTF-8",
  "-unchecked",
  "-deprecation",
  "-language:implicitConversions",
  "-feature",
  "-Xlint"
) ++ (if (scalaVersion.value.startsWith("2.11")) Seq(
  // Scala 2.11 specific compiler flags
  "-Ywarn-unused-import"
) else Nil) ++ (if (scalaVersion.value.startsWith("2.12")) Seq(
  // Scala 2.12 specific compiler flags
  // NOTE: These are currently broken on Scala <= 2.12.6 when using Java 9+ (will hopefully be fixed in 2.12.7)
  //"-opt:l:inline",
  //"-opt-inline-from:<sources>",
) else Nil)

enablePlugins(SbtPlugin)

scriptedBufferLog := false

// Don't depend on publishLocal when running "scripted". This allows us to run
// "^publishLocal" for the crossSbtVersions and then run "scripted" on arbitrary
// SBT versions for testing.
scriptedDependencies := {}

scriptedLaunchOpts ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value)

crossSbtVersions := Vector("0.13.18", "1.1.0")

val amazonSDKVersion = "1.12.597"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % amazonSDKVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % amazonSDKVersion,
  "org.apache.ivy" % "ivy" % "2.5.0",
  // Uncomment, and rename "src/main/resources/log4j.properties.debug" to "log4j.properties" to enable wire debugging
  //"log4j" % "log4j" % "1.2.17",
  "org.scalatest" %% "scalatest" % "3.2.10" % Test
)

publishTo := sonatypePublishToBundle.value

ThisBuild / versionScheme := Some("semver-spec")
