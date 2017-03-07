package org.pgscala.embedded

import java.io.File
import java.net.HttpURLConnection
import java.text.NumberFormat

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.io.FileUtils
import org.pgscala.embedded.Util._

import scala.util.Try

object PostgresSizeUpdater extends StrictLogging {
  case class DownloadAttributes(version: PostgresVersion, os: OS, size: Long, sha256: Array[Byte])

  lazy val downloadAttributes = (for {
    ver <- PostgresVersion.values.par
    os <- OS.values
  } yield {
    val download = PostgresDownload(ver, os)
    val url = download.downloadUrl
    val conn = new java.net.URL(url).openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("HEAD")
    try {
      val is = conn.getInputStream()
      try {
        val size = conn.getHeaderField("Content-Length").toLong
        val sha256 = download.sha256
        val nf = NumberFormat.getInstance()
        logger.debug(s"Retrieved attributes for $ver on OS $os - size: ${nf.format(size)} - sha256: ${bin2Hex(sha256)}")
        DownloadAttributes(ver, os, size, sha256)
      } finally {
        is.close()
      }
    } finally {
      conn.disconnect()
    }
  }).seq

  def main(args: Array[String]): Unit = {
    val file = new File(EmbeddedSpec.projectRoot, "src/main/resources/org/pgscala/embedded/version-metadata.txt")
    val oldBody = Try { FileUtils.readFileToString(file, "UTF-8") } getOrElse ""

    val sb = new StringBuilder
    for (DownloadAttributes(ver, os, size, sha256) <- downloadAttributes) {
      (sb ++= ver.toString += ';' ++= os.toString += ';') append size += ';' ++= bin2Hex(sha256) += '\n'
    }
    val newBody = sb.toString

    if (newBody != oldBody) {
      logger.info("Updated version-metadata.txt in {}", file)
      FileUtils.writeStringToFile(file, newBody, "UTF-8")
    } else {
      logger.debug("No need to update version-metadata.txt, as it contains the correct sizes")
    }
  }
}
