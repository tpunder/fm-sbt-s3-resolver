publishTo in ThisBuild := Some("Custom Releases" at "s3://maven.custom/releases/")
libraryDependencies in ThisBuild += "org.scalatest" %% "scalatest" % "3.2.10" % Test

lazy val root = project
  .in(file("."))
  .settings(publishArtifact := false)
  .aggregate(mavenStyle, ivyStyle)

lazy val mavenStyle = project
  .in(file("maven-style"))
  .settings(
    version := "0.1.0",
    publishMavenStyle := true,
    (sourceDirectory in Compile) := sourceDirectory.in(LocalRootProject, Compile).value,
    (sourceDirectory in Test)  := sourceDirectory.in(LocalRootProject, Compile).value
  )

lazy val ivyStyle = project
  .in(file("ivy-style"))
  .settings(
    version := "0.1.0",
    publishMavenStyle := false,
    (sourceDirectory in Compile) := sourceDirectory.in(LocalRootProject, Compile).value,
    (sourceDirectory in Test)  := sourceDirectory.in(LocalRootProject, Compile).value
  )