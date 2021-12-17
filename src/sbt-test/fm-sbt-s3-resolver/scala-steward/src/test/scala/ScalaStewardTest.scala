package scalasteward

import cats.effect.unsafe.IORuntime
import cats.effect._
import cats.syntax.all._
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Resolver, Scope}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.ExecutionContext

object ScalaStewardTest {
  private val s3Resolver = Resolver.MavenRepository("Custom Releases", "s3://maven.custom/releases/", None)
}

final class ScalaStewardTest extends AsyncFunSuite with Matchers {
  import ScalaStewardTest._
  implicit def ioRuntime: IORuntime = IORuntime.global
  implicit def unsafeLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]

/*
[info] [info] - getVersions: library *** FAILED ***
[info] [info]   java.lang.Throwable: not found: s3://maven.custom/releases/javax/ws/rs/javax.ws.rs-api/maven-metadata.xml
[info] [info]   at org.scalasteward.core.coursier.CoursierAlg$$anon$1.$anonfun$getVersions$5(CoursierAlg.scala:95)
[info] [info]   at cats.syntax.FlatMapOps$.$anonfun$$greater$greater$1(flatMap.scala:33)
[info] [info]   at debug @ org.scalasteward.core.coursier.CoursierAlg$$anon$1.$anonfun$getVersions$3(CoursierAlg.scala:95)
[info] [info]   at debug @ org.scalasteward.core.coursier.CoursierAlg$$anon$1.$anonfun$getVersions$3(CoursierAlg.scala:95)
[info] [info]   at >>$extension @ org.scalasteward.core.coursier.CoursierAlg$$anon$1.$anonfun$getVersions$3(CoursierAlg.scala:95)
[info] [info]   at blocking @ org.scalasteward.core.coursier.package$$anon$1.schedule(package.scala:49)
[info] [info]   at blocking @ org.scalasteward.core.coursier.package$$anon$1.schedule(package.scala:49)
[info] [info]   at delay @ org.scalasteward.core.coursier.package$$anon$1.delay(package.scala:31)
[info] [info]   at flatMap @ org.scalasteward.core.coursier.package$$anon$1.bind(package.scala:46)
[info] [info]   at flatMap @ org.scalasteward.core.coursier.package$$anon$1.bind(package.scala:46)
*/
  test("getVersions: library") {
    val artifactId = ArtifactId("javax.ws.rs-api")
    val dep = Dependency(GroupId("javax.ws.rs"), artifactId, "2.1")
    val coursierAlg: CoursierAlg[IO] = CoursierAlg.create[IO]
    val obtained = coursierAlg.getVersions(dep, s3Resolver).unsafeRunSync()
    obtained shouldBe Seq("2.1")
  }
}

