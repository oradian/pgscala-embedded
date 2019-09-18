package org.pgscala.embedded

import scala.collection.immutable.ListMap

case class PostgresVersion private (major: Int, minor: Option[Int], patch: Int) extends Ordered[PostgresVersion] {
  require(major > 0, "Major version must be positive")
  require(minor.isEmpty || minor.get >= 0, "Minor version cannot be negative")
  require(patch >= 0, "Patch version cannot be negative")

  def nonPatch: String = s"$major${minor.fold(""){"." + _}}"
  override def toString: String = s"$nonPatch.$patch"

  override def compare(that: PostgresVersion): Int =
    Ordering[(Int, Option[Int], Int)].compare((major, minor, patch), (that.major, that.minor, that.patch))
}

object PostgresVersion {
  // latest available use-case versions
  val `11.5`: PostgresVersion = PostgresVersion(11, None, 5)
  val `10.10`: PostgresVersion = PostgresVersion(10, None, 10)
  val `9.6.15`: PostgresVersion = PostgresVersion(9, Some(6), 15)
  val `9.5.19`: PostgresVersion = PostgresVersion(9, Some(5), 19)
  val `9.4.24`: PostgresVersion = PostgresVersion(9, Some(4), 24)
  val `9.3.25`: PostgresVersion = PostgresVersion(9, Some(3), 25)
  val `9.2.24`: PostgresVersion = PostgresVersion(9, Some(2), 24)
  val `9.1.24`: PostgresVersion = PostgresVersion(9, Some(1), 24)
  val `9.0.23`: PostgresVersion = PostgresVersion(9, Some(0), 23)

  // use-cases - preferably use these over hardcoding the patch version
  val `11`: PostgresVersion = `11.5`
  val `10`: PostgresVersion = `10.10`
  val `9.6`: PostgresVersion = `9.6.15`
  val `9.5`: PostgresVersion = `9.5.19`
  val `9.4`: PostgresVersion = `9.4.24`
  val `9.3`: PostgresVersion = `9.3.25`
  val `9.2`: PostgresVersion = `9.2.24`
  val `9.1`: PostgresVersion = `9.1.24`
  val `9.0`: PostgresVersion = `9.0.23`

  /** for runtime lookup of latest patch version */
  val minorVersions: Map[String, PostgresVersion] = ListMap(
    "11" -> `11`,
    "10" -> `10`,
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
