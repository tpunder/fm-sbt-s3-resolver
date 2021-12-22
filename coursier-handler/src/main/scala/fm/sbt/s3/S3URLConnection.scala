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
import com.amazonaws.services.s3.model.{ListObjectsRequest, ObjectMetadata}
import fm.sbt.S3URLHandler
import java.io.InputStream
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date

object S3URLConnection {
  private val s3: S3URLHandler = new S3URLHandler()
}

/**
 * Implements an HttpURLConnection for compatibility with Coursier (https://github.com/coursier/coursier)
 */
final class S3URLConnection(url: URL) extends HttpURLConnection(url) {
  import S3URLConnection.s3

  private[this] var response: Option[S3Response] = None

  def connect(): Unit = {
    val (client, bucket, key) = s3.getClientBucketAndKey(url)

    val res: Option[S3Response]  = try {
      getRequestMethod.toLowerCase match {
        case "head" =>
          url.getPath match {
            case p if p.endsWith("/maven-metadata.xml") || p.endsWith("/maven-metadata.xml.sha1")  =>
              val meta = new ObjectMetadata()
              meta.setLastModified(new Date)
              Option(HEADResponse(meta))
            case _ => Option(HEADResponse(client.getObjectMetadata(bucket, key)))
          }
        case "get" =>
          url.getPath match {
            // Directory Listing (HTML)
            case p if p.endsWith("/") =>
              // "/" as a delimiter to be returned only entries in the first level (no recursion),
              // with (pseudo) sub-directories indeed ending with a "/"
              val req = new ListObjectsRequest(bucket, key, null, "/", null)
              Option(GETListHTMLResponse(key, client.listObjects(req)))
            // Version info
            case p if p.endsWith("/maven-metadata.xml.sha1") =>
              S3MavenMetadata.getSha1(client, bucket, key).map{ contents =>
                TextResponse(contents.getBytes(StandardCharsets.UTF_8), new Date)
              }
            case p if p.endsWith("/maven-metadata.xml") =>
              S3MavenMetadata.getXml(client, bucket, key).map{ contents =>
                XmlResponse(contents.getBytes(StandardCharsets.UTF_8), new Date)
              }
            case _ => Option(GETResponse(client.getObject(bucket, key)))

          }
        case "post" => ???
        case "put" => ???
        case _ => throw new IllegalArgumentException("Invalid request method: "+getRequestMethod)
      }
    } catch {
      case ex: AmazonServiceException =>
        responseCode = ex.getStatusCode
        None
    }

    response = if (res.isDefined) res else response
    responseCode = if (response.isEmpty) 404 else 200
    // Also set the responseMessage (an HttpURLConnection field) for better compatibility
    responseMessage = statusMessageForCode(responseCode)
    connected = true
  }

  def usingProxy(): Boolean = Option(s3.getProxyConfiguration.getProxyHost).exists{ _ != "" }

  override def getInputStream: InputStream = {
    if (!connected) connect()
    response.flatMap{ _.inputStream }.orNull
  }

  override def getHeaderField(n: Int): String = {
    // n == 0 means you want the HTTP Status Line
    // This is called from HttpURLConnection.getResponseCode()
    if (n == 0 && responseCode != -1) {
      s"HTTP/1.0 $responseCode ${statusMessageForCode(responseCode)}"
    } else {
      super.getHeaderField(n)
    }
  }

  override def getHeaderField(field: String): String = {
    if (!connected) connect()

    field.toLowerCase match {
      case "content-type" => response.map{ _.meta.getContentType }.orNull
      case "content-encoding" => response.map{ _.meta.getContentEncoding }.orNull
      case "content-length" => response.map{ _.meta.getContentLength().toString }.orNull
      case "last-modified" => response.map{ _.meta.getLastModified }.map{ _.toInstant.atOffset(ZoneOffset.UTC) }.map{ DateTimeFormatter.RFC_1123_DATE_TIME.format }.orNull
      case _ => null // Should return null if no value for header
    }
  }

  override def disconnect(): Unit = {
    response.foreach{ _.close() }
  }

  private def statusMessageForCode(code: Int): String = {
    // I'm not sure if we care about any codes besides 200 and 404
    code match {
      case 200 => "OK"
      case 404 => "Not Found"
      case _   => "DUMMY"
    }
  }
}
