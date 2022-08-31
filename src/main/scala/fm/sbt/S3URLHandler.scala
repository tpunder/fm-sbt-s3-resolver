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

import com.amazonaws.services.s3.AmazonS3URI

import java.io.{File, FileInputStream, InputStream}
import java.net.{URI, URL}
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import javax.naming.{Context, NamingException}
import javax.naming.directory.{Attribute, Attributes, InitialDirContext}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, AwsCredentials, AwsCredentialsProvider, AwsCredentialsProviderChain, AwsSessionCredentials, DefaultCredentialsProvider, InstanceProfileCredentialsProvider}
import software.amazon.awssdk.core.SdkSystemSetting
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.{GetObjectAttributesRequest, GetObjectRequest, GetObjectResponse, HeadObjectRequest, ListObjectsRequest, ListObjectsResponse, ObjectCannedACL, PutObjectRequest, PutObjectResponse, S3Exception, ServerSideEncryption}
import software.amazon.awssdk.services.s3.{S3Client, S3Configuration}
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.{AssumeRoleRequest, AssumeRoleResponse}
import org.apache.ivy.util.url.URLHandler
import org.apache.ivy.util.{CopyProgressEvent, CopyProgressListener, Message}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.matching.Regex

object S3URLHandler {
  private val DOT_SBT_DIR: File = new File(System.getProperty("user.home"), ".sbt")

  // This is for matching region names in URLs or host names
  private val RegionMatcher: Regex = Region.regions().asScala.map{ _.id() }.sortBy{ -1 * _.length }.mkString("|").r

  private var bucketCredentialsProvider: String => AwsCredentialsProvider = a => DefaultCredentialsProvider.create() //makePropertiesFileCredentialsProvider

  private var bucketACLMap: Map[String, ObjectCannedACL] = Map()

  def registerBucketCredentialsProvider(provider: String => AwsCredentialsProvider): Unit = {
    bucketCredentialsProvider = provider
  }

  def registerBucketACLMap(aclMap: Map[String, ObjectCannedACL]): Unit = {
    bucketACLMap = aclMap
  }

  def getBucketCredentialsProvider: String => AwsCredentialsProvider = bucketCredentialsProvider

  private class S3URLInfo(available: Boolean, contentLength: Long, lastModified: Long) extends URLHandler.URLInfo(available, contentLength, lastModified)
  
  private class BucketSpecificSystemPropertiesCredentialsProvider(bucket: String) extends BucketSpecificCredentialsProvider(bucket) {
    
    def AccessKeyName: String = SdkSystemSetting.AWS_ACCESS_KEY_ID.property()
    def SecretKeyName: String = SdkSystemSetting.AWS_SECRET_ACCESS_KEY.property()

    protected def getProp(names: String*): String = names.map{ System.getProperty }.flatMap{ Option(_) }.head.trim
  }
  
  private class BucketSpecificEnvironmentVariableCredentialsProvider(bucket: String) extends BucketSpecificCredentialsProvider(bucket) {
    def AccessKeyName: String = SdkSystemSetting.AWS_ACCESS_KEY_ID.property()
    def SecretKeyName: String = SdkSystemSetting.AWS_SECRET_ACCESS_KEY.property()
    
    protected def getProp(names: String*): String = names.map{ toEnvironmentVariableName }.map{ System.getenv }.flatMap{ Option(_) }.head.trim
  }
  
  private abstract class BucketSpecificCredentialsProvider(bucket: String) extends AwsCredentialsProvider {
    def AccessKeyName: String
    def SecretKeyName: String
    
    def resolveCredentials(): AwsCredentials = {
      val accessKey: String = getProp(s"${AccessKeyName}.${bucket}", s"${bucket}.${AccessKeyName}")
      val secretKey: String = getProp(s"${SecretKeyName}.${bucket}", s"${bucket}.${SecretKeyName}")
      
      AwsBasicCredentials.create(accessKey, secretKey)
    }
    
    def refresh(): Unit = {}
    
    // This should throw an exception if the value is missing
    protected def getProp(names: String*): String
  }

  private abstract class RoleBasedCredentialsProvider(providerChain: AwsCredentialsProviderChain) extends AwsCredentialsProvider {
    def RoleArnKeyNames: Seq[String]

    // This should throw an exception if the value is missing
    protected def getRoleArn(keys: String*): String

