package fm.sbt

import _root_.sbt._
import java.util.Locale
import lmcoursier.CoursierConfiguration
import scala.reflect.runtime.universe
import scala.util.Try

object S3ResolverPluginCompat {
  object Keys extends S3ResolverPluginCompat.Keys

  trait Keys {
    /** This provides a shim for pre-sbt 1.3.x+ tests */
    private[S3ResolverPluginCompat] lazy val sbtShimCsrResolvers: TaskKey[Seq[Resolver]] =             TaskKey[Seq[Resolver]]("sbtShimCsrResolvers", "Provide a sbt shim for pre-coursier versions")
    private[S3ResolverPluginCompat] lazy val sbtShimCsrSbtResolvers: TaskKey[Seq[Resolver]] =          TaskKey[Seq[Resolver]]("sbtShimCsrSbtResolvers", "Provide a sbt shim for pre-coursier versions")
    private[S3ResolverPluginCompat] lazy val sbtShimSbtResolvers: SettingKey[Seq[Resolver]] =          SettingKey[Seq[Resolver]]("sbtShimSbtResolvers", "Provide a sbt shim for pre-coursier versions")(implicitly, sbt.util.NoJsonWriter())
    private[S3ResolverPluginCompat] lazy val sbtShimUseCoursier: SettingKey[Boolean] =                 SettingKey[Boolean]("sbtShimUseCoursier", "Provide a sbt shim for pre-coursier versions")(implicitly, sbt.util.NoJsonWriter())
    private[S3ResolverPluginCompat] lazy val sbtShimCsrConfiguration: TaskKey[CoursierConfiguration] = TaskKey[CoursierConfiguration]("sbtCsrConfiguration", "Provide a sbt shim for pre-coursier versions")
    private[S3ResolverPluginCompat] lazy val sbtShimCsrCacheDirectory: SettingKey[File] =              SettingKey[File]("sbtShimCsrCacheDirectory", "Provide a sbt shim for pre-coursier versions")(implicitly, sbt.util.NoJsonWriter())

    lazy val csrResolvers: TaskKey[Seq[Resolver]] = loadIfExists(
      fullyQualifiedName = "sbt.Keys.csrResolvers",
      args = None,
      default = sbtShimCsrResolvers
    )

    lazy val csrSbtResolvers: TaskKey[Seq[Resolver]] = loadIfExists(
      fullyQualifiedName = "sbt.Keys.csrSbtResolvers",
      args = None,
      default = sbtShimCsrSbtResolvers
    )

    lazy val sbtResolvers: SettingKey[Seq[Resolver]] = loadIfExists(
      fullyQualifiedName = "sbt.Keys.sbtResolvers",
      args = None,
      default = sbtShimSbtResolvers
    )

    lazy val useCoursier: SettingKey[Boolean] = loadIfExists(
      fullyQualifiedName = "sbt.Keys.useCoursier",
      args = None,
      default = sbtShimUseCoursier
    )

    lazy val csrConfiguration: TaskKey[CoursierConfiguration] = loadIfExists(
      fullyQualifiedName = "sbt.Keys.csrConfiguration",
      args = None,
      default = sbtShimCsrConfiguration
    )

    lazy val csrCacheDirectory: SettingKey[File] = loadIfExists(
      fullyQualifiedName = "sbt.Keys.csrCacheDirectory",
      args = None,
      default = sbtShimCsrCacheDirectory
    )
  }

  /**
   * From: https://github.com/etspaceman/kinesis-mock/blob/a7d94e74d367b74479f565fa9c5b5692e4d1b8fd/project/BloopSettings.scala#L8
   * License: MIT
   *
   * MIT License
   *
   * Copyright (c) 2021 Eric Meisel
   *
   * Permission is hereby granted, free of charge, to any person obtaining a copy
   * of this software and associated documentation files (the "Software"), to deal
   * in the Software without restriction, including without limitation the rights
   * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   * copies of the Software, and to permit persons to whom the Software is
   * furnished to do so, subject to the following conditions:
   *
   * The above copyright notice and this permission notice shall be included in all
   * copies or substantial portions of the Software.
   * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
   * SOFTWARE.
   *
   * Example Usage:
   * {{{
   * val default: Seq[Def.Setting[_]] = loadIfExists(
   *   fullyQualifiedName = "bloop.integrations.sbt.BloopDefaults.configSettings",
   *   args = Some(Nil),
   *   default = Seq.empty[Def.Setting[_]]
   * )
   * }}}
   *
   * @param fullyQualifiedName
   * @param args
   * @param default
   * @tparam T
   * @return
   */
  private def loadIfExists[T](
    fullyQualifiedName: String,
    args: Option[Seq[Any]],
    default: => T
  ): T = {
    val tokens     = fullyQualifiedName.split('.')
    val memberName = tokens.last
    val moduleName = tokens.take(tokens.length - 1).mkString(".")

    val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)
    val value = Try(runtimeMirror.staticModule(moduleName)).map { module =>
      val obj            = runtimeMirror.reflectModule(module)
      val instance       = obj.instance
      val instanceMirror = runtimeMirror.reflect(instance)
      val member =
        instanceMirror.symbol.info.member(universe.TermName(memberName))
      args
        .fold(instanceMirror.reflectField(member.asTerm).get)(args => instanceMirror.reflectMethod(member.asMethod)(args: _*))
        .asInstanceOf[T]
    }
    value.getOrElse(default)
  }

  private object RichSbtUtil {
    private lazy val isMac: Boolean =
      System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("mac")
  }

  private implicit class RichSbtUtil(val util: Util.type) extends AnyVal {
    def isMac: Boolean = RichSbtUtil.isMac
  }

  private def home: File = file(sys.props("user.home"))

  private def defaultCacheLocation: File = {
    def propCacheDir: Option[File] = sys.props.get("coursier.cache").map(file)
    def envCacheDir: Option[File] = sys.env.get("COURSIER_CACHE").map(file)
    def windowsCacheDir: Option[File] =
      sys.env.get("LOCALAPPDATA") match {
        case Some(app) if Util.isWindows => Some(file(app) / "Coursier" / "v1")
        case _                           => None
      }
    def macCacheDir: Option[File] =
      if (Util.isMac) {
        Some(home / "Library" / "Caches" / "Coursier" / "v1")
      } else {
        None
      }
    def linuxCache: File =
      sys.env.get("XDG_CACHE_HOME") match {
        case Some(cache) => file(cache) / "coursier" / "v1"
        case _           => home / ".cache" / "coursier" / "v1"
      }
    def baseCache: File =
      propCacheDir
        .orElse(envCacheDir)
        .orElse(windowsCacheDir)
        .orElse(macCacheDir)
        .getOrElse(linuxCache)

    baseCache.getAbsoluteFile
  }
}

