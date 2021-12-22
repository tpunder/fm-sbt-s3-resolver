package fm.sbt

import com.amazonaws.SDKGlobalConfiguration.{ACCESS_KEY_ENV_VAR, ACCESS_KEY_SYSTEM_PROPERTY, SECRET_KEY_ENV_VAR, SECRET_KEY_SYSTEM_PROPERTY}
import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider, AWSCredentialsProviderChain, BasicAWSCredentials, BasicSessionCredentials, DefaultAWSCredentialsProviderChain, InstanceProfileCredentialsProvider, PropertiesFileCredentialsProvider}
import com.amazonaws.services.securitytoken.{AWSSecurityTokenService, AWSSecurityTokenServiceClient}
import com.amazonaws.services.securitytoken.model.{AssumeRoleRequest, AssumeRoleResult}
import java.io.{File, FileInputStream, InputStream}
import java.util.Properties
import org.apache.ivy.util.Message

object S3CredentialsProvider {

  private[this] val DOT_SBT_DIR: File = {
    sys.props.get("sbt.global.base")
      .map{ new File(_) }
      .getOrElse(new File(System.getProperty("user.home"), ".sbt"))
  }

  private[this] var bucketCredentialsProvider: String => AWSCredentialsProvider = makePropertiesFileCredentialsProvider

  def registerBucketCredentialsProvider(provider: String => AWSCredentialsProvider): Unit = {
    bucketCredentialsProvider = provider
  }

  def getBucketCredentialsProvider: String => AWSCredentialsProvider = bucketCredentialsProvider

  private class BucketSpecificSystemPropertiesCredentialsProvider(bucket: String) extends BucketSpecificCredentialsProvider(bucket) {

    def AccessKeyName: String = ACCESS_KEY_SYSTEM_PROPERTY
    def SecretKeyName: String = SECRET_KEY_SYSTEM_PROPERTY

    protected def getProp(names: String*): String = names.map{ System.getProperty }.flatMap{ Option(_) }.head.trim
  }

  private class BucketSpecificEnvironmentVariableCredentialsProvider(bucket: String) extends BucketSpecificCredentialsProvider(bucket) {
    def AccessKeyName: String = ACCESS_KEY_ENV_VAR
    def SecretKeyName: String = SECRET_KEY_ENV_VAR

    protected def getProp(names: String*): String = names.map{ toEnvironmentVariableName }.map{ System.getenv }.flatMap{ Option(_) }.head.trim
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

  private abstract class RoleBasedCredentialsProvider(providerChain: AWSCredentialsProviderChain) extends AWSCredentialsProvider {
    def RoleArnKeyNames: Seq[String]

    // This should throw an exception if the value is missing
    protected def getRoleArn(keys: String*): String

    def getCredentials(): AWSCredentials = {
      val roleArn: String = getRoleArn(RoleArnKeyNames: _*)

      if (roleArn == null || roleArn == "") return null

      val securityTokenService: AWSSecurityTokenService = AWSSecurityTokenServiceClient.builder().withCredentials(providerChain).build()

      val roleRequest: AssumeRoleRequest = new AssumeRoleRequest()
        .withRoleArn(roleArn)
        .withRoleSessionName(System.currentTimeMillis.toString)

      val result: AssumeRoleResult = securityTokenService.assumeRole(roleRequest)

      new BasicSessionCredentials(result.getCredentials.getAccessKeyId, result.getCredentials.getSecretAccessKey, result.getCredentials.getSessionToken)
    }

    def refresh(): Unit = {}
  }

  private class RoleBasedSystemPropertiesCredentialsProvider(providerChain: AWSCredentialsProviderChain)
    extends RoleBasedCredentialsProvider(providerChain) {

    val RoleArnKeyName: String = "aws.roleArn"
    val RoleArnKeyNames: Seq[String] = Seq(RoleArnKeyName)

    protected def getRoleArn(keys: String*): String = keys.map( System.getProperty ).flatMap( Option(_) ).head.trim
  }

