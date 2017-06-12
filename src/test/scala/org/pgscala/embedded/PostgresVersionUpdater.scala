package org.pgscala.embedded

import java.io.File
import java.net.URL
import java.util.regex.Matcher

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.io.{FileUtils, IOUtils}

object PostgresVersionUpdater extends StrictLogging {
  val MinMajor = 9

  lazy val minorVersions: Seq[PostgresVersion] = {
    val catalog = IOUtils.toString(new URL("https://www.postgresql.org/ftp/source/"), "UTF-8")

    val allVersions = """<a href="v((\d)\.(\d+)(\.(\d+))?)/">v\1</a>""".r.findAllMatchIn(catalog).map { v =>
      PostgresVersion(v.group(2).toInt, v.group(3).toInt, Option(v.group(4)).fold(0) { _.tail.toInt })
    }.toSeq
    logger.trace(s"Found ${allVersions.size} version of PostgreSQL server on the official website")

    // filter out legacy Postgres versions (i.e. take only >= 9.0.0)
    val versionsOfInterest = allVersions filter { _.major >= MinMajor }
    logger.debug(s"Filtered down to ${versionsOfInterest.size} versions of interest: ${versionsOfInterest.mkString(", ")}")

    versionsOfInterest                  // SELECT * FROM versions
      .groupBy(e => (e.major, e.minor)) // GROUP BY major, minor
      .map(_._2.maxBy(_.patch))         // WHERE patch = MAX
      .toSeq.sorted.reverse             // ORDER BY 1 DESC
  }

  lazy val generatedSource: String = s"""object PostgresVersion {
  // latest available use-case versions
${minorVersions.map { case PostgresVersion(major, minor, patch) =>
  s"  val `$major.$minor.$patch` = PostgresVersion($major, $minor, $patch)"
}.mkString("\n")}

  // use-cases - preferably use these over hardcoding the patch version
${minorVersions.map { case PostgresVersion(major, minor, patch) =>
  s"  val `$major.$minor` = `$major.$minor.$patch`"
}.mkString("\n")}

  /** for runtime lookup of latest patch version */
  val minorVersions: Map[String, PostgresVersion] = ListMap(
${minorVersions.map { case PostgresVersion(major, minor, _) =>
  s"""    "$major.$minor" -> `$major.$minor`"""
}.mkString("", ",\n", "")}
  )

  val values: IndexedSeq[PostgresVersion] = minorVersions.values.toIndexedSeq
}
"""

  def main(args: Array[String]): Unit = {
    val file = new File(EmbeddedSpec.projectRoot, "src/main/scala/org/pgscala/embedded/PostgresVersion.scala")
    val oldBody = FileUtils.readFileToString(file, "UTF-8")

    val newBody = oldBody.replaceFirst("(?s)object PostgresVersion.*", Matcher.quoteReplacement(generatedSource))

    if (newBody != oldBody) {
      logger.info("Updated PostgresVersion object in {}", file)
      FileUtils.writeStringToFile(file, newBody, "UTF-8")
    } else {
      logger.debug("No need to update PostgresVersion object, as it contains the latest versions")
    }

    EmbeddedSpec.shutdown()
  }
}
