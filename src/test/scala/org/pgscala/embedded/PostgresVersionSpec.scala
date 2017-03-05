package org.pgscala.embedded

import java.net.HttpURLConnection
import org.apache.commons.io.IOUtils
import scala.collection.JavaConverters._
import scala.util.{Success, Try}

class PostgresVersionSpec extends EmbeddedSpec {
  def is = s2"""
    check latest versions  $checkLatestVersions
    re-resolve downloads   $reResolveDownloads
"""

  def checkLatestVersions =
    PostgresVersion.values === PostgresVersionUpdater.minorVersions

  private[this] def constructExpectedUrl(version: PostgresVersion, os: OS): String =
    PostgresDownload(version, os).downloadUrl

  private[this] def constructInitialUrl(version: PostgresVersion, os: OS): String = {
    val pgVersionNoDots = s"${version.major}${version.minor}${version.patch}"

    import OS.Architecture._
    import OS.Name._
    val classifier =
      (os.name match { case Windows => "win"; case Linux => "linux"; case Mac => "osx" }) +
      (os.architecture match { case AMD64 => "64"; case X86 => "32"; case PPC => "" })

    s"https://www.enterprisedb.com/postgresql-${pgVersionNoDots}-binaries-${classifier}"
  }

  private[this] def resolveActualFromInitialUrl(url: String): String = {
    val conn = new java.net.URL(url).openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("HEAD")
    IOUtils.readFully(conn.getInputStream, 0)
    conn.disconnect()

    val props = conn.getRequestProperties
    conn.getResponseCode match {
      case 200 =>
        val host = props.get("Host").get(0)
        val head = props.asScala.find(p => p._2.size() == 1 && p._2.get(0) == null).get._1
        assert(head.matches("HEAD (.*) HTTP/\\S{3}"))
        val path = head.drop("HEAD ".length).dropRight(" HTTP/x.y".length)
        s"https://$host$path"

      case 302 =>
        val redirect = conn.getHeaderField("Location")
        resolveActualFromInitialUrl(redirect)

      case other =>
        sys.error("Unknown response code, expected 200 or 302, got: " + other)
    }
  }

  def reResolveDownloads = (for {
    version <- PostgresVersion.values.par
    os <- OS.values
  } yield {
    val expectedUrl = constructExpectedUrl(version, os)
    val initialUrl = constructInitialUrl(version, os)
    val actualUrl = Try { resolveActualFromInitialUrl(initialUrl) }
    logger.trace("Resolved actual URL for version {} @ {}: {}", version, os, actualUrl)
    actualUrl ==== Success(expectedUrl)
  }).seq
}
