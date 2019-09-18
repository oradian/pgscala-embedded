package org.pgscala.embedded

import java.io.File
import java.sql.DriverManager

import org.apache.commons.io.FileUtils

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SystemTest extends EmbeddedSpec {
  override def is = s2"""
    test cluster versions $testClusterVersions
"""

  private[this] val TestClustersFolder = new File(EmbeddedSpec.projectRoot, s"target/test-clusters")

  /** Installs a couple use-case PostgreSQL clusters in parallel and queries the cluster
    * version by connecting to the `postgres` database using the superuser account */
  private[this] def testClusterVersions = {
    logger.info("Deleting previous clusters ...")
    FileUtils.deleteDirectory(TestClustersFolder)

    val portsReserved = new mutable.LinkedHashMap[PostgresVersion, Int]
    val clusterVersions = PostgresVersion.values.take(5) // legacy clusters require legacy libraries on the OS, only test a couple top versions

    val portsToTry = 5678 to 5999
    for (version <- clusterVersions) {
      val fromPort = if (portsReserved.isEmpty) portsToTry.head else portsReserved.values.max + 1
      val port = Util.findFreePort("127.0.0.1", fromPort to portsToTry.last)
      logger.info(s"Reserved port $port for cluster version $version ...")
      portsReserved(version) = port
    }

    val timeToken = System.currentTimeMillis
    val clusterTests = (for ((version, port) <- portsReserved.toSeq) yield Future {
      val testClusterFolder = new File(TestClustersFolder, s"$timeToken-$version")
      FileUtils.deleteDirectory(testClusterFolder)

      val address = "127.0.0.1"
      val role = s"NonStandardSuperuser:$version"
      val pass = s"NonStandardPassword:$version"

      val pc = new PostgresCluster(version, testClusterFolder, Map(
        "listen_addresses" -> s"'$address'",
        "port" -> s"$port",
      ))
      pc.initialize(role, pass)

      val (process, clusterReady) = pc.start()
      try {
        Await.result(clusterReady, 5.minutes)
        Class.forName("org.postgresql.Driver")
        val connection = DriverManager.getConnection(s"jdbc:postgresql://$address:$port/postgres?user=$role&password=$pass")
        try {
          val stmt = connection.prepareStatement("SELECT version()")
          try {
            val rs = stmt.executeQuery()
            rs.next() ==== true
            val response = rs.getString(1)
            logger.info("Cluster version {} responded with: {}", version, response)
            response must startWith(s"PostgreSQL $version")
          } finally {
            stmt.close()
          }
        } finally {
          connection.close()
        }
      } finally {
        pc.stop()
      }

      process.exitValue() == 0
    })

    val tests = Future.sequence(clusterTests)
    Await.result(tests, 30.minutes).count(res => res) ==== clusterTests.size
  }
}
