package fm.sbt

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

final class S3URLHandlerTest extends AnyFunSuite with Matchers {
  test("getDNSAliasesForHost - s3-1-w.amazonaws.com. should have a CNAME to s3-w.us-east-1.amazonaws.com.") {
    S3URLHandler.getDNSAliasesForHost("s3-1-w.amazonaws.com") shouldBe Seq("s3-w.us-east-1.amazonaws.com.")
  }

  test("getDNSAliasesForHost - invalid") {
    S3URLHandler.getDNSAliasesForHost("test.invalid.") shouldBe Nil
  }

  test("getDNSAliasesForHost - empty") {
    S3URLHandler.getDNSAliasesForHost("") shouldBe Nil
  }

  Vector(
    // Bucket Name                                     , Expected Aliases                                             , Expected Region
    ("fm-sbt-s3-resolver-example-bucket"               , Seq("s3-us-west-2-w.amazonaws.com.")                         , Some("us-west-2")),
    ("fm-sbt-s3-resolver-example-bucket-us-east-1"     , Seq("s3-w.us-east-1.amazonaws.com.", "s3-1-w.amazonaws.com."), Some("us-east-1")),
    ("fm-sbt-s3-resolver-example-bucket-us-west-2"     , Seq("s3-us-west-2-w.amazonaws.com.")                         , Some("us-west-2")),
    ("fm-sbt-s3-resolver-example-bucket-eu-central-1"  , Seq("s3-w.eu-central-1.amazonaws.com.")                      , Some("eu-central-1")),
    ("fm-sbt-s3-resolver-example-bucket-ap-northeast-1", Seq("s3-ap-northeast-1-w.amazonaws.com.")                    , Some("ap-northeast-1")),
    (""                                                , Nil                                                          , None)
  ).foreach { case (bucket: String, aliases: Seq[String], region: Option[String]) =>
    test(s"getDNSAliasesForBucket - $bucket") {
      S3URLHandler.getDNSAliasesForBucket(bucket) shouldBe aliases
    }

    test(s"getRegionNameFromDNS - Bucket: $bucket | Aliases: ${aliases.mkString(", ")} | Region: ${region.getOrElse("<none>")}") {
      S3URLHandler.getRegionNameFromDNS(bucket) shouldBe region
    }
  }
}