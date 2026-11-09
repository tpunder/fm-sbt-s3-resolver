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

val scala212 = "2.12.20"
val scala3 = "3.8.4"

scalaVersion := scala212

crossScalaVersions := Seq(scala212, scala3)

// sbt 1.x plugins are built with Scala 2.12 and sbt 2.x plugins with Scala 3
pluginCrossBuild / sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.12" => "1.5.8"
    case _      => "2.0.12"
  }
}

val amazonSDKVersion = "1.12.597"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % amazonSDKVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % amazonSDKVersion,
  "org.apache.ivy" % "ivy" % "2.4.0",
  "org.scalatest" %% "scalatest" % "3.2.10" % Test
)

publishTo := sonatypePublishToBundle.value

ThisBuild / versionScheme := Some("semver-spec")
