package org.pgscala.embedded

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.Arrays

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.compress.utils.IOUtils
import org.pgscala.embedded.Downloader.{PostDownloadHook, ProgressListener, ProgressUpdate}
import org.pgscala.embedded.PostgresDownload.VersionMetadata
import org.pgscala.embedded.Util._

private object PostgresDownload {
  private final val DownloadUrl = "https://get.enterprisedb.com/postgresql/"

  private case class VersionMetadata(size: Long, sha256: Array[Byte])

  private val metadata: Map[PostgresDownload, VersionMetadata] = {
    val bytes = IOUtils.toByteArray(getClass.getResourceAsStream("version-metadata.txt"))
    val sizeLines = new String(bytes, "ISO-8859-1").split('\n')
    (sizeLines map { sizeLine =>
      val verStr :: classifierStr :: sizeStr :: sha256Str :: Nil = sizeLine.split(';').toList
      val ver = PostgresVersion.values.find(_.toString == verStr).getOrElse(sys.error(s"PostgresVersion($verStr) is not defined in PostgresVersion.values"))
      val os = OS.values.find(_.toString == classifierStr).getOrElse(sys.error(s"OS($classifierStr) is not defined in OS.values"))
      val size = sizeStr.toLong
      val sha256 = javax.xml.bind.DatatypeConverter.parseHexBinary(sha256Str)
      PostgresDownload(ver, os) -> VersionMetadata(size, sha256)
    }).toMap
  }
}

case class PostgresDownload(version: PostgresVersion, os: OS) extends StrictLogging {
  val archiveName = s"postgresql-${version}-1-${os.name.classifier}${os.architecture.classifier}-binaries.${os.name.archiveMode}"
  val downloadUrl = PostgresDownload.DownloadUrl + archiveName

  private[this] lazy val resolvedMetadata: Option[VersionMetadata] =
    PostgresDownload.metadata.get(this)

  def resolveSize: Option[Long] = resolvedMetadata.map(_.size)
  def resolveSha256: Option[Array[Byte]] = resolvedMetadata.map(_.sha256)

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

    (resolveSha256, md.digest()) match {
      case (Some(sha256), digest) if Arrays.equals(sha256, digest) =>
        logger.debug("SHA256 digest successfully verified")
      case (Some(sha256), digest) =>
        logger.error("SHA256 digest mismatch!")
        sys.error(s"""SHA256 digest mismatch, expected "${bin2Hex(sha256)}", but got: "${bin2Hex(digest)}"""")
      case (_, digest) =>
        logger.debug(s"""SHA256 digest for download was not validated: "${bin2Hex(digest)}"""")
    }
  })

  def download(target: File): Unit = {
    Downloader(new java.net.URL(downloadUrl), target, resolveSize).download(progressLogger, sha256Check)
  }
}
