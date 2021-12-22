val Versions = new {
  val crossScalaVersions = Seq("2.10.7", "2.12.15")
  val ivy                = "2.5.0"
  val lmCoursier         = "2.0.10-1"
  val scalaTest          = "3.2.10"
  val amazonSDK          = "1.12.129"
}

// Because we're both a library and an sbt plugin, we use crossScalaVersions rather than crossSbtVersions for
// cross building. So you can use commands like +scripted.
crossScalaVersions := Versions.crossScalaVersions

sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.10" => "0.13.18"
    case "2.12" => "1.2.8"
  }
}

ThisBuild / organization := "com.frugalmechanic"
ThisBuild / licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("https://github.com/tpunder/fm-sbt-s3-resolver"))
ThisBuild / publishMavenStyle := true


ThisBuild / scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-language:implicitConversions")
//val unusedWarnings = Seq("-Ywarn-unused:imports")
//
//ThisBuild / scalacOptions ++= PartialFunction.condOpt(CrossVersion.partialVersion(scalaVersion.value)){
//  case Some((2, v)) if v >= 11 => unusedWarnings
//}.toList.flatten
//
//Seq(Compile, Test).flatMap(c =>
//  ThisBuild / scalacOptions in (c, console) --= unusedWarnings
//)

def hash(): String = sys.process.Process("git rev-parse HEAD").lineStream_!.head

ThisBuild / scalacOptions in (Compile, doc) ++= {
  Seq(
    "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
    "-doc-source-url", s"https://github.com/tpunder/fm-sbt-s3-resolver/tree/${hash()}€{FILE_PATH}.scala"
  )
}

ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

ThisBuild / Test / publishArtifact := false
ThisBuild / pomIncludeRepository := { _ => false }

ThisBuild / pomExtra := (
  <developers>
    <developer>
      <id>tim</id>
      <name>Tim Underwood</name>
      <email>timunderwood@gmail.com</email>
      <url>https://github.com/tpunder</url>
    </developer>
    <developer>
      <id>ericpeters</id>
      <name>Eric Peters</name>
      <email>eric@peters.org</email>
      <url>https://github.com/er1c</url>
    </developer>
  </developers>
  <scm>
      <connection>scm:git:git@github.com:tpunder/fm-sbt-s3-resolver.git</connection>
      <developerConnection>scm:git:git@github.com:tpunder/fm-sbt-s3-resolver.git</developerConnection>
      <url>git@github.com:tpunder/fm-sbt-s3-resolver.git</url>
  </scm>)

lazy val root = project
  .in(file("."))
  .settings(
    name := "fm-sbt-s3-resolver-root",

    // https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Note+about+sbt-release
    // crossScalaVersions must be set to Nil on the aggregating project
    crossScalaVersions := Nil,
    publish / skip := true,

    // don't use sbt-release's cross facility
    releaseCrossBuild := false,
    releaseProcess := {
      import ReleaseTransformations._
      Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        runClean,
        releaseStepCommandAndRemaining("+test"),
        setReleaseVersion,
        commitReleaseVersion,
        tagRelease,
        releaseStepCommandAndRemaining("+publishSigned"),
        setNextVersion,
        commitNextVersion,
        pushChanges
      )
    }
  )
  .aggregate(plugin)

lazy val plugin = project
  .in(file("./plugin"))
  .enablePlugins(BuildInfoPlugin, SbtPlugin)
  .settings(
    name := "fm-sbt-s3-resolver",
    description := "SBT S3 Resolver Plugin",
    buildInfoKeys := Seq[BuildInfoKey](name, organization, version, scalaVersion, scalaBinaryVersion, sbtVersion, sbtBinaryVersion),
    buildInfoPackage := "fm.sbt",
    scriptedBufferLog := false,
    crossScalaVersions := Versions.crossScalaVersions,
    // This overrides scriptedSbt
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.10" => "0.13.18"
        case "2.12" => "1.2.8"
      }
    },
    // Via https://github.com/sbt/sbt/issues/5049
    dependencyOverrides := {
      val v = scalaBinaryVersion.value match {
        case "2.10" => "0.13.18"
        case "2.12" => "1.2.8"
      }
      "org.scala-sbt" % "sbt" % v :: Nil
    },
    scripted := Def.inputTaskDyn {
      sys.error(s"Run scripted from the 'scripted' directory")
    }.evaluated
  )
  .dependsOn(coursierHandler)
  .aggregate(coursierHandler)

lazy val coursierHandler = project
  .in(file("./coursier-handler"))
  .settings(
    name := "fm-sbt-s3-resolver-coursier-handler",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-s3" % Versions.amazonSDK,
      "com.amazonaws" % "aws-java-sdk-sts" % Versions.amazonSDK,
      "org.apache.ivy" % "ivy" % Versions.ivy,
      "org.scalatest" %% "scalatest" % Versions.scalaTest % Test
    ),
    crossScalaVersions := Vector("2.10.7", "2.12.15", "2.13.7"),
    sbtVersion := (LocalRootProject / pluginCrossBuild / sbtVersion ).value,
    // additional custom protocol support added in 2.0.9 (https://github.com/coursier/sbt-coursier/commit/92e40c22256bea44d1e1befbef1cb2a627f8b155)
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 12 =>
          Seq(
            "io.get-coursier" %% "lm-coursier" % Versions.lmCoursier,
            "io.get-coursier" %% "lm-coursier-shaded" % Versions.lmCoursier,
          )
        case _ => Nil
      }
    }
  )
