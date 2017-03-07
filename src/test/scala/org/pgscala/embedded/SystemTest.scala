package org.pgscala.embedded

import java.io.File
import java.sql.DriverManager

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.io.FileUtils
import org.specs2.mutable.Specification

import scala.collection.mutable.LinkedHashMap
import scala.concurrent.Await
import scala.concurrent.duration._

class SystemTest extends Specification with StrictLogging {
  override def is = s2"""
    test cluster versions $testClusterVersions
"""

  private[this] val TestClustersFolder = new File(EmbeddedSpec.projectRoot, s"target/test-clusters")

  /** Installs a couple use-case PostgreSQL clusters in parallel and queries the cluster
    * version by connecting to the `postgres` database using the superuser account */
  def testClusterVersions() = {
    logger.info("Deleting previous clusters ...")
    FileUtils.deleteDirectory(TestClustersFolder)

    val portsReserved = new LinkedHashMap[PostgresVersion, Int]
    val clusterVersions = PostgresVersion.values.take(4) // legacy clusters require legacy libraries on the OS

    val portsToTry = 5432 to 5678
    for (version <- clusterVersions) {
      val fromPort = if (portsReserved.isEmpty) portsToTry.head else portsReserved.values.max + 1
      val port = Util.findFreePort("127.0.0.1", fromPort to portsToTry.last)
      logger.info(s"Reserved port ${port} for cluster version ${version} ...")
      portsReserved(version) = port
    }

    val timeToken = System.currentTimeMillis
    (for ((version, port) <- portsReserved.toSeq.par) yield {
      val testClusterFolder = new File(TestClustersFolder, s"$timeToken-$version")
      FileUtils.deleteDirectory(testClusterFolder)

      val pc = new PostgresCluster(version, testClusterFolder, Map("port" -> port.toString))
      pc.initialize("HyperUser", "HyperPass")

      val (process, clusterReady) = pc.start()
      Await.result(clusterReady, 60 seconds)

      try {
        Class.forName("org.postgresql.Driver")
        val connection = DriverManager.getConnection(s"jdbc:postgresql://127.0.0.1:$port/postgres?user=HyperUser&password=HyperPass")
        try {
          val stmt = connection.prepareStatement("SELECT version()")
          try {
            val rs = stmt.executeQuery()
            rs.next() ==== true
            val response = rs.getString(1)
            logger.info("Cluster version {} responsed with: {}", version, response)
            response must startWith(s"PostgreSQL ${version}")
          } finally {
            stmt.close()
          }
        } finally {
          connection.close()
        }
      } finally {
        pc.stop()
      }

      process.exitValue() ==== 0
    }).seq
  }
}
