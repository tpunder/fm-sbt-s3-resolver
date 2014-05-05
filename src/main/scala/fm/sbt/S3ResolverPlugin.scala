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

import sbt._
import Keys._

import java.net.{URL, URLStreamHandler, URLStreamHandlerFactory}
import org.apache.ivy.util.url.{URLHandlerDispatcher, URLHandlerRegistry}

/**
 * All this does is register the s3:// url handler with the JVM and IVY
 */
object S3ResolverPlugin extends Plugin {
  //
  // This *should* work but it looks like SBT is doing some multi class loader stuff
  // because the class loader used to load java.net.URL doesn't see fm.sbt.s3.Handler.
  // So instead we use the URL.setURLStreamHandlerFactory method below which only works
  // if nobody else has called URL.setURLStreamHandlerFactory.
  //
  /*
  // See JavaDocs for the java.net.URL Constructors
  private def protocolPkgKey: String = "java.protocol.handler.pkgs"
  private def existingProtocolHandlers: Option[String] = Option(System.getProperty(protocolPkgKey))
  
  // Register our S3URLStreamHandler so that we can create instances of URLs with an "s3" Protocol
  System.setProperty(protocolPkgKey, "fm.sbt"+existingProtocolHandlers.map{ "|"+_ }.getOrElse(""))
  */
 
  private object S3URLStreamHandlerFactory extends URLStreamHandlerFactory {
    def createURLStreamHandler(protocol: String): URLStreamHandler = protocol match {
      case "s3" => new fm.sbt.s3.Handler
      case _    => null
    }
  }
  
  // This allows us to create s3:// urls without throwing a MalformedURLException
  URL.setURLStreamHandlerFactory(S3URLStreamHandlerFactory)
  
  // This sets up the Ivy URLHandler for s3:// urls
  private val dispatcher: URLHandlerDispatcher = new URLHandlerDispatcher
  dispatcher.setDefault(URLHandlerRegistry.getDefault())
  dispatcher.setDownloader("s3", new S3URLHandler)
  
  URLHandlerRegistry.setDefault(dispatcher)
}
