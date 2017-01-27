package org.pgscala.embedded

import java.net.HttpURLConnection

import org.apache.commons.io.IOUtils
import org.pgscala.embedded.PostgresSizeUpdater.DownloadAttributes

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

class PostgresDownloadSpec extends EmbeddedSpec {
  def is = s2"""
    check download sizes  $checkDownloadSizes
    re-resolve sizes      $reResolveSizes
"""

  def checkDownloadSizes() =
    for {
      ver <- PostgresVersion.values
      os <- OS.values
    } yield {
      val size = PostgresDownload(ver, os).resolveSize
      size.getOrElse(0L) must be_>(10L * 1024 * 1024)
    }

  def reResolveSizes() = PostgresSizeUpdater.downloadAttributes map { case DownloadAttributes(ver, os, size, _) =>
    PostgresDownload(ver, os).resolveSize ==== Some(size)
  }
}
