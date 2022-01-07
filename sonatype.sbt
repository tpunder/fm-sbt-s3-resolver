
//
// For sbt-sonatype
//
organization := "com.frugalmechanic"

publishMavenStyle := true

licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

import xerial.sbt.Sonatype._
sonatypeProjectHosting := Some(GitHubHosting("tpunder", "fm-sbt-s3-resolver", "Tim Underwood", "timunderwood@gmail.com"))

//
// For sbt-pgp
//
usePgpKeyHex("AB8A8ACD374B4E2FF823BA35553D700D8BD8EF54")

//
// For sbt-release
//
import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommandAndRemaining("^ test"),
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("^ publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
