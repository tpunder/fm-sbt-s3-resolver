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

import java.net.{URL, URLConnection, URLStreamHandler}

/**
 * For normal SBT usage this is a dummy URLStreamHandler so that s3:// URLs can be created without throwing a
 * java.net.MalformedURLException.  However for something like Coursier (https://github.com/coursier/coursier)
 * this needs to be implemented since it doesn't use the normal SBT resolving mechanisms.
 */
final class Handler extends URLStreamHandler {
  def openConnection(url: URL): URLConnection = new S3URLConnection(url)
}