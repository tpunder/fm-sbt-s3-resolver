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
package fm.sbt

import com.amazonaws.SDKGlobalConfiguration.{ACCESS_KEY_SYSTEM_PROPERTY, SECRET_KEY_SYSTEM_PROPERTY}
import com.amazonaws.SDKGlobalConfiguration.{ACCESS_KEY_ENV_VAR, SECRET_KEY_ENV_VAR}
import com.amazonaws.auth._
import com.amazonaws.regions.{Region, Regions, RegionUtils}
import com.amazonaws.services.s3.{AmazonS3Client, AmazonS3URI}
import com.amazonaws.services.s3.model.{AmazonS3Exception, GetObjectRequest, ObjectMetadata, PutObjectResult, S3Object}
import org.apache.ivy.util.{CopyProgressEvent, CopyProgressListener, Message, FileUtil}
import org.apache.ivy.util.url.URLHandler
import java.io.{File, InputStream}
import java.net.{InetAddress, URI, URL}
import scala.util.matching.Regex
import scala.util.Try

object S3URLHandler {
  // This is for matching region names in URLs or host names
  private val RegionMatcher: Regex = Regions.values().map{ _.getName }.sortBy{ -1 * _.length }.mkString("|").r
  
  private class S3URLInfo(available: Boolean, contentLength: Long, lastModified: Long) extends URLHandler.URLInfo(available, contentLength, lastModified)
  
  private class BucketSpecificSystemPropertiesCredentialsProvider(bucket: String) extends BucketSpecificCredentialsProvider(bucket) {
    
    def AccessKeyName: String = ACCESS_KEY_SYSTEM_PROPERTY
    def SecretKeyName: String = SECRET_KEY_SYSTEM_PROPERTY

    protected def getProp(names: String*): String = names.map{ System.getProperty }.flatMap{ Option(_) }.head.trim
  }
  
  private class BucketSpecificEnvironmentVariableCredentialsProvider(bucket: String) extends BucketSpecificCredentialsProvider(bucket) {
    def AccessKeyName: String = ACCESS_KEY_ENV_VAR
    def SecretKeyName: String = SECRET_KEY_ENV_VAR
    
    protected def getProp(names: String*): String = names.map{ cleanName }.map{ System.getenv }.flatMap{ Option(_) }.head.trim
    
    private def cleanName(s: String): String = s.toUpperCase.replace('-','_').replace('.','_').replaceAll("[^A-Z0-9_]", "")
  }
  
  private abstract class BucketSpecificCredentialsProvider(bucket: String) extends AWSCredentialsProvider {
    def AccessKeyName: String
    def SecretKeyName: String
    
    def getCredentials(): AWSCredentials = {
      val accessKey: String = getProp(s"${AccessKeyName}.${bucket}", s"${bucket}.${AccessKeyName}")
      val secretKey: String = getProp(s"${SecretKeyName}.${bucket}", s"${bucket}.${SecretKeyName}")
      
      new BasicAWSCredentials(accessKey, secretKey)
    }
    
    def refresh(): Unit = {}
    
    // This should throw an exception if the value is missing
    protected def getProp(names: String*): String
  }
}

/**
 * This implements the Ivy URLHandler
 */
final class S3URLHandler extends URLHandler {
  import URLHandler.{UNAVAILABLE, URLInfo}
  import S3URLHandler._
  
  def isReachable(url: URL): Boolean = getURLInfo(url).isReachable
  def isReachable(url: URL, timeout: Int): Boolean = getURLInfo(url, timeout).isReachable
  def getContentLength(url: URL): Long = getURLInfo(url).getContentLength
  def getContentLength(url: URL, timeout: Int): Long = getURLInfo(url, timeout).getContentLength
  def getLastModified(url: URL): Long = getURLInfo(url).getLastModified
  def getLastModified(url: URL, timeout: Int): Long = getURLInfo(url, timeout).getLastModified
  def getURLInfo(url: URL): URLInfo = getURLInfo(url, 0)
  
  private def debug(msg: String): Unit = Message.debug("S3URLHandler."+msg)
  
  private def makePropertiesFileCredentialsProvider(fileName: String): PropertiesFileCredentialsProvider = {
    val dir: File = new File(System.getProperty("user.home"), ".sbt")
    val file: File = new File(dir, fileName)
    new PropertiesFileCredentialsProvider(file.toString)
  }
  
  private def makeCredentialsProviderChain(bucket: String): AWSCredentialsProviderChain = {
    val providers = Vector(
      new BucketSpecificEnvironmentVariableCredentialsProvider(bucket),
      new BucketSpecificSystemPropertiesCredentialsProvider(bucket),
      makePropertiesFileCredentialsProvider(s".s3credentials_${bucket}"),
      makePropertiesFileCredentialsProvider(s".${bucket}_s3credentials"),
      new EnvironmentVariableCredentialsProvider(),
      new SystemPropertiesCredentialsProvider(),
      makePropertiesFileCredentialsProvider(".s3credentials"),
      new InstanceProfileCredentialsProvider()
    )
    
    new AWSCredentialsProviderChain(providers: _*)
  }
  
  def getCredentials(bucket: String): AWSCredentials = try {
    makeCredentialsProviderChain(bucket).getCredentials()
  } catch {
    case ex: com.amazonaws.AmazonClientException => 
      Message.error("Unable to find AWS Credentials.")
      throw ex
  }
  
