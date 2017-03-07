package org.pgscala.embedded

import java.io._
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import java.util.zip._

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.IOUtils

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
          unpack() // do not create empty folders

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
          if (Util.isUnix) {
            // TODO: this means we're on a Mac, unpacking a zip archive
            // We need to read the actual permissions form the "extra"
            // property of each zip entry and convert them insteaf of
            // flipping on all permissions
            val allPermissions = EnumSet.allOf(classOf[PosixFilePermission])
            Files.setPosixFilePermissions(target.toPath, allPermissions)
          }
          unpack()
      }
      unpack()
    } finally {
      zis.close()
    }
  }

  private[this] val PosixFilePermissions = PosixFilePermission.values.reverse
  private[this] val PosixFileIndices = PosixFilePermissions.indices.toSet
  private[this] def mode2Posix(mode: Int): Set[PosixFilePermission] =
    PosixFileIndices filter { i =>
      val mask = 1 << i
      (mode & mask) == mask
    } map PosixFilePermissions

  private[this] def unpackTarGZipArchive(is: InputStream, targetFolder: File): Unit = {
    val tgis = new TarArchiveInputStream(new GzipCompressorInputStream(is))
    try {
      @tailrec
      def unpack(): Unit = tgis.getNextTarEntry() match {
        case null => // end of archive

        case tge if tge.isDirectory =>
          unpack() // do not create empty folders

        case tge =>
          val name = tge.getName
          val target = new File(targetFolder, name)
          if (!target.getParentFile.isDirectory) {
            target.getParentFile.mkdirs()
          }
          if (Util.isUnix && tge.isSymbolicLink) {
            val destination = tge.getLinkName
            Files.createSymbolicLink(target.toPath, new File(destination).toPath)
            // TODO: Change symlink date (setting lastModified does not affect it)
          } else {
            val body = IOUtils.toByteArray(tgis)
            val fos = new FileOutputStream(target)
            try {
              fos.write(body)
            } finally {
              fos.close()
            }
            target.setLastModified(tge.getModTime.getTime)
            if (Util.isUnix) {
              import scala.collection.JavaConverters._
              Files.setPosixFilePermissions(target.toPath, mode2Posix(tge.getMode).asJava)
            }
          }
          unpack()
      }
      unpack()
    } finally {
      tgis.close()
    }
  }
}