    def resolveCredentials(): AwsCredentials = {
      val roleArn: String = getRoleArn(RoleArnKeyNames: _*)

      if (roleArn == null || roleArn == "") return null

      val securityTokenService: StsClient = StsClient.builder().credentialsProvider(providerChain).build()

      val roleRequest: AssumeRoleRequest = AssumeRoleRequest.builder()
        .roleArn(roleArn)
        .roleSessionName(System.currentTimeMillis.toString)
        .build()

      val result: AssumeRoleResponse = securityTokenService.assumeRole(roleRequest)

      AwsSessionCredentials.create(result.credentials().accessKeyId(), result.credentials().secretAccessKey(), result.credentials().sessionToken())
    }

    def refresh(): Unit = {}
  }

  private class RoleBasedSystemPropertiesCredentialsProvider(providerChain: AwsCredentialsProviderChain)
      extends RoleBasedCredentialsProvider(providerChain) {

    val RoleArnKeyName: String = "aws.roleArn"
    val RoleArnKeyNames: Seq[String] = Seq(RoleArnKeyName)

    protected def getRoleArn(keys: String*): String = keys.map( System.getProperty ).flatMap( Option(_) ).head.trim
  }

  private class RoleBasedEnvironmentVariableCredentialsProvider(providerChain: AwsCredentialsProviderChain)
      extends RoleBasedCredentialsProvider(providerChain) {

    val RoleArnKeyName: String = "AWS_ROLE_ARN"
    val RoleArnKeyNames: Seq[String] = Seq("AWS_ROLE_ARN")

    protected def getRoleArn(keys: String*): String = keys.map( toEnvironmentVariableName ).map( System.getenv ).flatMap( Option(_) ).head.trim
  }

  private class RoleBasedPropertiesFileCredentialsProvider(providerChain: AwsCredentialsProviderChain, fileName: String)
      extends RoleBasedCredentialsProvider(providerChain) {

    val RoleArnKeyName: String = "roleArn"
    val RoleArnKeyNames: Seq[String] = Seq(RoleArnKeyName)

    protected def getRoleArn(keys: String*): String = {
      val file: File = new File(DOT_SBT_DIR, fileName)
      
      // This will throw if the file doesn't exist
      val is: InputStream = new FileInputStream(file)
      
      try {
        val props: Properties = new Properties()
        props.load(is)
        // This will throw if there is no matching properties
        RoleArnKeyNames.map{ props.getProperty }.flatMap{ Option(_) }.head.trim
      } finally is.close()
    }
  }

  private class BucketSpecificRoleBasedSystemPropertiesCredentialsProvider(providerChain: AwsCredentialsProviderChain, bucket: String)
      extends RoleBasedSystemPropertiesCredentialsProvider(providerChain) {

    override val RoleArnKeyNames: Seq[String] = Seq(s"${RoleArnKeyName}.${bucket}", s"${bucket}.${RoleArnKeyName}")
  }

  private class BucketSpecificRoleBasedEnvironmentVariableCredentialsProvider(providerChain: AwsCredentialsProviderChain, bucket: String)
      extends RoleBasedEnvironmentVariableCredentialsProvider(providerChain) {

    override val RoleArnKeyNames: Seq[String] = Seq(s"${RoleArnKeyName}.${bucket}", s"${bucket}.${RoleArnKeyName}")
  }
  
  private def toEnvironmentVariableName(s: String): String = s.toUpperCase.replace('-','_').replace('.','_').replaceAll("[^A-Z0-9_]", "")

//  private def makePropertiesFileCredentialsProvider(fileName: String): PropertiesFileCredentialsProvider = {
//    val file: File = new File(DOT_SBT_DIR, fileName)
//    new PropertiesFileCredentialsProvider(file.toString)
//  }

  def defaultCredentialsProviderChain(bucket: String): AwsCredentialsProviderChain = {
    val basicProviders: Vector[AwsCredentialsProvider] = Vector(
      new BucketSpecificEnvironmentVariableCredentialsProvider(bucket),
      new BucketSpecificSystemPropertiesCredentialsProvider(bucket),
//      makePropertiesFileCredentialsProvider(s".s3credentials_${bucket}"),
//      makePropertiesFileCredentialsProvider(s".${bucket}_s3credentials"),
      DefaultCredentialsProvider.create(),
//      makePropertiesFileCredentialsProvider(".s3credentials"),
      InstanceProfileCredentialsProvider.create()
    )

    val basicProviderChain: AwsCredentialsProviderChain = AwsCredentialsProviderChain.builder().credentialsProviders(basicProviders: _*).build()

    val roleBasedProviders: Vector[AwsCredentialsProvider] = Vector(
      new BucketSpecificRoleBasedEnvironmentVariableCredentialsProvider(basicProviderChain, bucket),
      new BucketSpecificRoleBasedSystemPropertiesCredentialsProvider(basicProviderChain, bucket),
      new RoleBasedPropertiesFileCredentialsProvider(basicProviderChain, s".s3credentials_${bucket}"),
      new RoleBasedPropertiesFileCredentialsProvider(basicProviderChain, s".${bucket}_s3credentials"),
      new RoleBasedEnvironmentVariableCredentialsProvider(basicProviderChain),
      new RoleBasedSystemPropertiesCredentialsProvider(basicProviderChain),
      new RoleBasedPropertiesFileCredentialsProvider(basicProviderChain, s".s3credentials")
    )

    AwsCredentialsProviderChain.builder().credentialsProviders((roleBasedProviders ++ basicProviders): _*).build()
  }

