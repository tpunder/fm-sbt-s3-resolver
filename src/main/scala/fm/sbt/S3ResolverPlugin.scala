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
import org.apache.ivy.util.Message
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
  
  // We need s3:// URLs to work without throwing a java.net.MalformedURLException
  // which means installeing a dummy URLStreamHandler.  We only install the handler
  // if it's not already installed (since a second call to URL.setURLStreamHandlerFactory
  // will fail).
  try {
    new URL("s3://example.com")
    info("The s3:// URLStreamHandler is already installed")
  } catch {
    // This means we haven't installed the handler, so install it
    case _: java.net.MalformedURLException => 
      info("Installing the s3:// URLStreamHandler via java.net.URL.setURLStreamHandlerFactory")
      URL.setURLStreamHandlerFactory(S3URLStreamHandlerFactory)
  }
  
  //
  // This sets up the Ivy URLHandler for s3:// URLs
  //
  private val dispatcher: URLHandlerDispatcher = URLHandlerRegistry.getDefault() match {
    // If the default is already a URLHandlerDispatcher then just use that
    case disp: URLHandlerDispatcher =>
      info("Using the existing Ivy URLHandlerDispatcher to handle s3:// URLs")
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
  
  // Not sure how to log using SBT so I'm using Ivy's Message class
  private def info(msg: String): Unit = Message.info(msg)
}
