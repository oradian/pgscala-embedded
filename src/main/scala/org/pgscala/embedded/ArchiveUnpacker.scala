package org.pgscala.embedded

import java.io._
import java.nio.file.attribute.FileTime
import java.util.zip._

import ch.qos.logback.core.util.FileUtil
import org.apache.commons.compress.archivers.tar.{TarArchiveInputStream, TarArchiveOutputStream}
import org.apache.commons.compress.compressors.gzip.{GzipCompressorInputStream, GzipCompressorOutputStream}
import org.apache.commons.compress.utils.IOUtils

import scala.annotation.tailrec

/** ArchiveProcessor has the ability to filter out files completely
  * or modify their content while streaming the archive.
  *
  * In case archive entries were modified, their checksum and
  * modifiedAt timestamp will be mutated. */
object ArchiveUnpacker {
  def unpack(archive: File, targetFolder: File): Unit = {
    val is = new BufferedInputStream(new FileInputStream(archive))

    // peek into the archive to detect archive type via magic number (first 2 bytes)
    val magicNumber = {
      is.mark(2)
      val header = new Array[Byte](2)
      is.read(header)
      is.reset()
      new String(header, "ISO-8859-1")
    }

    if (!targetFolder.isDirectory) {
      targetFolder.mkdirs()
    }

    magicNumber match {
      case "PK" =>
        unpackZipArchive(is, targetFolder)
      case "\u001f\u008b" =>
        unpackTarGZipArchive(is, targetFolder)
      case _ =>
        sys.error(s"""Could not detect archive type from filename - needed ".zip" or ".tar.gz""")
    }
  }

  private[this] def unpackZipArchive(is: InputStream, targetFolder: File): Unit = {
    val zis = new ZipInputStream(new BufferedInputStream(is))
    try {
      @tailrec
      def unpack(): Unit = zis.getNextEntry() match {
        case null => // end of archive

        case ze if ze.isDirectory =>
          unpack() // do not unpack folders

        case ze =>
          val name = ze.getName
          val body = IOUtils.toByteArray(zis)
          val target = new File(targetFolder, name)
          if (!target.getParentFile.isDirectory) {
            target.getParentFile.mkdirs()
          }
          val fos = new FileOutputStream(target)
          try {
            fos.write(body)
          } finally {
            fos.close()
          }
          target.setLastModified(ze.getLastModifiedTime.toMillis)
          unpack()
      }
      unpack()
    } finally {
      zis.close()
    }
  }

  private[this] def unpackTarGZipArchive(is: InputStream, targetFolder: File): Unit = {
    val tgis = new TarArchiveInputStream(new GzipCompressorInputStream(is))
    try {
      @tailrec
      def unpack(): Unit = tgis.getNextTarEntry() match {
        case null => // end of archive

        case tge if tge.isDirectory =>
          unpack() // do not repackage folders

        case tge =>
          val name = tge.getName
          val body = IOUtils.toByteArray(tgis)
          val target = new File(targetFolder, name)
          if (!target.getParentFile.isDirectory) {
            target.getParentFile.mkdirs()
          }
          val fos = new FileOutputStream(target)
          try {
            fos.write(body)
          } finally {
            fos.close()
          }

          target.setLastModified(tge.getModTime.toInstant.getEpochSecond)
          unpack()
      }
      unpack()
    } finally {
      tgis.close()
    }
  }
}