  def getRegionNameFromDNS(bucket: String): Option[String] = {
    // maven.custom.s3.amazonaws.com. 21600 IN	CNAME	s3-1-w.amazonaws.com.
    //           s3-1-w.amazonaws.com.	39	IN	CNAME	s3-w.us-east-1.amazonaws.com.
    getDNSAliasesForBucket(bucket).flatMap { RegionMatcher.findFirstIn(_) }.headOption
  }

  private[this] val dnsContext: InitialDirContext = {
    val env: Properties = new Properties()
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory")
    new InitialDirContext(env)
  }

  def getDNSAliasesForBucket(bucket: String): Seq[String] = {
    getDNSAliasesForHost(bucket + ".s3.amazonaws.com")
  }

  def getDNSAliasesForHost(host: String): Seq[String] = getDNSAliasesForHost(host, Nil)

  @tailrec private def getDNSAliasesForHost(host: String, matches: List[String]): Seq[String] = {
    val cname: Option[String] = try {
      val attrs: Attributes = dnsContext.getAttributes(host, Array("CNAME"))
      Option(attrs.get("CNAME"))
        .flatMap{ attr: Attribute => Option(attr.get) }
        .collectFirst{ case res: String => res }
    } catch {
      case _: NamingException => None
    }

    if (cname.isEmpty || cname.exists{ matches.contains(_) }) matches
    else getDNSAliasesForHost(cname.get, cname.get :: matches)
  }
}

/**
 * This implements the Ivy URLHandler
 */
final class S3URLHandler extends URLHandler {
  import fm.sbt.S3URLHandler._
  import org.apache.ivy.util.url.URLHandler.{UNAVAILABLE, URLInfo}

  // Cache of Bucket Name => AmazonS3 Client Instance
  private val amazonS3ClientCache: ConcurrentHashMap[String,S3Client] = new ConcurrentHashMap()

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

  def getCredentialsProvider(bucket: String): AwsCredentialsProvider = {
    Message.info("S3URLHandler - Looking up AWS Credentials for bucket: "+bucket+" ...")

    val credentialsProvider: AwsCredentialsProvider = try {
      getBucketCredentialsProvider(bucket)
    } catch {
      case ex: SdkClientException =>
        Message.error("Unable to find AWS Credentials.")
        throw ex
    }

    Message.info("S3URLHandler - Using AWS Access Key Id: "+credentialsProvider.resolveCredentials().accessKeyId()+" for bucket: "+bucket)

    credentialsProvider
  }

//  def getProxyConfiguration: SdkHttpClient.Builder = {
//    val configuration = SdkHttpClient.Builder
//    for {
//      proxyHost <- Option( System.getProperty("https.proxyHost") )
//      proxyPort <- Option( System.getProperty("https.proxyPort").toInt )
//    } {
//      configuration.setProxyHost(proxyHost)
//      configuration.setProxyPort(proxyPort)
//    }
//    configuration
//  }

  def getClientBucketAndKey(url: URL): (S3Client, String, String) = {
    val (bucket, key) = getBucketAndKey(url)

    var client: S3Client = amazonS3ClientCache.get(bucket)

    if (null == client) {
      // This allows you to change the S3 endpoint and signing region to point to a non-aws S3 implementation (e.g. LocalStack).
      val endpointConfiguration: Option[(URI, Region)] = for {
        serviceEndpoint <- Option(System.getenv("S3_SERVICE_ENDPOINT")).map(URI.create)
        signingRegion <- Option(System.getenv("S3_SIGNING_REGION")).map(Region.of)
      } yield (serviceEndpoint, signingRegion)

      // Path Style Access is deprecated by Amazon S3 but LocalStack seems to want to use it
      val pathStyleAccess: Boolean = Option(System.getenv("S3_PATH_STYLE_ACCESS")).map{ _.toBoolean }.getOrElse(false)

      val tmp = S3Client.builder()
        .credentialsProvider(getCredentialsProvider(bucket))
//        .httpClient(getProxyConfiguration)
        .serviceConfiguration(
          S3Configuration.builder()
            //        .withForceGlobalBucketAccessEnabled(true) https://github.com/aws/aws-sdk-java-v2/issues/52
            .pathStyleAccessEnabled(pathStyleAccess)
            .build()
        )

      // Only one of the endpointConfiguration or region can be set at a time.
      client = (endpointConfiguration match {
        case Some((endpoint, region)) => tmp.endpointOverride(endpoint).region(region)
        case None => tmp.region(getRegion(url, bucket))
      }).build()

      amazonS3ClientCache.put(bucket, client)

      Message.info("S3URLHandler - Created S3 Client for bucket: "+bucket)
    }

    (client, bucket, key)
  }

