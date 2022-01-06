sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("com.frugalmechanic" % "fm-sbt-s3-resolver" % x)
  case _ => sys.error("""|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}
