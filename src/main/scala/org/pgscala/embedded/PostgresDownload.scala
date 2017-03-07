package org.pgscala.embedded

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.Arrays

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.io.IOUtils
import org.pgscala.embedded.Downloader.{PostDownloadHook, ProgressListener, ProgressUpdate}
import org.pgscala.embedded.Util._

case class PostgresDownload(version: PostgresVersion, os: OS) extends StrictLogging {
  val (patch, size, sha256): (Int, Long, Array[Byte]) = {
    val bytes = IOUtils.toByteArray(getClass.getResourceAsStream("version-metadata.txt"))
    val lineVersion = version.toString + '-'
    val sizeLine = new String(bytes, "ISO-8859-1").split('\n') find { sizeLine =>
      sizeLine.startsWith(lineVersion) &&
      sizeLine.contains(';' + os.toString + ';')
    } getOrElse(sys.error("Could not resolve metadata for: " + this))

    val Array(verPatchStr, _, sizeStr, sha256Str) = sizeLine.split(';')
    val patch = verPatchStr.substring(lineVersion.length).toInt
    val size = sizeStr.toLong
    val sha256 = javax.xml.bind.DatatypeConverter.parseHexBinary(sha256Str)
    (patch, size, sha256)
  }

  val archiveName = s"postgresql-${version}-${patch}-${os.name.classifier}${os.architecture.classifier}-binaries.${os.name.archiveMode}"
  val downloadUrl = "https://get.enterprisedb.com/postgresql/" + archiveName

  private[this] val progressLogger: ProgressListener = Some((progressUpdate: ProgressUpdate) => {
    logger.debug(s"Downloading ${archiveName} - ${progressUpdate.soFar}/${progressUpdate.size} ...")
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

    val digest = md.digest()
    if (Arrays.equals(sha256, digest)) {
      logger.debug("SHA256 digest successfully verified")
    } else {
      sys.error(s"""SHA256 digest mismatch, expected "${bin2Hex(sha256)}", but got: "${bin2Hex(digest)}"""")
    }
  })

  def download(target: File): Unit = {
    Downloader(new java.net.URL(downloadUrl), target, size).download(progressLogger, sha256Check)
  }
}
