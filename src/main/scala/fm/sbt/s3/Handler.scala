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

import java.io.InputStream
import java.net.{URL, URLConnection, URLStreamHandler}

import com.amazonaws.services.s3.AmazonS3Client

/**
 * This is a dummy URLStreamHandler so that s3:// URLs can be created without throwing a
 * java.net.MalformedURLException.
 *
 * But it provides an implementation just in case someone actually needs to use an S3 URL
 * handler.
 */
final class Handler extends URLStreamHandler {
  val s3Client = new AmazonS3Client()

  def openConnection(url: URL): URLConnection = {
    new URLConnection(url) {
      override def connect(): Unit = ()

      override def getInputStream: InputStream = {
        val bucketName = url.getHost
        val key = url.getPath
        s3Client.getObject(bucketName, key).getObjectContent
      }
    }
  }
}
