/*
 * Copyright 2014 Frugal Mechanic (http://frugalmechanic.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fm.sbt.s3

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{ListObjectsRequest, ObjectListing, S3Object, S3ObjectInputStream}
import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import scala.annotation.tailrec
import scala.xml.NodeSeq
import scala.collection.JavaConverters._

/**
 * This creates a `maven-metadata.xml` based upon the s3 directory listings
 */
object S3MavenMetadata {

  private val sha1MessageDigest = java.security.MessageDigest.getInstance("SHA-1")

  def getSha1(client: AmazonS3, bucketName: String, path: String): Option[String] = {
    getXml(client, bucketName, path).map { contents =>
      sha1MessageDigest
        .digest(contents.getBytes(StandardCharsets.UTF_8))
        .map( b => "%02x".format(b) )
        .mkString
    }
  }

  /**
   * This gets s3 object listings for the specified path, and returns back a generated maven-metadata.xml file
   * @param client
   * @param bucketName
   * @param path
   * @param logger
   * @return
   */
  def getXml(client: AmazonS3, bucketName: String, path: String): Option[String] = {
    val bucketPath = path
      .stripPrefix("/")
      .stripSuffix("maven-metadata.xml")
      .stripSuffix("maven-metadata.xml.sha1")
      .stripSuffix("/") + "/"

    val dirListings: Seq[ObjectListing] = getObjectListings(client, bucketName, bucketPath)
    val lastUpdated: Date = dirListings
      .flatMap{ _.getObjectSummaries.asScala.map{ _.getLastModified } }
      .sorted
      .headOption
      .getOrElse(new Date)

    val versions: Seq[String] = for {
      obj <- dirListings
      key <- obj.getCommonPrefixes.asScala
    } yield {
      assert(key.startsWith(bucketPath))
      key.stripPrefix(bucketPath).stripSuffix("/")
    }

    //if (versions.isEmpty) logger.debug(s"[S3ResolverPlugin] S3MavenMetadata.getXml($bucketPath) no versions found.")

    for {
      latestVersion <- versions.headOption
      artifactName = {
        val s = bucketPath.stripSuffix("/")
        val idx = s.lastIndexOf('/')
        s.drop(idx+1)
      }
      groupId <- getGroupId(client, bucketName, bucketPath, latestVersion, artifactName)
    } yield {
      createXml(
        groupId = groupId,
        artifactName = artifactName,
        latestVersion = latestVersion,
        versions = versions.map{ v => <version>{v}</version> },
        lastUpdated = lastUpdated
      )
    }
  }

  @tailrec private def getObjectListingsImpl(client: AmazonS3, bucketName: String, objectListing: ObjectListing, accum: Seq[ObjectListing] = Nil): Seq[ObjectListing] = {
    val updatedAccum = accum :+ objectListing
    if (!objectListing.isTruncated) updatedAccum
    else getObjectListingsImpl(client, bucketName, client.listNextBatchOfObjects(objectListing), updatedAccum)
  }

  private def getObjectListings(client: AmazonS3, bucketName: String, bucketPath: String): Seq[ObjectListing] = {
    tryS3(bucketPath){
      // "/" as a delimiter to be returned only entries in the first level (no recursion),
      // with (pseudo) sub-directories indeed ending with a "/"
      val req = new ListObjectsRequest(bucketName, bucketPath, null, "/", null)
      getObjectListingsImpl(client, bucketName, client.listObjects(req))
    }.getOrElse(Nil)
  }

  private def tryS3[T](path: String)(f: => T): Option[T] = {
    try {
      Option(f)
    } catch {
      case ex: AmazonServiceException if ex.getStatusCode == 404 =>
        //logger.warn(s"[S3ResolverPlugin] S3MavenMetadata.tryS3($path) not found.")
        None

    }
  }

  private def getGroupId(
    client: AmazonS3,
    bucketName: String,
    path: String,
    latestVersion: String,
    artifactName: String
  ): Option[String] = {
    val artifactPom = artifactName + "-" + latestVersion + ".pom"
    val key = path + latestVersion + "/" + artifactPom

    val pomObject: S3Object = tryS3(key) {
      client.getObject(bucketName, key)
    }.getOrElse(throw new IllegalStateException("[S3ResolverPlugin] S3MavenMetadata.getGroupId() - could not find pom for artifact: " + artifactName + ", version: " + latestVersion + " (s3 path: " + key + ")"))

    var is: S3ObjectInputStream = null
    val pomContent = try {
      is = pomObject.getObjectContent
      inputStreamToString(is)
    } finally {
      Option(is).foreach { _.close() }
      pomObject.close()
    }

    try {
      val pomXML = scala.xml.XML.loadString(pomContent)
      Some((pomXML \ "groupId").text)
    } catch {
      case ex: IllegalArgumentException =>
        //logger.error("[S3ResolverPlugin] S3MavenMetadata.getGroupId() - artifact pom: " +artifactPom + " did not contain 'groupId': " + ex.getMessage)
        None
    }
  }

  private def createXml(
    groupId: String,
    artifactName: String,
    latestVersion: String,
    versions: NodeSeq,
    lastUpdated: java.util.Date
  ): String = {
    <metadata modelVersion="1.1.0">
      <groupId>{groupId}</groupId>
      <artifactId>{artifactName}</artifactId>
      <versioning>
        <latest>{latestVersion}</latest>
        <release>{latestVersion}</release>
        <versions>
          {versions}
        </versions>
        <lastUpdated>{new SimpleDateFormat("yyyyMMddHHmmss").format(lastUpdated)}</lastUpdated>
      </versioning>
    </metadata>
  }.toString()

  private def inputStreamToString(is: InputStream): String = {
    val inputStreamReader = new InputStreamReader(is)
    val bufferedReader = new BufferedReader(inputStreamReader)
    bufferedReader.readLine()
    Iterator.continually{ bufferedReader.readLine }.takeWhile(_ != null).mkString
  }
}