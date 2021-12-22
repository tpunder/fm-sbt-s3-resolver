resolvers in ThisBuild += "Custom Releases" at "s3://maven.custom/releases/"

lazy val mavenStyle = project
  .in(file("maven-style"))
  .settings(
    version := "0.1.0",
    scalaVersion := "2.10.6",
    publishMavenStyle := true,
    TaskKey[Unit]("check") := {
      assert(resolvers.value.filter(_.name.equals("Custom Releases")).head.isInstanceOf[MavenRepository],
        "A maven style project should have a maven repository as it default resolver"
      )
    }
  )

lazy val ivyStyle = project
  .in(file("ivy-style"))
  .settings(
    version := "0.1.0",
    publishMavenStyle := false,
    TaskKey[Unit]("check") := {
      assert(resolvers.value.filter(_.name.equals("Custom Releases")).head.isInstanceOf[MavenRepository],
        "A maven style project should have a maven repository as it default resolver"
      )
    }
  )