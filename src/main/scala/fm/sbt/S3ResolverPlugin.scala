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

import java.net.{URL, URLStreamHandler, URLStreamHandlerFactory}
import org.apache.ivy.util.Message
import org.apache.ivy.util.url.{URLHandlerDispatcher, URLHandlerRegistry}

/**
 * All this does is register the s3:// url handler with the JVM and IVY
 */
object S3ResolverPlugin extends AutoPlugin {
  object autoImport extends S3Implicits
  
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
  // which means installing a dummy URLStreamHandler.  We only install the handler
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
  
  
  //
  // More hackery to make directory listings work for s3:// URLs
  //
// {
//     import java.lang.reflect.Method
//     
//     // The class laoder that loaded this plugin
//     val fmClassLoader: ClassLoader = getClass.getClassLoader
//     
//     // The class loader that loaded (or will load) the Ivy Classes
//     //val ivyClassLoader: ClassLoader = classOf[org.apache.ivy.core.settings.XmlSettingsParser].getClassLoader
//     val ivyClassLoader: ClassLoader = fmClassLoader // classOf[org.apache.ivy.plugins.repository.url.URLRepository].getClassLoader
//     
//     // Private ClassLoader methods that we need for this to work
//     val findLoadedClass: Method = classOf[ClassLoader].getDeclaredMethod("findLoadedClass", classOf[String])
//     val getResourceAsStream: Method = classOf[ClassLoader].getDeclaredMethod("getResourceAsStream", classOf[String])
//     val defineClass: Method = classOf[ClassLoader].getDeclaredMethod("defineClass", classOf[String], classOf[Array[Byte]], Integer.TYPE, Integer.TYPE)
//     
//     // We need access to all of these methods
//     findLoadedClass.setAccessible(true)
//     getResourceAsStream.setAccessible(true)
//     defineClass.setAccessible(true)
//     
//     val s3URLRepositoryClass: String = "fm.ivy.S3URLRepository"
//     val urlResolverClass: String = "org.apache.ivy.plugins.resolver.URLResolver"
//     
//     val isPatched: Boolean = null != findLoadedClass.invoke(ivyClassLoader, s3URLRepositoryClass)
//     
//     if (isPatched) {
//       info(s"$urlResolverClass has already been patched")
//     } else {
//       require(null == findLoadedClass.invoke(ivyClassLoader, urlResolverClass), s"$urlResolverClass was already loaded, can't overwrite it!!!")
//       
//       // We need to inject our version of the URLResolver/S3URLRepository into the ivy class loader
//       // but we need to read them from the plugin class loader so we can see them
//       val s3ClassBytes: Array[Byte] = toBytes(getResourceAsStream.invoke(fmClassLoader, s"${s3URLRepositoryClass.replace('.','/')}.class").asInstanceOf[InputStream])
//       val resolverClassBytes: Array[Byte] = toBytes(getResourceAsStream.invoke(fmClassLoader, s"${urlResolverClass.replace('.','/')}.class").asInstanceOf[InputStream])
//       
//       defineClass.invoke(ivyClassLoader, s3URLRepositoryClass, s3ClassBytes, (0: Integer), (s3ClassBytes.length: Integer))
//       defineClass.invoke(ivyClassLoader, urlResolverClass, resolverClassBytes, (0: Integer), (resolverClassBytes.length: Integer))
//       
//       info(s"Patched $urlResolverClass to work with s3:// URLs")
//     }
//   }
  
  // Not sure how to log using SBT so I'm using Ivy's Message class
  private def info(msg: String): Unit = Message.info(msg)
  
  // private def toBytes(is: InputStream): Array[Byte] = {
  //   require(null != is, "null InputStream!")
  //   
  //   val result = new ByteArrayOutputStream
  //   val buf = new Array[Byte](1024)
  //   readInputStream(is, buf, result)
  //   is.close()
  //   result.toByteArray
  // }
  // 
  // @tailrec
  // private def readInputStream(is: InputStream, buf: Array[Byte], result: ByteArrayOutputStream): Unit = {
  //   val num: Int = is.read(buf)
  //   if (num == -1) return
  //   result.write(buf, 0, num)
  //   readInputStream(is, buf, result)
  // }
}
