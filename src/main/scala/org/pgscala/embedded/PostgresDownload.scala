package org.pgscala.embedded

import java.io.{BufferedReader, File, InputStreamReader}
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets._
import java.security.MessageDigest

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.codec.binary.Hex
import org.pgscala.embedded.Downloader.{PostDownloadHook, ProgressListener, ProgressUpdate}

import scala.annotation.tailrec

case class PostgresDownload(
    url: URI,
    archiveName: String,
    size: Option[Long],
    sha256: Option[String]
  ) extends StrictLogging {

  private[this] val progressLogger: ProgressListener = Some((progressUpdate: ProgressUpdate) => {
    logger.debug(s"Downloading $archiveName - ${progressUpdate.soFar}/${progressUpdate.size} ...")
  })

  private[this] val sha256Check: PostDownloadHook = Some((fileChannel: FileChannel, size: Long) => {
    val buffer = ByteBuffer.allocateDirect(64 * 1024)
    val md = MessageDigest.getInstance("SHA-256")
    fileChannel.position(0L)

    var index = 0L
    while (index < size) {
      val read = fileChannel.read(buffer)
      buffer.flip()
      md.update(buffer)
      buffer.clear()
      index += read
    }

    val digest = Hex.encodeHexString(md.digest())
    sha256 foreach { hash =>
      if (hash equalsIgnoreCase digest) {
        logger.debug("SHA256 digest successfully verified")
      } else {
        sys.error(s"""SHA256 digest mismatch, expected "$hash", but got: "$digest"""")
      }
    }
  })

  def download(target: File): Unit = {
    val expectedLength = size getOrElse Downloader.resolveSize(url)
    Downloader(url, target, expectedLength).download(progressLogger, sha256Check)
  }
}

object PostgresDownload {
  @tailrec private[this] def seekVersion(reader: BufferedReader, versionLine: String): Option[String] =
    reader.readLine() match {
      case null => None
      case line if line startsWith versionLine => Some(line)
      case _ => seekVersion(reader, versionLine)
    }

  def apply(version: PostgresVersion, os: OS): PostgresDownload = {
    val metadata = new BufferedReader(new InputStreamReader(getClass.getResourceAsStream("version-metadata.txt"), UTF_8))
    val line = seekVersion(metadata, s"$version;$os;")
      .getOrElse(sys.error(s"Could not resolve download metadata for PostgreSQL version '$version' and OS '$os'"))

    val entries = line.split(";", -1)
    // first two entries are already matched - version and OS
    val url = entries(2)
    val archiveName = url.replaceFirst(".*/", "")
    val size = Option(entries(3)).filter(_.nonEmpty).map(_.toLong)
    val sha256 = Option(entries(4)).filter(_.nonEmpty)
    PostgresDownload(new URI(url), archiveName, size, sha256)
  }
}
