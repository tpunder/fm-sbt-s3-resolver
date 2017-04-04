name := "fm-sbt-s3-resolver"

organization := "com.frugalmechanic"

version := "0.10.0-SNAPSHOT"

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
  "-opt:l:project"
) else Nil)

sbtPlugin := true

EclipseKeys.withSource := true

// Don't use the default "target" directory (which is what SBT uses)
EclipseKeys.eclipseOutput := Some(".target")

val amazonSDKVersion = "1.10.35"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % amazonSDKVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % amazonSDKVersion,
  "joda-time" % "joda-time" % "2.9.1" force(), //This is to fix https://github.com/aws/aws-sdk-java/issues/484
  "org.apache.ivy" % "ivy" % "2.3.0"
)

publishMavenStyle := true

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) 
    Some("snapshots" at nexus + "content/repositories/snapshots") 
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <developers>
    <developer>
      <id>tim</id>
      <name>Tim Underwood</name>
      <email>tim@eluvio.com</email>
      <organization>Frugal Mechanic</organization>
      <organizationUrl>http://frugalmechanic.com</organizationUrl>
    </developer>
  </developers>
  <scm>
      <connection>scm:git:git@github.com:frugalmechanic/sbt-s3-resolver.git</connection>
      <developerConnection>scm:git:git@github.com:frugalmechanic/sbt-s3-resolver.git</developerConnection>
      <url>git@github.com:frugalmechanic/sbt-s3-resolver.git</url>
  </scm>)

