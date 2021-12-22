libraryDependencies ++= Seq(
  "com.dimafeng" %% "testcontainers-scala-localstack" % "0.39.12",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.12.129",
  "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
)