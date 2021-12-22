import sbt._
import Keys._
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.dimafeng.testcontainers.LocalStackContainer
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import com.amazonaws.services.s3.model.{ObjectListing, ObjectMetadata, PutObjectRequest}
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import sbt.ScriptedPlugin.autoImport.{scripted, scriptedLaunchOpts, scriptedSbt}
import sbt.plugins.SbtPlugin
import scala.annotation.tailrec
import scala.collection.JavaConverters._

/**
 * This starts an embedded S3 Server and sets appropriate ScriptedPlugin settings to
 * use the s3 server for the scripted tests
 */
case object S3ScriptedPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = SbtPlugin

  val TestBucket = "maven.custom"
  val s3ContainerKey = AttributeKey[LocalStackContainer]("fm-s3-container")
  val s3ClientKey = AttributeKey[AmazonS3]("fm-s3-client")
  val s3EndpointConfig = AttributeKey[EndpointConfiguration]("fm-s3-container-endpoint-config")
  val s3CredentialsKey = AttributeKey[AWSCredentials]("fm-s3-credentials")

  override lazy val projectSettings = Seq(
    scripted := scripted.dependsOn(setupS3ServerTask).evaluated
  )

  override val globalSettings: Seq[Def.Setting[_]] = Seq(
    Global / onLoad := (Global / onLoad).value andThen startS3Server,
  )

  private def getEndpointConfig(state: State): Option[EndpointConfiguration] = state.get(s3ContainerKey).map{ _.container.getEndpointConfiguration(Service.S3) }
  private def getCredentials(state: State): Option[AWSCredentials] = state.get(s3ContainerKey).map{ _.container.getDefaultCredentialsProvider.getCredentials }
  private def getS3Client(state: State): Option[AmazonS3] = state.get(s3ClientKey)
  private def getServiceEndpoint(state: State): Option[String] = getEndpointConfig(state).map{ _.getServiceEndpoint }
  private def getSigningRegion(state: State): Option[String] = getEndpointConfig(state).map{ _.getSigningRegion }
  private def getAWSAccessKeyId(state: State): Option[String] = getCredentials(state).map{ _.getAWSAccessKeyId }
  private def getAWSSecretKey(state: State): Option[String] = getCredentials(state).map{ _.getAWSSecretKey }

  private def startS3Server(state: State): State = {
    state.get(s3ContainerKey) match {
      case None =>
        state.log.info("[S3ScriptedPlugin] Starting Embedded S3 Server...")
        val container = LocalStackContainer(services = List(Service.S3))
        container.start()
        state.log.info("[S3ScriptedPlugin] Started S3 Server on endpoint: "+container.container.getEndpointConfiguration(Service.S3).getServiceEndpoint)

        ensureScriptedLaunchOpts(
          state.addExitHook {
            state
              .get(s3ContainerKey)
              .map { container =>
                state.log.info("[S3ScriptedPlugin] Stopping Embedded S3 Server...")
                container.stop()
                state.log.info("[S3ScriptedPlugin] Stopping Embedded S3 Server...done")
                state.remove(s3ContainerKey)
              }.getOrElse(state)
          },
          container
        )
      case Some(container) =>
        state.log.info("[S3ScriptedPlugin] Reusing S3 Server "+container.container.getEndpointConfiguration(Service.S3).getServiceEndpoint)
        // This is a hack to work around reloading via ^^ sbt.version
        if (!Project.extract(state).get(scriptedLaunchOpts).exists{ _.startsWith("-Daws.accessKeyId")}) {
          ensureScriptedLaunchOpts(state, container)
//          println(
//            s"""|state: $state
//                |
//                |${state.getSetting()}
//                |currentCommand: ${state.currentCommand}
//                |remainingCommands: ${state.remainingCommands}
//                |""".stripMargin)
//          state
        } else {
          state
        }
    }
  }

  private def ensureScriptedLaunchOpts(state: State, container: LocalStackContainer): State = {
    val client = AmazonS3Client.builder()
      .withForceGlobalBucketAccessEnabled(true)
      .withEndpointConfiguration(container.container.getEndpointConfiguration(Service.S3))
      .withCredentials(container.container.getDefaultCredentialsProvider)
      .build()

    val newState = state
      .put(s3ContainerKey, container)
      .put(s3ClientKey, client)
      .put(s3EndpointConfig, container.container.getEndpointConfiguration(Service.S3))
      .put(s3CredentialsKey, container.container.getDefaultCredentialsProvider.getCredentials)

    val currentCrossBuildSbtVersion = Project.extract(state).get(sbtVersion in pluginCrossBuild)
//    val currentScriptedSbtVersion = Project.extract(state).getOpt(scriptedSbt)
//    val currentSbtVersion = Project.extract(state).get(sbtVersion)
//    val currentSbtBinaryVersion = Project.extract(state).get(sbtBinaryVersion)

    // This triggers onLoad and onUnload "again", so we must persist a single s3 server instance
    // in-between session reloads, so the settingKey values are correct when the onLoad is ran.
    Project
      .extract(newState)
      .appendWithSession(
        Seq(
          ThisBuild / scriptedLaunchOpts ++= Seq(
            getServiceEndpoint(newState).map{ "-Dfm.sbt.s3.endpoint.serviceEndpoint=" + _ }.get,
            getSigningRegion(newState).map{ "-Dfm.sbt.s3.endpoint.signingRegion=" + _ }.get,
            getAWSAccessKeyId(newState).map{ "-Daws.accessKeyId=" + _ }.get,
            getAWSSecretKey(newState).map{ "-Daws.secretKey=" + _ }.get
          ),
          //ThisBuild / scriptedSbt := currentCrossBuildSbtVersion,
//          ThisBuild / pluginCrossBuild / sbtVersion := {
//            scalaBinaryVersion.value match {
//              case "2.10" => "0.13.18"
//              case "2.12" => "1.2.8"
//            }
//          },
          ThisBuild / sbtVersion in pluginCrossBuild := currentCrossBuildSbtVersion,
          //scriptedSbt := currentScriptedSbtVersion,
          //ThisBuild / sbtVersion := currentCrossBuildSbtVersion,
          //sbtBinaryVersion := currentSbtBinaryVersion
        ),// ++ currentScriptedSbtVersion.toSeq.map{ scriptedSbt := _ },
        newState
      )
  }

  private lazy val setupS3ServerTask: Def.Initialize[Task[Unit]] = Def.task {
    setupS3Server(state.value)
  }

  @tailrec private def deleteS3BucketObjects(client: AmazonS3, bucketName: String, objectListing: ObjectListing): Unit = {
    objectListing.getObjectSummaries.iterator().asScala.foreach { obj =>
      client.deleteObject(bucketName, obj.getKey)
    }
    if (objectListing.isTruncated) deleteS3BucketObjects(client, bucketName, client.listNextBatchOfObjects(objectListing))
  }

  private def setupS3Server(state: State): Unit = {
    getS3Client(state).foreach { client =>
      state.log.info("[S3ScriptedPlugin] Recreating s3 bucket: '"+TestBucket+"'")
      client.listBuckets().asScala.foreach{ bucket =>
        val bucketName = bucket.getName
        deleteS3BucketObjects(client, bucketName, client.listObjects(bucketName))
        client.deleteBucket(bucketName)
      }
      client.createBucket(TestBucket)
      val bucketPath = "test/s3/"+TestBucket
      val finder: PathFinder = file(bucketPath) ** "*.*"
      finder.get.filter{ _.isFile }.foreach { f: File =>
        val path = f.getAbsolutePath
        val key = path.drop(path.indexOf(bucketPath)+bucketPath.length+1)
        state.log.info("[S3ScriptedPlugin] ["+TestBucket+"] Adding "+key)

        val meta: ObjectMetadata = new ObjectMetadata()
        //if (serverSideEncryption) meta.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
        meta.setContentType("application/octet-stream")
        val req = new PutObjectRequest(TestBucket, key, f).withMetadata(meta)
        client.putObject(req)
      }

    }
  }

//  //onUnload in Global := (onUnload in Global).value andThen finish
//  private def finish(state: State): State = {
//    state
//      .get(s3ContainerKey)
//      .map { container =>
//        state.log.info(s"[S3ScriptedPlugin] Stopping Embedded S3 Server...")
//        container.stop()
//        state.log.info(s"[S3ScriptedPlugin] Stopping Embedded S3 Server...done")
//        state.remove(s3ContainerKey)
//      }
//      .getOrElse(state)
//  }
}