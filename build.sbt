name := "fm-sbt-s3-resolver"

organization := "com.frugalmechanic"

description := "SBT S3 Resolver Plugin"

licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

homepage := Some(url("https://github.com/frugalmechanic/sbt-s3-resolver"))

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

sbtPlugin := true

scriptedBufferLog := false

scriptedLaunchOpts ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value)

crossSbtVersions := Vector("0.13.16", "1.1.0")

val amazonSDKVersion = "1.11.416"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % amazonSDKVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % amazonSDKVersion,
  "org.apache.ivy" % "ivy" % "2.4.0"
)

// Tell the sbt-release plugin to use publishSigned
releasePublishArtifactsAction := PgpKeys.publishSigned.value

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT")) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  } else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}

// From: https://github.com/xerial/sbt-sonatype#using-with-sbt-release-plugin
import ReleaseTransformations._

// From: https://github.com/xerial/sbt-sonatype#using-with-sbt-release-plugin
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommandAndRemaining("^ test"),
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("^ publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <developers>
    <developer>
      <id>tim</id>
      <name>Tim Underwood</name>
      <email>tim@eluvio.com</email>
      <organization>Eluvio</organization>
      <organizationUrl>https://www.eluvio.com</organizationUrl>
    </developer>
  </developers>
  <scm>
      <connection>scm:git:git@github.com:frugalmechanic/sbt-s3-resolver.git</connection>
      <developerConnection>scm:git:git@github.com:frugalmechanic/sbt-s3-resolver.git</developerConnection>
      <url>git@github.com:frugalmechanic/sbt-s3-resolver.git</url>
  </scm>)

