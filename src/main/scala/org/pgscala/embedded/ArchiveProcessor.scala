package org.pgscala.embedded

import java.io._
import java.nio.charset.StandardCharsets._
import java.nio.file.attribute.FileTime
import java.util.zip._

import org.apache.commons.compress.archivers.tar.{TarArchiveInputStream, TarArchiveOutputStream}
import org.apache.commons.compress.compressors.gzip.{GzipCompressorInputStream, GzipCompressorOutputStream}
import org.apache.commons.io.IOUtils

import scala.annotation.tailrec

/** ArchiveProcessor has the ability to filter out files completely
  * or modify their content while streaming the archive.
  *
  * In case archive entries were modified, their checksum and
  * modifiedAt timestamp will be mutated. */
object ArchiveProcessor {
  type EntryProcessor = (String, Array[Byte]) => Option[Array[Byte]]

  def filterArchive(in: File, out: File, filter: EntryProcessor): Unit = {
    val is = new BufferedInputStream(new FileInputStream(in))
    val os = new BufferedOutputStream(new FileOutputStream(out))

    // peek into the archive to detect archive type via magic number (first 2 bytes)
    val magicNumber = {
      is.mark(2)
      val header = new Array[Byte](2)
      is.read(header)
      is.reset()
      new String(header, ISO_8859_1)
    }

    magicNumber match {
      case "PK" =>
        filterZipArchive(is, os, filter)
      case "\u001f\u008b" =>
        filterTarGZipArchive(is, os, filter)
      case _ =>
        sys.error(s"""Could not detect archive type from filename - needed ".zip" or ".tar.gz""")
    }
  }

  private[this] def filterZipArchive(is: InputStream, os: OutputStream, filter: EntryProcessor): Unit = {
    val zis = new ZipInputStream(is)
    try {
      val zos = new ZipOutputStream(os)
      try {
        @tailrec
        def repackage(): Unit = zis.getNextEntry match {
          case null => // end of archive

          case ze if ze.isDirectory =>
            repackage() // do not repackage folders

          case ze =>
            val name = ze.getName
            val body = IOUtils.toByteArray(zis)
            filter(name, body) foreach { newBody =>
              val ne = new ZipEntry(ze)
              if (newBody ne body) { // optionally leave entry as-is
                ne.setSize(newBody.length.toLong)
                ne.setCrc {
                  val tmp = new CRC32()
                  tmp.update(newBody)
                  tmp.getValue
                }
                ne.setLastModifiedTime(FileTime.fromMillis(System.currentTimeMillis))
              }
              ne.setCompressedSize(-1L)
              try {
                zos.putNextEntry(ne)
                zos.write(newBody)
              } catch {
                case e: ZipException => if (e.getMessage != "duplicate entry: " + name) throw e
              }
            }
            repackage()
        }
        repackage()
      } finally {
        zos.close()
      }
    } finally {
      zis.close()
    }
  }

  private[this] def filterTarGZipArchive(is: InputStream, os: OutputStream, filter: EntryProcessor): Unit = {
    val tgis = new TarArchiveInputStream(new GzipCompressorInputStream(is))
    try {
      val tgos = new TarArchiveOutputStream(new GzipCompressorOutputStream(os))
      tgos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
      try {
        @tailrec
        def repackage(): Unit = tgis.getNextTarEntry match {
          case null => // end of archive

          case tge if tge.isDirectory =>
            repackage() // do not repackage folders

          case tge =>
            val name = tge.getName
            val body = IOUtils.toByteArray(tgis)
            filter(name, body) foreach { newBody =>
              if (newBody ne body) { // optionally leave entry as-is
                tge.setSize(newBody.length.toLong)
                tge.setModTime(System.currentTimeMillis)
              }
              tgos.putArchiveEntry(tge)
              tgos.write(newBody)
              tgos.closeArchiveEntry()
            }
            repackage()
        }
        repackage()
      } finally {
        tgos.close()
      }
    } finally {
      tgis.close()
    }
  }
}
