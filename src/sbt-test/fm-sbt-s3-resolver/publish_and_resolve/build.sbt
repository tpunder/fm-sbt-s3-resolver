
// This could be pulled from an environment variable or Java system properties
val s3Bucket: String = "fm-sbt-s3-resolver-example-bucket"

// By default we use a random UUID to prevent conflicts across test runs
// if we are using an actual S3 bucket or another shared S3 compatible storage.
val s3Directory: String = java.util.UUID.randomUUID().toString()

// This shouldn't matter since we aren't testing the Scala code.
// Note: Cannot be named "scalaVersion" since that conflicts with SBT
val scalaVersionForCompile: String = "2.13.7"

lazy val lib = (project in file("example-lib"))
  .settings(
    name := "example-lib",
    organization := "com.example",
    version := "1.0.0",
    scalaVersion := scalaVersionForCompile,
    publishMavenStyle := true,
    publishTo := Some(s"S3 Test Repository - $s3Bucket" at s"s3://$s3Bucket/$s3Directory")
  )

lazy val app = (project in file("example-app"))
  .settings(
    name := "example-app",
    scalaVersion := scalaVersionForCompile,
    // Note: We configure this as the only resolver by overwriting any default resolvers
    resolvers := Seq(s"S3 Test Repository - $s3Bucket" at s"s3://$s3Bucket/$s3Directory"),
    libraryDependencies += "com.example" %% "example-lib" % "1.0.0"
  )
