package coursier.cache.protocol

import java.net.{URLStreamHandler, URLStreamHandlerFactory}

/**
 * This class must be named coursier.cache.protocol.{protocol.capitalize}Handler
 */
class S3Handler extends URLStreamHandlerFactory {
  def createURLStreamHandler(protocol: String): URLStreamHandler = protocol match {
    case "s3" => new fm.sbt.s3.Handler
    case _    => null
  }
}