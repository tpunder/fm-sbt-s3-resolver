scalaVersion := "2.13.7"

resolvers ++= Seq(
  "Custom Releases" at "s3://maven.custom/releases/"
)

libraryDependencies ++= Seq(
  "org.scala-steward" %% "scala-steward-core" % "0.13.0",
  "org.typelevel" %% "cats-effect" % "3.3.0",
  "org.scalatest" %% "scalatest" % "3.2.10" % Test
) 