  def getURLInfo(url: URL, timeout: Int): URLInfo = try {
    debug(s"getURLInfo($url, $timeout)")
    
    val (client, bucket, key) = getClientBucketAndKey(url)
    
    val meta = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
    
    val available: Boolean = true
    val contentLength: Long = meta.contentLength()
    val lastModified: Long = meta.lastModified().toEpochMilli
    
    new S3URLInfo(available, contentLength, lastModified)
  } catch {
    case ex: S3Exception if ex.statusCode() == 404 => UNAVAILABLE
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
    val obj = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())
    obj
  }
  
  /**
   * A directory listing for keys/directories under this prefix
   */
  def list(url: URL): Seq[URL] = {
    debug(s"list($url)")
    
    val (client, bucket, key /* key is the prefix in this case */) = getClientBucketAndKey(url)
    
    // We want the prefix to have a trailing slash
    val prefix: String = key.stripSuffix("/") + "/"
    
    val request: ListObjectsRequest = ListObjectsRequest.builder().bucket(bucket).prefix(prefix).delimiter("/").build()
    
    val listing: ListObjectsResponse = client.listObjects(request)
    
    require(!listing.isTruncated, "Truncated ObjectListing!  Making additional calls currently isn't implemented!")
    
    val keys: Seq[String] = listing.commonPrefixes().asScala.map(_.prefix()) ++ listing.contents().asScala.map{ _.key() }
    
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
    
    val meta: GetObjectResponse = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), dest.toPath)
    dest.setLastModified(meta.lastModified().toEpochMilli)
    
    if (null != l) l.end(event) //l.progress(evt.update(EMPTY_BUFFER, 0, meta.getContentLength))
  }

  def upload(src: File, dest: URL, l: CopyProgressListener): Unit = {
    debug(s"upload($src, $dest)")

    val event: CopyProgressEvent = new CopyProgressEvent()
    if (null != l) l.start(event)

    val (client, bucket, key) = getClientBucketAndKey(dest)

    // Nested helper method for performing the actual PUT
    def putImpl(serverSideEncryption: Boolean): PutObjectResponse = {
      val customizers = Seq[PutObjectRequest.Builder => PutObjectRequest.Builder](
        // add metadata
        x => {if (serverSideEncryption) x.serverSideEncryption(ServerSideEncryption.AES256) else x},
        // add bucket ACL
        x => {
          bucketACLMap.get(bucket) match {
            case Some(y) => x.acl(y)
            case None => x
          }
        }
      )

      val req = customizers.foldLeft(PutObjectRequest.builder().bucket(bucket).key(key))((putObjectRequest, customizer) => customizer(putObjectRequest))

      client.putObject(req.build(), src.toPath)
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
        case ex: S3Exception if ex.statusCode() == 403 =>
          debug(s"upload($src, $dest) failed with a 403 status code.  Retrying with Server Side Encryption Enabled.")

          // Retry with SSE
          val res: PutObjectResponse = putImpl(true)

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
  def getRegion(url: URL, bucket: String/*, client: AmazonS3*/): Region = {
    getRegionNameFromURL(url).toOptionalRegion orElse
      getRegionNameFromDNS(bucket).toOptionalRegion getOrElse
      //Option(Region.getCurrentRegion()).map{ _.getName }.toOptionalRegion getOrElse
      Region.US_EAST_1
  }

  private implicit class RichStringOption(s: Option[String]) {
    def toOptionalRegion: Option[Region] = s.flatMap{ _.toOptionalRegion }
  }

  private implicit class RichString(s: String) {
    def toOptionalRegion: Option[Region] = Try{ Region.of(s) }.toOption
  }

  def getRegionNameFromURL(url: URL): Option[String] = {
    // We'll try the AmazonS3URI parsing first then fallback to our RegionMatcher
    //getAmazonS3URI(url).map{ _.getRegion }.flatMap{ Option(_) } orElse RegionMatcher.findFirstIn(url.toString)
    ???
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