  private class RoleBasedEnvironmentVariableCredentialsProvider(providerChain: AWSCredentialsProviderChain)
    extends RoleBasedCredentialsProvider(providerChain) {

    val RoleArnKeyName: String = "AWS_ROLE_ARN"
    val RoleArnKeyNames: Seq[String] = Seq("AWS_ROLE_ARN")

    protected def getRoleArn(keys: String*): String = keys.map( toEnvironmentVariableName ).map( System.getenv ).flatMap( Option(_) ).head.trim
  }

  private class RoleBasedPropertiesFileCredentialsProvider(providerChain: AWSCredentialsProviderChain, fileName: String)
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

  private class BucketSpecificRoleBasedSystemPropertiesCredentialsProvider(providerChain: AWSCredentialsProviderChain, bucket: String)
    extends RoleBasedSystemPropertiesCredentialsProvider(providerChain) {

    override val RoleArnKeyNames: Seq[String] = Seq(s"${RoleArnKeyName}.${bucket}", s"${bucket}.${RoleArnKeyName}")
  }

  private class BucketSpecificRoleBasedEnvironmentVariableCredentialsProvider(providerChain: AWSCredentialsProviderChain, bucket: String)
    extends RoleBasedEnvironmentVariableCredentialsProvider(providerChain) {

    override val RoleArnKeyNames: Seq[String] = Seq(s"${RoleArnKeyName}.${bucket}", s"${bucket}.${RoleArnKeyName}")
  }

  private def toEnvironmentVariableName(s: String): String = s.toUpperCase.replace('-','_').replace('.','_').replaceAll("[^A-Z0-9_]", "")

  private def makePropertiesFileCredentialsProvider(fileName: String): PropertiesFileCredentialsProvider = {
    val file: File = new File(DOT_SBT_DIR, fileName)
    new PropertiesFileCredentialsProvider(file.toString)
  }

  def defaultCredentialsProviderChain(bucket: String): AWSCredentialsProviderChain = {
    val basicProviders: Vector[AWSCredentialsProvider] = Vector(
      new BucketSpecificEnvironmentVariableCredentialsProvider(bucket),
      new BucketSpecificSystemPropertiesCredentialsProvider(bucket),
      makePropertiesFileCredentialsProvider(s".s3credentials_${bucket}"),
      makePropertiesFileCredentialsProvider(s".${bucket}_s3credentials"),
      DefaultAWSCredentialsProviderChain.getInstance(),
      makePropertiesFileCredentialsProvider(".s3credentials"),
      InstanceProfileCredentialsProvider.getInstance()
    )

    val basicProviderChain: AWSCredentialsProviderChain = new AWSCredentialsProviderChain(basicProviders: _*)

    val roleBasedProviders: Vector[AWSCredentialsProvider] = Vector(
      new BucketSpecificRoleBasedEnvironmentVariableCredentialsProvider(basicProviderChain, bucket),
      new BucketSpecificRoleBasedSystemPropertiesCredentialsProvider(basicProviderChain, bucket),
      new RoleBasedPropertiesFileCredentialsProvider(basicProviderChain, s".s3credentials_${bucket}"),
      new RoleBasedPropertiesFileCredentialsProvider(basicProviderChain, s".${bucket}_s3credentials"),
      new RoleBasedEnvironmentVariableCredentialsProvider(basicProviderChain),
      new RoleBasedSystemPropertiesCredentialsProvider(basicProviderChain),
      new RoleBasedPropertiesFileCredentialsProvider(basicProviderChain, s".s3credentials")
    )

    new AWSCredentialsProviderChain((roleBasedProviders ++ basicProviders): _*)
  }

  def getCredentialsProvider(bucket: String): AWSCredentialsProvider = {
    Message.info("S3URLHandler - Looking up AWS Credentials for bucket: "+bucket+" ...")

    val credentialsProvider: AWSCredentialsProvider = try {
      S3CredentialsProvider.getBucketCredentialsProvider(bucket)
    } catch {
      case ex: com.amazonaws.AmazonClientException =>
        Message.error("Unable to find AWS Credentials.")
        throw ex
    }

    Message.info("S3URLHandler - Using AWS Access Key Id: "+credentialsProvider.getCredentials().getAWSAccessKeyId+" for bucket: "+bucket)

    credentialsProvider
  }
}