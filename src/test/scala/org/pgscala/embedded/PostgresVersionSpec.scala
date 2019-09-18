/*
package org.pgscala.embedded

import java.net.URI

import org.apache.commons.io.IOUtils

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

class PostgresVersionSpec extends EmbeddedSpec {
  def is = s2"""
    check latest versions  $checkLatestVersions
    re-resolve downloads   $reResolveDownloads
"""

  def checkLatestVersions =
    PostgresVersion.values === PostgresVersionUpdater.minorVersions

  private[this] def constructExpectedUrl(version: PostgresVersion, os: OS): String =
    PostgresDownload(version, os).downloadUrl

  def reResolveDownloads = (for {
    version <- PostgresVersion.values
    os <- OS.values
  } yield Future {
    val expectedUrl = Try { constructExpectedUrl(version, os) }
    val actualUrl = Try {
      val url = resolveActual(version, os)
      logger.trace("Resolved actual URL for version {} @ {}: {}", version, os, url)
      url
    }
    expectedUrl ==== actualUrl
  }) map {
    Await.result(_, 600 seconds)
  }
}
*/
