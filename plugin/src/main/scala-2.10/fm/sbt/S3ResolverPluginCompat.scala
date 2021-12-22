package fm.sbt

import _root_.sbt._

object S3ResolverPluginCompat {
  object Keys extends S3ResolverPluginCompat.Keys
  trait Keys {}
}

trait S3ResolverPluginCompat {
  import S3ResolverPluginCompat.Keys._

  protected def s3PluginVersion: String
  protected def s3PluginGroupId: String

  protected def compatProjectSettings: Seq[Setting[_]] = Nil
  val Logger = _root_.sbt.Logger
  val Using = _root_.sbt.Using
}