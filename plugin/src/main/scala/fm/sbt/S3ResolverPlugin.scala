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

import java.net.{URL, URLStreamHandler, URLStreamHandlerFactory}
import com.amazonaws.auth.AWSCredentialsProvider
import org.apache.ivy.util.url.{URLHandlerDispatcher, URLHandlerRegistry}
import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import scala.util.Try

/**
 * All this does is register the s3:// url handler with the JVM and IVY
 */
object S3ResolverPlugin extends AutoPlugin with S3ResolverPluginCompat {

  object autoImport extends S3Implicits with S3ResolverPluginCompat.Keys {
    lazy val s3CredentialsProvider: SettingKey[Function1[String, AWSCredentialsProvider]] = {
      SettingKey[Function1[String, AWSCredentialsProvider]]("s3CredentialsProvider", "AWS credentials provider to access S3")
    }

    lazy val showS3Credentials: InputKey[Unit] = {
      InputKey[Unit]("showS3Credentials", "Just outputs credentials that are loaded by the s3credentials provider")
    }
  }

  import autoImport._

  // This plugin will load automatically
  override def trigger: PluginTrigger = allRequirements

  override protected def s3PluginVersion: String = BuildInfo.version
  override protected def s3PluginGroupId: String = BuildInfo.organization

  override def projectSettings: Seq[Setting[_]] = compatProjectSettings ++ Seq(
    s3CredentialsProvider := S3CredentialsProvider.defaultCredentialsProviderChain,
    showS3Credentials := {
      val log = state.value.log

      spaceDelimited("<arg>").parsed match {
        case bucket :: Nil =>
          val provider: AWSCredentialsProvider = s3CredentialsProvider.value(bucket)

          Try {
            Option(provider.getCredentials) match {
              case Some(awsCredentials) =>
                log.info("Found following AWS credentials:")
                log.info("Access key: " + awsCredentials.getAWSAccessKeyId)
                log.info("Secret key: " + awsCredentials.getAWSSecretKey)

              case None =>
                log.error("Couldn't find credentials for bucked: %s" format bucket)
            }
          } recover { case e: Exception =>
            log.error(e.getMessage)
          }

        case Nil =>
          log.error("Bucket name not given")

        case _ =>
          log.error("Too many arguments for showS3Credentials")
      }
    },
    onLoad in Global := (onLoad in Global).value andThen { state: State =>
      def info: String => Unit = state.log.info(_)
      def debug: String => Unit = state.log.debug(_)

      // We need s3:// URLs to work without throwing a java.net.MalformedURLException
      // which means installing a dummy URLStreamHandler.  We only install the handler
      // if it's not already installed (since a second call to URL.setURLStreamHandlerFactory
      // will fail).
      try {
        new URL("s3://example.com")
        debug("The s3:// URLStreamHandler is already installed")
      } catch {
        // This means we haven't installed the handler, so install it
        case _: java.net.MalformedURLException =>
          info("Installing the s3:// URLStreamHandler via java.net.URL.setURLStreamHandlerFactory")
          URL.setURLStreamHandlerFactory(S3URLStreamHandlerFactory)
      }

      //
      // This sets up the Ivy URLHandler for s3:// URLs
      //   See: coursier.cache.protocol.S3Handler for coursier compatibility
      //
      val dispatcher: URLHandlerDispatcher = URLHandlerRegistry.getDefault match {
        // If the default is already a URLHandlerDispatcher then just use that
        case disp: URLHandlerDispatcher =>
          debug("Using the existing Ivy URLHandlerDispatcher to handle s3:// URLs")
          disp
        // Otherwise create a new URLHandlerDispatcher
        case default =>
          info("Creating a new Ivy URLHandlerDispatcher to handle s3:// URLs")
          val disp: URLHandlerDispatcher = new URLHandlerDispatcher()
          disp.setDefault(default)
          URLHandlerRegistry.setDefault(disp)
          disp
      }

      // Register (or replace) the s3 handler
      dispatcher.setDownloader("s3", new S3URLHandler)

      val extracted: Extracted = Project.extract(state)

      S3CredentialsProvider
        .registerBucketCredentialsProvider(
          extracted.getOpt(s3CredentialsProvider)
            .getOrElse(S3CredentialsProvider.defaultCredentialsProviderChain)
        )

      state
    }
  )

  private object S3URLStreamHandlerFactory extends URLStreamHandlerFactory {
    def createURLStreamHandler(protocol: String): URLStreamHandler = protocol match {
      case "s3" => new fm.sbt.s3.Handler
      case _    => null
    }
  }
}
