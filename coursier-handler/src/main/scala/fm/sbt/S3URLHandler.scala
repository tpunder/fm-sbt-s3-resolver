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

import java.io.{File, InputStream}
import java.net.{URI, URL}
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import com.amazonaws.ClientConfiguration
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client, AmazonS3URI}
import org.apache.ivy.util.url.URLHandler
import org.apache.ivy.util.{CopyProgressEvent, CopyProgressListener, Message}
import javax.naming.{Context, NamingException}
import javax.naming.directory.InitialDirContext
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.matching.Regex

object S3URLHandler {
  // This is for matching region names in URLs or host names
  private val RegionMatcher: Regex = Regions.values().map{ _.getName }.sortBy{ -1 * _.length }.mkString("|").r

  private class S3URLInfo(available: Boolean, contentLength: Long, lastModified: Long) extends URLHandler.URLInfo(available, contentLength, lastModified)

  def getRegionNameFromDNS(bucket: String): Option[String] = {
    // maven.custom.s3.amazonaws.com. 21600 IN	CNAME	s3-1-w.amazonaws.com.
    //           s3-1-w.amazonaws.com.	39	IN	CNAME	s3-w.us-east-1.amazonaws.com.
    getDNSAliases(bucket).flatMap { RegionMatcher.findFirstIn(_) }.headOption
  }

  private[this] val dnsContext: InitialDirContext = {
    val env: Properties = new Properties()
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory")
    new InitialDirContext(env)
  }

  private def getDNSAliases(bucket: String): Seq[String] = {
    val bucketHostname = bucket + ".s3.amazonaws.com"

    @tailrec def impl(host: String, matches: Seq[String]): Seq[String] = {
      val cname: Option[String] = try {
        val attrs = dnsContext.getAttributes(host, Array("CNAME"))
        Option(attrs.get("CNAME"))
          .flatMap{ attr => Option(attr.get) }
          .collectFirst { case host: String => host }
      } catch {
        case _: NamingException => None
      }

      if (cname.isEmpty || cname.exists{ matches.contains(_) }) matches
      else impl(cname.get, cname.get +: matches)
    }

    impl(bucketHostname, Nil)
  }
}

/**
 * This implements the Ivy URLHandler
 */
final class S3URLHandler extends URLHandler {
  import S3URLHandler._

  import org.apache.ivy.util.url.URLHandler.{UNAVAILABLE, URLInfo}

  // Cache of Bucket Name => AmazonS3 Client Instance
  private val amazonS3ClientCache: ConcurrentHashMap[String,AmazonS3] = new ConcurrentHashMap()

  // Cache of Bucket Name => true/false (requires Server Side Encryption or not)
  private val bucketRequiresSSE: ConcurrentHashMap[String,Boolean] = new ConcurrentHashMap()

  def isReachable(url: URL): Boolean = getURLInfo(url).isReachable
  def isReachable(url: URL, timeout: Int): Boolean = getURLInfo(url, timeout).isReachable
  def getContentLength(url: URL): Long = getURLInfo(url).getContentLength
  def getContentLength(url: URL, timeout: Int): Long = getURLInfo(url, timeout).getContentLength
  def getLastModified(url: URL): Long = getURLInfo(url).getLastModified
  def getLastModified(url: URL, timeout: Int): Long = getURLInfo(url, timeout).getLastModified
  def getURLInfo(url: URL): URLInfo = getURLInfo(url, 0)

  private def debug(msg: String): Unit = Message.debug("S3URLHandler."+msg)

  def getProxyConfiguration: ClientConfiguration = {
    val configuration = new ClientConfiguration()
    for {
      proxyHost <- Option( System.getProperty("https.proxyHost") )
      proxyPort <- Option( System.getProperty("https.proxyPort").toInt )
    } {
      configuration.setProxyHost(proxyHost)
      configuration.setProxyPort(proxyPort)
    }
    configuration
  }

  def getClientBucketAndKey(url: URL): (AmazonS3, String, String) = {
    val (bucket, key) = getBucketAndKey(url)

    var client: AmazonS3 = amazonS3ClientCache.get(bucket)

    if (null == client) {
      var builder = AmazonS3Client.builder()
        .withCredentials(S3CredentialsProvider.getCredentialsProvider(bucket))
        .withClientConfiguration(getProxyConfiguration)
        .withPathStyleAccessEnabled(true)
        .withForceGlobalBucketAccessEnabled(true)

      // Enables specifying a specific endpoint config (e.g. LocalStack instance for integration tests)
      val endpointConfig: Option[EndpointConfiguration] = for {
        serviceEndPoint <- scala.util.Properties.propOrNone("fm.sbt.s3.endpoint.serviceEndpoint")
        signingRegion <- scala.util.Properties.propOrNone("fm.sbt.s3.endpoint.signingRegion")
      } yield {
        new EndpointConfiguration(serviceEndPoint, signingRegion)
      }

      // Client requires endpoint config OR region
      builder = endpointConfig
        .map { builder.withEndpointConfiguration(_) }
        .getOrElse(builder.withRegion(getRegion(url, bucket)))

      client = builder.build()
      amazonS3ClientCache.put(bucket, client)

      Message.info("S3URLHandler - Created S3 Client for bucket: "+bucket+" and region: "+client.getRegionName)
    }

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
    case ex: java.net.URISyntaxException                  =>
      // We can hit this when given a URL that looks like:
      //   s3://maven.custom/releases/javax/ws/rs/javax.ws.rs-api/2.1/javax.ws.rs-api-2.1.${packaging.type}
      //
      // In that case we just ignore it and treat it as a 404.  It looks like this is really a bug in IVY that has
      // recently been fixed (as of 2018-03-12): https://issues.apache.org/jira/browse/IVY-1577
      //
      // Original Bug: https://github.com/frugalmechanic/fm-sbt-s3-resolver/issues/45
      // Original PR:  https://github.com/frugalmechanic/fm-sbt-s3-resolver/pull/46
      //
      Message.warn("S3URLHandler - " + ex.getMessage)

      UNAVAILABLE
  }
  
