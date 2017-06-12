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

  def resolveActual(version: PostgresVersion, os: OS): String = {
    val pgVersionNoDots = s"${version.major}${version.minor}${version.patch}"

    import OS.Architecture._
    import OS.Name._
    val classifier =
      (os.name match { case Windows => "win"; case Linux => "linux"; case OSX => "osx" }) +
      (os.architecture match { case AMD64 => "64"; case X86 => "32"; case X86_64 => "" })

    val url = s"https://www.enterprisedb.com/postgresql-${pgVersionNoDots}-binaries-${classifier}"
    val body = IOUtils.toString(new URI(url), "UTF-8")

    """<a href="(https?://get.enterprisedb.com/postgresql/[^"]+?)">""".r
      .findFirstMatchIn(body).getOrElse(sys.error("Could not find download link at: " + url))
      .group(1)
      .replaceFirst("^http:", "https:") // sometimes get.enterprisedb.com provides a non-SSL download link
  }

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
