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

import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse, HeadObjectRequest, HeadObjectResponse, S3Exception}
import fm.sbt.S3URLHandler
import java.io.InputStream
import java.net.{HttpURLConnection, URL}
import java.time.format.DateTimeFormatter

object S3URLConnection {
  private val s3: S3URLHandler = new S3URLHandler()
}

/**
 * Implements an HttpURLConnection for compatibility with Coursier (https://github.com/coursier/coursier)
 */
final class S3URLConnection(url: URL) extends HttpURLConnection(url) {
  import S3URLConnection.s3

  sealed private trait S3Response extends AutoCloseable {
    def inputStream: Option[InputStream]
  }

  private case class HEADResponse(resp: HeadObjectResponse) extends S3Response {
    def close(): Unit = {}
    def inputStream: Option[InputStream] = None
  }

  private case class GETResponse(obj: ResponseInputStream[GetObjectResponse]) extends S3Response {
    def inputStream: Option[InputStream] = Option(obj)
    def close(): Unit = obj.close()
  }

  private[this] var response: Option[S3Response] = None

  def connect(): Unit = {
    val (client, bucket, key) = s3.getClientBucketAndKey(url)

    try {
      response = getRequestMethod.toLowerCase match {
        case "head" => Option(HEADResponse(client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())))
        case "get" => Option(GETResponse(client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())))
        case "post" => ???
        case "put" => ???
        case _ => throw new IllegalArgumentException("Invalid request method: "+getRequestMethod)
      }

      responseCode = if (response.isEmpty) 404 else 200
    } catch {
      case ex: S3Exception => responseCode = ex.statusCode()
    }

    // Also set the responseMessage (an HttpURLConnection field) for better compatibility
    responseMessage = statusMessageForCode(responseCode)
    connected = true
  }

  def usingProxy(): Boolean = false //Option(s3.getProxyConfiguration.getProxyHost).exists{ _ != "" }

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

    response match {
      case Some(HEADResponse(response)) =>
        field.toLowerCase match {
        case "content-type" => response.contentType()
        case "content-encoding" => response.contentEncoding()
        case "content-length" => response.contentLength().toString
        case "last-modified" => DateTimeFormatter.RFC_1123_DATE_TIME.format(response.lastModified())
        case _ => null // Should return null if no value for header
      }
      case Some(GETResponse(response)) =>
        field.toLowerCase match {
          case "content-type" => response.response()contentType()
          case "content-encoding" => response.response().contentEncoding()
          case "content-length" => response.response().contentLength().toString
          case "last-modified" => DateTimeFormatter.RFC_1123_DATE_TIME.format(response.response().lastModified())
          case _ => null // Should return null if no value for header
        }
      case None => null
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
