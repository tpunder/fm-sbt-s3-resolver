name := "fm-sbt-s3-resolver-scripted"
// Scripted tests are used in a different project to avoid issues with +test +publish/etc
crossScalaVersions := Vector("2.10.7", "2.12.15")
crossSbtVersions := Vector("1.2.8", "0.13.18")
publish / skip := true
enablePlugins(SbtPlugin, ScriptedPlugin)
scriptedBufferLog := false
scriptedLaunchOpts ++= Seq(
  "-Xmx1024M",
  "-Dplugin.version=" + version.value
)