  def openStream(url: URL): InputStream = {
    debug(s"openStream($url)")
    
    val (client, bucket, key) = getClientBucketAndKey(url)
    val obj: S3Object = client.getObject(bucket, key)
    obj.getObjectContent()
  }
  
  /**
   * A directory listing for keys/directories under this prefix
   */
  def list(url: URL): Seq[URL] = {
    debug(s"list($url)")
    
    val (client, bucket, key /* key is the prefix in this case */) = getClientBucketAndKey(url)
    
    // We want the prefix to have a trailing slash
    val prefix: String = key.stripSuffix("/") + "/"
    
    val request: ListObjectsRequest = new ListObjectsRequest()
      .withBucketName(bucket)
      .withPrefix(prefix)
      .withDelimiter("/")
    
    val listing: ObjectListing = client.listObjects(request)
    
    require(!listing.isTruncated, "Truncated ObjectListing!  Making additional calls currently isn't implemented!")
    
    val keys: Seq[String] = listing.getCommonPrefixes.asScala.toSeq ++ listing.getObjectSummaries.asScala.toSeq.map{ _.getKey }
    
    val res: Seq[URL] = keys.map{ k: String =>
      new URL(url.toString.stripSuffix("/") + "/" + k.stripPrefix(prefix))
    }
    
    debug(s"list($url) => \n  "+res.mkString("\n  "))
    
    res
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

    // Nested helper method for performing the actual PUT
    def putImpl(serverSideEncryption: Boolean): PutObjectResult = {
      val meta: ObjectMetadata = new ObjectMetadata()
      if (serverSideEncryption) meta.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
      client.putObject(new PutObjectRequest(bucket, key, src).withMetadata(meta))
    }

    // Do we know for sure that this bucket requires SSE?
    val requiresSSE: Boolean = bucketRequiresSSE.containsKey(bucket)

    if (requiresSSE) {
      // We know we require SSE
      putImpl(true)
    } else {
      try {
        // We either don't require SSE or don't know yet so we try without SSE enabled
        putImpl(false)
      } catch {
        case ex: AmazonS3Exception if ex.getStatusCode() == 403 =>
          debug(s"upload($src, $dest) failed with a 403 status code.  Retrying with Server Side Encryption Enabled.")

          // Retry with SSE
          val res: PutObjectResult = putImpl(true)

          // If that succeeded then save the fact that we require SSE for future requests
          bucketRequiresSSE.put(bucket, true)

          Message.info(s"S3URLHandler - Enabled Server Side Encryption (SSE) for bucket: $bucket")

          res
      }
    }

    if (null != l) l.end(event)
  }

  // I don't think we care what this is set to
  def setRequestMethod(requestMethod: Int): Unit = debug(s"setRequestMethod($requestMethod)")
  
  // Try to get the region of the S3 URL so we can set it on the S3Client
  def getRegion(url: URL, bucket: String/*, client: AmazonS3*/): Regions = {
    getRegionNameFromURL(url).toOptionalRegion orElse
      getRegionNameFromDNS(bucket).toOptionalRegion orElse
      Option(Regions.getCurrentRegion()).map{ _.getName }.toOptionalRegion getOrElse
      Regions.DEFAULT_REGION
  }

  private implicit class RichStringOption(s: Option[String]) {
    def toOptionalRegion: Option[Regions] = s.flatMap{ _.toOptionalRegion }
  }

  private implicit class RichString(s: String) {
    def toOptionalRegion: Option[Regions] = Try{ Regions.fromName(s) }.toOption
  }

  def getRegionNameFromURL(url: URL): Option[String] = {
    // We'll try the AmazonS3URI parsing first then fallback to our RegionMatcher
    getAmazonS3URI(url).map{ _.getRegion }.flatMap{ Option(_) } orElse RegionMatcher.findFirstIn(url.toString)
  }


  // Not used anymore since the AmazonS3ClientBuilder requires the region during construction
//  def getRegionNameFromService(bucket: String, client: AmazonS3): Option[String] = {
//    // This might fail if the current credentials don't have access to the getBucketLocation call
//    Try { client.getBucketLocation(bucket) }.toOption
//  }
  
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