  def getClientBucketAndKey(url: URL): (AmazonS3Client, String, String) = {
    val (bucket, key) = getBucketAndKey(url)
    val client: AmazonS3Client = new AmazonS3Client(getCredentials(bucket))
    
    val region: Option[Region] = getRegion(url, bucket, client)
    region.foreach{ client.setRegion }
    
    (client, bucket, key)
  }
  
  def getURLInfo(url: URL, timeout: Int): URLInfo = try {
    debug(s"getURLInfo($url, $timeout)")
    
    val (client, bucket, key) = getClientBucketAndKey(url)
    
    val meta: ObjectMetadata = client.getObjectMetadata(bucket, key)
    
    val available: Boolean = true
    val contentLength: Long = meta.getContentLength
    val lastModified: Long = meta.getLastModified.getTime
    
    new S3URLInfo(available, contentLength, lastModified)
  } catch {
    case ex: AmazonS3Exception if ex.getStatusCode == 404 => UNAVAILABLE
  }
  
  def openStream(url: URL): InputStream = {
    debug(s"openStream($url)")
    
    val (client, bucket, key) = getClientBucketAndKey(url)
    val obj: S3Object = client.getObject(bucket, key)
    obj.getObjectContent()
  }
  
  def download(src: URL, dest: File, l: CopyProgressListener): Unit = {
    debug(s"download($src, $dest)")
    
    val (client, bucket, key) = getClientBucketAndKey(src)
    
    val event: CopyProgressEvent = new CopyProgressEvent()
    if (null != l) l.start(event)
    
    val meta: ObjectMetadata = client.getObject(new GetObjectRequest(bucket, key), dest)
    dest.setLastModified(meta.getLastModified.getTime)
    
    if (null != l) l.end(event) //l.progress(evt.update(EMPTY_BUFFER, 0, meta.getContentLength))
  }
  
  def upload(src: File, dest: URL, l: CopyProgressListener): Unit = {
    debug(s"upload($src, $dest)")
    
    val event: CopyProgressEvent = new CopyProgressEvent()
    if (null != l) l.start(event)
    
    val (client, bucket, key) = getClientBucketAndKey(dest)
    val res: PutObjectResult = client.putObject(bucket, key, src)
    
    if (null != l) l.end(event)
  }
  
  // I don't think we care what this is set to
  def setRequestMethod(requestMethod: Int): Unit = {}
  
  // Try to get the region of the S3 URL so we can set it on the S3Client
  def getRegion(url: URL, bucket: String, client: AmazonS3Client): Option[Region] = {
    val region: Option[String] = getRegionNameFromURL(url) orElse getRegionNameFromDNS(bucket) orElse getRegionNameFromService(bucket, client)

    region.map{ RegionUtils.getRegion }.flatMap{ Option(_) }
  }
  
  def getRegionNameFromURL(url: URL): Option[String] = {
    // We'll try the AmazonS3URI parsing first then fallback to our RegionMatcher
    getAmazonS3URI(url).map{ _.getRegion }.flatMap{ Option(_) } orElse RegionMatcher.findFirstIn(url.toString)
  }
  
  def getRegionNameFromDNS(bucket: String): Option[String] = {
    // This gives us something like s3-us-west-2-w.amazonaws.com which must have changed
    // at some point because the region from that hostname is no longer parsed by AmazonS3URI
    val canonicalHostName: String = InetAddress.getByName(bucket+".s3.amazonaws.com").getCanonicalHostName()
    
    // So we use our regex based RegionMatcher to try and extract the region since AmazonS3URI doesn't work
    RegionMatcher.findFirstIn(canonicalHostName)
  }
  
  // TODO: cache the result of this so we aren't always making the call
  def getRegionNameFromService(bucket: String, client: AmazonS3Client): Option[String] = {
    // This might fail if the current credentials don't have access to the getBucketLocation call
    Try { client.getBucketLocation(bucket) }.toOption
  }
  
  def getBucketAndKey(url: URL): (String, String) = {
    // The AmazonS3URI constructor should work for standard S3 urls.  But if a custom domain is being used
    // (e.g. snapshots.maven.frugalmechanic.com) then we treat the hostname as the bucket and the path as the key
    getAmazonS3URI(url).map{ amzn: AmazonS3URI =>
      (amzn.getBucket, amzn.getKey)
    }.getOrElse {
      // Probably a custom domain name - The host should be the bucket and the path the key
      (url.getHost, url.getPath.stripPrefix("/"))
    }
  }
  
  def getAmazonS3URI(uri: String): Option[AmazonS3URI] = getAmazonS3URI(URI.create(uri))
  def getAmazonS3URI(url: URL)   : Option[AmazonS3URI] = getAmazonS3URI(url.toURI)
  
  def getAmazonS3URI(uri: URI)   : Option[AmazonS3URI] = try {
    val httpsURI: URI =
      // If there is no scheme (e.g. new URI("s3-us-west-2.amazonaws.com/<bucket>"))
      // then we need to re-create the URI to add one and to also make sure the host is set
      if (uri.getScheme == null) new URI("https://"+uri)
      // AmazonS3URI can't parse the region from s3:// URLs so we rewrite the scheme to https://
      else new URI("https", uri.getUserInfo, uri.getHost, uri.getPort, uri.getPath, uri.getQuery, uri.getFragment)

    Some(new AmazonS3URI(httpsURI))
  } catch {
    case _: IllegalArgumentException => None
  }
}