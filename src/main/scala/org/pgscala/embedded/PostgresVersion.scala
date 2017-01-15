package org.pgscala.embedded

import scala.collection.immutable.ListMap

case class PostgresVersion(major: Int, minor: Int, patch: Int) extends Ordered[PostgresVersion] {
  require(major > 0, "Major version must be positive")
  require(minor >= 0, "Minor version cannot be negative")
  require(patch >= 0, "Patch version cannot be negative")

  override def toString = major + "." + minor + "." + patch

  override def compare(that: PostgresVersion): Int =
    Ordering[(Int, Int, Int)].compare((major, minor, patch), (that.major, that.minor, that.patch))
}

object PostgresVersion {
  // latest available use-case versions
  val `9.6.1` = PostgresVersion(9, 6, 1)
  val `9.5.5` = PostgresVersion(9, 5, 5)
  val `9.4.10` = PostgresVersion(9, 4, 10)
  val `9.3.15` = PostgresVersion(9, 3, 15)
  val `9.2.19` = PostgresVersion(9, 2, 19)
  val `9.1.24` = PostgresVersion(9, 1, 24)
  val `9.0.23` = PostgresVersion(9, 0, 23)

  // use-cases - preferably use these over hardcoding the patch version
  val `9.6` = `9.6.1`
  val `9.5` = `9.5.5`
  val `9.4` = `9.4.10`
  val `9.3` = `9.3.15`
  val `9.2` = `9.2.19`
  val `9.1` = `9.1.24`
  val `9.0` = `9.0.23`

  /** for runtime lookup of latest patch version */
  val minorVersions: Map[String, PostgresVersion] = ListMap(
    "9.6" -> `9.6`,
    "9.5" -> `9.5`,
    "9.4" -> `9.4`,
    "9.3" -> `9.3`,
    "9.2" -> `9.2`,
    "9.1" -> `9.1`,
    "9.0" -> `9.0`
  )

  val values: IndexedSeq[PostgresVersion] = minorVersions.values.toIndexedSeq
}
