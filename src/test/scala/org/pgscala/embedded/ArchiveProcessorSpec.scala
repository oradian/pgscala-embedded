package org.pgscala.embedded

import java.io.{File, FileInputStream}
import java.util.zip.{ZipEntry, ZipInputStream}

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.{FileUtils, IOUtils}
import org.specs2.specification.BeforeAfterAll

import scala.collection.mutable.LinkedHashMap

class ArchiveProcessorSpec extends EmbeddedSpec with BeforeAfterAll{
  def is = s2"""
    ZIP Archive Processor
      can process empty              $zipCanProcessEmpty
      will remove empty directories  $zipWillRemoveEmptyDirectories
      clobbers duplicate files       $zipClobbersDuplicateFiles
      can filters out files          $zipCanFilterOutFiles
      can modifiy files              $zipCanModifyFiles

    TGZ Archive Processor
      can process empty              $tgzCanProcessEmpty
      will remove empty directories  $tgzWillRemoveEmptyDirectories
      can filters out files          $tgzCanFilterOutFiles
      can modifiy files              $tgzCanModifyFiles
"""

  /** everything after the FIRST dot is an extension */
  private[this] def getExt(name: String) = name.replaceFirst("""^.*?\.""", "")

  private[this] val targetArchives = new File(EmbeddedSpec.projectRoot, s"target/archives")

  private[this] implicit class PimpedArchive(val name: String) {
    def src = new File(EmbeddedSpec.projectRoot, s"src/test/resources/archives/${getExt(name)}/$name")
    def dst = new File(targetArchives, name)
  }

  private[this] def keepAll: ArchiveProcessor.EntryProcessor = (name, body) => Some(body)

  private[this] def getZipContents(file: File): Map[String, String] = {
    val zis = new ZipInputStream(new FileInputStream(file))
    try {
      val files = new LinkedHashMap[String, String]
      var ze: ZipEntry = null
      do {
        ze = zis.getNextEntry()
        if (ze != null) {
          val name = ze.getName
          val body = IOUtils.toString(zis, "ISO-8859-1")
          if (files.get(name).isEmpty) {
            files(name) = body
          } else {
            files(name + ".duplicate") = body
          }
        }
      } while (ze != null)
      files.toMap
    } finally {
      zis.close()
    }
  }

  def zipCanProcessEmpty =
    (getZipContents("void.zip".src) ==== Map()) and {
      ArchiveProcessor.filterArchive("void.zip".src, "void.zip".dst, keepAll)
      getZipContents("void.zip".dst) ==== Map()
    }

  def zipWillRemoveEmptyDirectories =
    (getZipContents("directories.zip".src) ====  Map("empty/" -> "", "non-empty/" -> "", "non-empty/uncompressed.bin" -> "")) and {
      ArchiveProcessor.filterArchive("directories.zip".src, "directories.zip".dst, keepAll)
      getZipContents("directories.zip".dst) ==== Map("non-empty/uncompressed.bin" -> "")
    }

  def zipClobbersDuplicateFiles =
    (getZipContents("aaa+aaa.zip".src) ==== Map("aaa.txt" -> "A" * 4000, "aaa.txt.duplicate" -> "A" * 4000)) and {
      ArchiveProcessor.filterArchive("aaa+aaa.zip".src, "aaa+aaa.zip".dst, keepAll)
      getZipContents("aaa+aaa.zip".dst) ====  Map("aaa.txt" -> "A" * 4000)
    }

  def zipCanFilterOutFiles =
    (getZipContents("filter.zip".src) ==== Map("keep/" -> "", "keep/nested.txt" -> "identical", "original.txt" -> "identical", "uncompressed.txt" -> "identical")) and {
      ArchiveProcessor.filterArchive("filter.zip".src, "filter.zip".dst, (name, body) => {
        if (name.startsWith("keep/")) Some(body) else None
      })
      getZipContents("filter.zip".dst) ====  Map("keep/nested.txt" -> "identical")
    }

  def zipCanModifyFiles =
    (getZipContents("filter.zip".src) ==== Map("keep/" -> "", "keep/nested.txt" -> "identical", "original.txt" -> "identical", "uncompressed.txt" -> "identical")) and {
      ArchiveProcessor.filterArchive("filter.zip".src, "filter.zip".dst, (name, body) => {
        if (name == "uncompressed.txt") Some("modified".getBytes("ISO-8859-1")) else Some(body)
      })
      getZipContents("filter.zip".dst) ====  Map("keep/nested.txt" -> "identical", "original.txt" -> "identical", "uncompressed.txt" -> "modified")
    }

  private[this] def getTgzContents(file: File): Map[String, String] = {
    val tgis = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(file)))
    try {
      val files = new LinkedHashMap[String, String]
      var tge: TarArchiveEntry = null
      do {
        tge = tgis.getNextTarEntry()
        if (tge != null) {
          val name = tge.getName
          val body = IOUtils.toString(tgis, "ISO-8859-1")
          if (files.get(name).isEmpty) {
            files(name) = body
          } else {
            files(name + ".duplicate") = body
          }
        }
      } while (tge != null)
      files.toMap
    } finally {
      tgis.close()
    }
  }

  def tgzCanProcessEmpty =
    (getTgzContents("void.tar.gz".src) ==== Map()) and {
      ArchiveProcessor.filterArchive("void.tar.gz".src, "void.tar.gz".dst, keepAll)
      getTgzContents("void.tar.gz".dst) ==== Map()
    }

  def tgzWillRemoveEmptyDirectories =
    (getTgzContents("directories.tar.gz".src) ====  Map("empty/" -> "", "non-empty/" -> "", "non-empty/uncompressed.bin" -> "")) and {
      ArchiveProcessor.filterArchive("directories.tar.gz".src, "directories.tar.gz".dst, keepAll)
      getTgzContents("directories.tar.gz".dst) ==== Map("non-empty/uncompressed.bin" -> "")
    }

  def tgzCanFilterOutFiles =
    (getTgzContents("filter.tar.gz".src) ==== Map("keep/" -> "", "keep/nested.txt" -> "identical", "original.txt" -> "identical", "uncompressed.txt" -> "identical")) and {
      ArchiveProcessor.filterArchive("filter.tar.gz".src, "filter.tar.gz".dst, (name, body) => {
        if (name.startsWith("keep/")) Some(body) else None
      })
      getTgzContents("filter.tar.gz".dst) ====  Map("keep/nested.txt" -> "identical")
    }

  def tgzCanModifyFiles =
    (getTgzContents("filter.tar.gz".src) ==== Map("keep/" -> "", "keep/nested.txt" -> "identical", "original.txt" -> "identical", "uncompressed.txt" -> "identical")) and {
      ArchiveProcessor.filterArchive("filter.tar.gz".src, "filter.tar.gz".dst, (name, body) => {
        if (name == "uncompressed.txt") Some("modified".getBytes("ISO-8859-1")) else Some(body)
      })
      getTgzContents("filter.tar.gz".dst) ====  Map("keep/nested.txt" -> "identical", "original.txt" -> "identical", "uncompressed.txt" -> "modified")
    }

  override def beforeAll(): Unit = {
    FileUtils.deleteDirectory(targetArchives)
    targetArchives.mkdirs()
  }

  override def afterAll(): Unit = () // no cleanup so that we can debug
}
