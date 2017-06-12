package org.pgscala.embedded

import java.io.File
import java.net.{HttpURLConnection, URL}
import java.security.MessageDigest
import java.text.NumberFormat
import java.util.regex.Pattern

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.io.FileUtils
import org.pgscala.embedded.Util._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration.Inf
import scala.util.Try

object PostgresSizeUpdater extends StrictLogging {
  case class DownloadAttributes(version: PostgresVersion, variant: Int, os: OS, size: Long, sha256: Array[Byte])

  private[this] val resolver = new PostgresVersionSpec()

  private[this] def makePostgresDownload(_ver: PostgresVersion, _os: OS): PostgresDownloadBase = try {
    PostgresDownload(_ver, _os)
  } catch {
    case _: Throwable => new {
      val version = _ver
      val os = _os

      val url = {
        val resolved = resolver.resolveActual(_ver, _os)
        logger.info(s"Could not retrieve (${_ver}, ${_os}) from metadata, downloading: $resolved ...")
        resolved
      }

      val variant = {(
          Pattern.quote(s"postgresql-${_ver}-")
        + "(\\d+)"
        + Pattern.quote(s"-${os.name.classifier}${os.architecture.classifier}-binaries.${os.name.archiveMode}"))
          .r.findFirstMatchIn(url)
          .getOrElse(sys.error(s"Could not decode variant from url: $url"))
          .group(1).toInt
      }

      val (size, sha256) = {
        val buffer = new Array[Byte](65536)
        val is = new URL(url).openStream()
        try {
          val md = MessageDigest.getInstance("SHA-256")
          var length = 0L
          while ({
            val read = is.read(buffer)
            if (read != -1) {
              md.update(buffer, 0, read)
              length += read
              true
            } else {
              false
            }
          }) {}
          (length, md.digest())
        } finally {
          is.close()
        }
      }
    } with PostgresDownloadBase
  }

  implicit val executionContext = EmbeddedSpec.executionContext

  lazy val downloadAttributes = Await.result(Future.sequence(for {
    ver <- PostgresVersion.values.take(7)
    os <- OS.values
  } yield Future {
    val download = makePostgresDownload(ver, os)
    val url = download.downloadUrl
    val conn = new java.net.URL(url).openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("HEAD")
    try {
      val is = conn.getInputStream()
      try {
        val size = conn.getHeaderField("Content-Length").toLong
        val sha256 = download.sha256
        val nf = NumberFormat.getInstance()
        logger.debug(s"Retrieved attributes for $ver-${download.variant} on OS $os - size: ${nf.format(size)} - sha256: ${bin2Hex(sha256)}")
        DownloadAttributes(ver, download.variant, os, size, sha256)
      } finally {
        is.close()
      }
    } finally {
      conn.disconnect()
    }
  }), Inf)

  def main(args: Array[String]): Unit = {
    val file = new File(EmbeddedSpec.projectRoot, "src/main/resources/org/pgscala/embedded/version-metadata.txt")
    val oldBody = Try { FileUtils.readFileToString(file, "UTF-8") } getOrElse ""

    val sb = new StringBuilder
    for (DownloadAttributes(ver, variant, os, size, sha256) <- downloadAttributes) {
      ((sb ++= ver.toString += '-') append variant += ';' ++= os.toString += ';') append size += ';' ++= bin2Hex(sha256) += '\n'
    }
    val newBody = sb.toString

    if (newBody != oldBody) {
      logger.info("Updated version-metadata.txt in {}", file)
      FileUtils.writeStringToFile(file, newBody, "UTF-8")
    } else {
      logger.debug("No need to update version-metadata.txt, as it contains the correct sizes")
    }

    EmbeddedSpec.shutdown()
  }
}
