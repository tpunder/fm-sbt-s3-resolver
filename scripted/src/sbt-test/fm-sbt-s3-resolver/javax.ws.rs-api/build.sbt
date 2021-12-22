resolvers += "Custom Releases" at "s3://maven.custom/releases/"
//csrResolvers += "Custom Releases" at "s3://maven.custom/releases/"

libraryDependencies ++= Seq(
  "javax.ws.rs" % "javax.ws.rs-api" % "2.1" artifacts Artifact("javax.ws.rs-api", "", "jar")
)
