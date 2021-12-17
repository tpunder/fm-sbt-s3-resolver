package fm.sbt

import org.scalatest.PrivateMethodTester
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

final class S3URLHandlerTest extends AnyFunSuite with Matchers with PrivateMethodTester {
  test("getDNSAliases") {
    val getDNSAliasesMethod = PrivateMethod[Seq[String]]('getDNSAliases)
    S3URLHandler invokePrivate getDNSAliasesMethod("maven.custom") shouldBe Seq("s3-w.us-east-1.amazonaws.com.", "s3-1-w.amazonaws.com.")
    S3URLHandler invokePrivate getDNSAliasesMethod("acloudguru1234") shouldBe Seq("s3-us-west-2-w.amazonaws.com.")
    S3URLHandler invokePrivate getDNSAliasesMethod("") shouldBe Nil
  }

  test("getRegionNameFromDNS") {
    S3URLHandler.getRegionNameFromDNS("maven.custom") shouldBe Some("us-east-1")
    S3URLHandler.getRegionNameFromDNS("acloudguru1234") shouldBe Some("us-west-2")
  }
}

