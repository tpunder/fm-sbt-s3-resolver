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
    publishTo := Some(s"S3 Test Repository - $s3Bucket" at s"s3://$s3Bucket/$s3Directory"),
    TaskKey[Unit]("checkCoursierVersions") := {
      import coursierapi._
      import java.time.LocalDateTime
      import java.time.temporal.ChronoUnit

      val s3Repo = MavenRepository.of(s"s3://$s3Bucket/$s3Directory")
      val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

      val csVersions: Versions =
        Versions
          .create()
          .withModule(coursierapi.Module.of("com.example", "example-lib_2.13"))
          .withRepositories(s3Repo)

      val res: VersionListing =
        csVersions
          .versions()
          .getMergedListings()

      val expectedMergedListing =
        VersionListing.of(
          "1.0.0",
          "1.0.0",
          java.util.Arrays.asList[String]("1.0.0"),
          now
        )

      def assertEquals[T](expected: T, actual: T): Unit = {
        assert(expected == actual, s"$expected was not equal to $actual")
      }

      assertEquals(expectedMergedListing.getLatest, res.getLatest)
      assertEquals(expectedMergedListing.getRelease, res.getRelease)
      assertEquals(expectedMergedListing.getAvailable, res.getAvailable)
      assert(res.getLastUpdated == now || res.getLastUpdated.isAfter(now), s"${res.getLastUpdated} was not after $now")
    }
  )

lazy val app = (project in file("example-app"))
  .settings(
    name := "example-app",
    scalaVersion := scalaVersionForCompile,
    // Note: We configure this as the only resolver by overwriting any default resolvers
    resolvers := Seq(s"S3 Test Repository - $s3Bucket" at s"s3://$s3Bucket/$s3Directory"),
    libraryDependencies += "com.example" %% "example-lib" % "1.0.0"
  )
