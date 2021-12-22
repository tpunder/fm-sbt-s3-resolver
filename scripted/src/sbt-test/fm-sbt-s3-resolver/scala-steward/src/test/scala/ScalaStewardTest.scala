package scalasteward

import cats.effect.unsafe.IORuntime
import cats.effect._
import cats.syntax.all._
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Resolver, Scope, Version}
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

  test("getVersions: library") {
    val artifactId = ArtifactId("javax.ws.rs-api")
    val dep = Dependency(GroupId("javax.ws.rs"), artifactId, "2.1")
    val coursierAlg: CoursierAlg[IO] = CoursierAlg.create[IO]
    val obtained = coursierAlg.getVersions(dep, s3Resolver).unsafeRunSync()
    obtained shouldBe List(Version("2.1"))
  }
}