trait S3ResolverPluginCompat {
  val Logger = _root_.sbt.util.Logger
  val Using = _root_.sbt.io.Using

  // Use this to set the appropriate S3 Handler dependency
  protected def s3PluginVersion: String
  protected def s3PluginGroupId: String

  import S3ResolverPluginCompat.Keys._

  protected def compatProjectSettings: Seq[Setting[_]] = Seq(
    // s3 resolvers in `resolvers` task don't always get set in coursier keys in
    // newer sbt versions, so setup a cross-sbt 0.13.x-compatible shim and manually
    // put the s3 resolvers into the appropriate csrResolvers/csrSbtResolvers values

    // Initialize our shim and use found java-reflection value or shim value
    sbtShimSbtResolvers := _root_.sbt.Keys.resolvers.value,
    sbtResolvers := {
      if (sbtHasCoursier(_root_.sbt.Keys.sbtVersion.value)) sbtResolvers.value
      else sbtShimSbtResolvers.value
    },
    sbtShimUseCoursier := false,
    useCoursier := {
      if (sbtHasCoursier(_root_.sbt.Keys.sbtVersion.value)) useCoursier.value
      else sbtShimUseCoursier.value
    },
    sbtShimCsrResolvers := Nil,
    csrResolvers := Def.taskDyn {
      val v = if (sbtHasCoursier(_root_.sbt.Keys.sbtVersion.value)) csrResolvers.value else sbtShimCsrResolvers.value
      Def.task {
        v
      }
    }.value,
    csrResolvers ++= _root_.sbt.Keys.resolvers.value,
    sbtShimCsrSbtResolvers := Nil,
    csrSbtResolvers := Def.taskDyn {
      val v = if (sbtHasCoursier(_root_.sbt.Keys.sbtVersion.value)) csrSbtResolvers.value else sbtShimCsrSbtResolvers.value
      Def.task {
        v
      }
    }.value,
    csrSbtResolvers ++= sbtResolvers.value,
    sbtShimCsrConfiguration := CoursierConfiguration(),
    csrConfiguration := Def.taskDyn {
      val v =
        if (sbtHasCoursier(_root_.sbt.Keys.sbtVersion.value))
          csrConfiguration.value
        else
          sbtShimCsrConfiguration.value
      Def.task {
        v
      }
    }.value,
    csrConfiguration := Def.taskDyn {
      val v =
        if (sbtHasCoursierProtocolHandlerDependencies(_root_.sbt.Keys.sbtVersion.value)) {
          val s3Plugin = s3PluginGroupId %% "fm-sbt-s3-resolver-coursier-handler" % s3PluginVersion
          csrConfiguration.value.withProtocolHandlerDependencies(Seq(s3Plugin))
        } else {
          sbtShimCsrConfiguration.value
        }
      Def.task {
        v
      }
    }.value,
    sbtShimCsrCacheDirectory := S3ResolverPluginCompat.defaultCacheLocation,
    csrCacheDirectory := {
      if (sbtHasCoursier(_root_.sbt.Keys.sbtVersion.value)) csrCacheDirectory.value
      else sbtShimCsrCacheDirectory.value
    }
  )

  private def sbtHasCoursier(sbtVersion: String): Boolean = CrossVersion.partialVersion(sbtVersion) match {
    case Some((1, minor)) if minor >= 3 => true
    case Some((1, minor)) if minor < 3  => false
    case Some((0, 13))                  => false
    case _                              => sys.error(s"Unsupported sbtVersion: ${sbtVersion}")
  }

  private def sbtHasCoursierProtocolHandlerDependencies(sbtVersion: String): Boolean = CrossVersion.partialVersion(sbtVersion) match {
    case Some((1, minor)) if minor >= 6 => true
    case Some((1, minor)) if minor < 6  => false
    case Some((0, 13))                  => false
    case _                              => sys.error(s"Unsupported sbtVersion: ${sbtVersion}")
  }
}