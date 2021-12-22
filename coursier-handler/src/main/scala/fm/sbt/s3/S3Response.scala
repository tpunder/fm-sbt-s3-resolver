package fm.sbt.s3

import com.amazonaws.services.s3.model.{ObjectListing, ObjectMetadata, S3Object}
import java.io.{ByteArrayInputStream, InputStream}
import java.util.Date
import scala.collection.JavaConverters._

private[s3] sealed trait S3Response extends AutoCloseable {
  def meta: ObjectMetadata
  def inputStream: Option[InputStream]
}

private[s3] case class HEADResponse(meta: ObjectMetadata) extends S3Response {
  def close(): Unit = {}
  def inputStream: Option[InputStream] = None
}

private[s3] case class GETResponse(obj: S3Object) extends S3Response {
  def meta: ObjectMetadata = obj.getObjectMetadata
  def inputStream: Option[InputStream] = Option(obj.getObjectContent())
  def close(): Unit = obj.close()
}

private[s3] sealed trait CustomResponse extends S3Response {
  def payload: Array[Byte]
  def meta: ObjectMetadata

  def inputStream: Option[InputStream] = Option(payload).map{ new ByteArrayInputStream(_) }
  def close(): Unit = {}
}

private[s3] case class TextResponse(payload: Array[Byte], lastModified: Date) extends CustomResponse {
  val meta: ObjectMetadata = {
    val m = new ObjectMetadata()
    m.setContentType("text/plain")
    m.setContentLength(inputStream.map{ _.available().toLong }.getOrElse(payload.length.toLong))
    m.setLastModified(lastModified)
    m
  }
}

private[s3] case class HtmlResponse(payload: Array[Byte], lastModified: Date) extends CustomResponse {
  val meta: ObjectMetadata = {
    val m = new ObjectMetadata()
    m.setContentType("text/html")
    m.setContentLength(inputStream.map{ _.available().toLong }.getOrElse(payload.length.toLong))
    m.setLastModified(lastModified)
    m
  }
}

private[s3] case class XmlResponse(payload: Array[Byte], lastModified: Date) extends CustomResponse {
  val meta: ObjectMetadata = {
    val m = new ObjectMetadata()
    m.setContentType("text/xml")
    m.setContentLength(inputStream.map{ _.available().toLong }.getOrElse(payload.length.toLong))
    m.setLastModified(lastModified)
    m
  }
}

private[s3] case class GETListHTMLResponse(path: String, obj: ObjectListing) extends S3Response {
  private val entries: Seq[String] =
    obj
      .getObjectSummaries
      .asScala
      .toSeq
      .map { summary =>
        val key = summary.getKey
        assert(key.startsWith(path))
        val name = key.stripPrefix(path)
        // TODO escape characters?
        s"""<li><a href="$name">$name</a></li>"""
    }

  private val listingInputStream: ByteArrayInputStream = {
    val page =
      s"""<!DOCTYPE html>
         |<html>
         |<head>
         |</head>
         |<body>
         |<ul>
         |${entries.mkString}
         |</ul>
         |</body>
         |</html>
           """.stripMargin

    val b = page.getBytes("UTF-8")
    new ByteArrayInputStream(b)
  }

  val meta: ObjectMetadata = {
    val m = new ObjectMetadata()
    m.setContentType("text/html")
    m.setContentLength(listingInputStream.available())
    //m.setLastModified(???)
    m
  }

  def inputStream: Option[InputStream] = {
    if (entries.isEmpty) None
    else Some(listingInputStream)
  }

  def close(): Unit = inputStream.map{ _.close() }
}