package org.pgscala.embedded

import java.io.{BufferedReader, File, FileInputStream, InputStreamReader}
import java.nio.charset.Charset
import java.util.Locale
import java.util.regex.{Matcher, Pattern}

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.io.{FileUtils, IOUtils}
import org.pgscala.embedded.Util._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

object PostgresCluster extends StrictLogging {
  private val ArchiveFolderBlacklist = Seq(
    "pgsql/doc/",
    "pgsql/include/",
    "pgsql/pgadmin 4/",
    "pgsql/pgadmin 4.app/",
    "pgsql/pgadmin iii/",
    "pgsql/pgadmin3/",
    "pgsql/pgadmin3.app/",
    "pgsql/share/locale/",
    "pgsql/stackbuilder/",
    "pgsql/stackbuilder.app/",
    "pgsql/symbols/"
  )

  private def digestSettings(settings: Map[String, String]): Array[Byte] = {
    val sb = new StringBuilder
    val orderedSettings = settings.toIndexedSeq.sorted
    orderedSettings foreach { case (key, value) =>
      sb ++= key ++= " = " ++= value += '\n'
    }
    Util.digest(sb.toString)
  }

  // windows will produce a brand new postgresql.conf on every initdb (read from the binary template)
  // linux/max will copy the existing postgresql.conf.sample into the new cluster (that's why the #? in the regex pattern)
  private def processPostgresqlConf(body: String, settings: Map[String, String]): String =
    settings.foldLeft(body) { case (current, (key, value)) =>
      val pattern = s"""#?${Pattern.quote(key)}\\s*=.*""".r.pattern
      val matcher = pattern.matcher(current)
      if (!matcher.find()) {
        sys.error("Could not configure cluster with: " + key + " = " + value)
      }
      val replacement = Matcher.quoteReplacement {
        val line = key + " = " + value
        logger.debug("Configuring cluster with: " + line)
        line
      }
      val sb = new StringBuffer
      matcher.appendReplacement(sb, replacement)
      matcher.appendTail(sb)
      sb.toString
    }

  private def processArchiveEntry(name: String, bytes: Array[Byte], settings: Map[String, String]): Option[Array[Byte]] = {
    if (ArchiveFolderBlacklist.exists(name.toLowerCase(Locale.ROOT).startsWith)) {
      None
    } else if (!name.endsWith("postgresql.conf.sample")) {
      Some(bytes)
    } else {
      val body = new String(bytes, "UTF-8")
      val newBody = processPostgresqlConf(body, settings)
      Some(newBody.getBytes("UTF-8"))
    }
  }

  private val executionContext = {
    import java.util.concurrent.Executors

    import scala.concurrent.ExecutionContext
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool)
  }

  private abstract sealed class PgExec(unixName: String, windowsName: String) {
    lazy val name: String = if (Util.isUnix) unixName else windowsName
  }
  private object PgExec {
    case object InitDb extends PgExec("./initdb",    "initdb.exe")
    case object PgCtl  extends PgExec("./pg_ctl.sh", "pg_ctl.bat")
  }
}

class PostgresCluster(postgresVersion: PostgresVersion, targetFolder: File, settings: Map[String, String]) extends StrictLogging {
  import PostgresCluster._

  val download = new PostgresDownload(postgresVersion, OS.resolved.get)
  val targetArchive = new File(Home.original, download.archiveName)

  private val postgresqlConfDigest: Array[Byte] =
    digestSettings(settings)

  def resolveOriginal(): Unit = {
    if (!targetArchive.isFile) {
      if (!Home.original.isDirectory) {
        Home.original.mkdirs()
      }
      download.download(targetArchive)
    }
  }

  val digest = bin2Hex(postgresqlConfDigest)
  private val cachedParent = new File(Home.cached, download.archiveName)
  private val cachedArchive = new File(cachedParent, digest)

  def resolveCached(): Unit = {
    if (!cachedArchive.isFile) {
      resolveOriginal()
      if (!cachedParent.isDirectory) {
        cachedParent.mkdirs()
      }
      ArchiveProcessor.filterArchive(targetArchive, cachedArchive, (name, bytes) =>
        processArchiveEntry(name, bytes, settings)
      )
    } else {
      logger.info("Cache exists: {}", cachedArchive.getAbsolutePath)
    }
  }

  def unpack(targetFolder: File): Unit = {
    resolveCached()
    ArchiveUnpacker.unpack(cachedArchive, targetFolder)
  }

  private[this] def runInTarget(relativePath: String, executable: PgExec, arguments: String*): ProcessBuilder = {
    val args = if (Util.isUnix) Nil else Seq("cmd", "/c")
    val pb = new ProcessBuilder((args :+ executable.name) ++ arguments: _*)
    val execFolder = new File(targetFolder, relativePath)
    pb.directory(execFolder)
    pb
  }

  private[this] def waitForProcess(process: Process, timeout: Duration): Int = {
    val errorFut = Future {
      IOUtils.toString(process.getErrorStream, Charset.defaultCharset)
    }(executionContext)
    val outputFut = Future {
      IOUtils.toString(process.getInputStream, Charset.defaultCharset)
    }(executionContext)

    process.getOutputStream.close()
    process.waitFor(timeout.length, timeout.unit)

    import scala.concurrent.Await
    val error = Await.result(errorFut, 30 seconds)
    val output = Await.result(outputFut, 30 seconds)
    if (error.nonEmpty) logger.error(error)
    if (output.nonEmpty) logger.debug(output)

    process.exitValue()
  }

  def initialize(superuser: String, superuserPassword: String): this.type = {
    FileUtils.deleteDirectory(targetFolder)
    unpack(targetFolder)

    val passwordFile = new File(targetFolder, "password.txt")
    FileUtils.writeStringToFile(passwordFile, superuserPassword, "UTF-8")

    val dataFolder = new File(targetFolder, "data")
    val process = runInTarget(
      "pgsql/bin", PgExec.InitDb,
      s"-U$superuser",
      "-Apassword", s"--pwfile=${passwordFile.getPath}",
      "-Eutf8",
      s"-D${dataFolder.getPath}"
    ).start()

    val exit = waitForProcess(process, 30 seconds)
    require(exit == 0, "Initialization was not successful!")

    val postgresqlConf = new File(dataFolder, "postgresql.conf")
    val oldConfig = FileUtils.readFileToString(postgresqlConf, "UTF-8")
    val newConfig = processPostgresqlConf(oldConfig, settings)
    FileUtils.writeStringToFile(postgresqlConf, newConfig, "UTF-8")

    val pgCtlFile = new File(targetFolder, PgExec.PgCtl.name)
    val body = IOUtils.toByteArray(getClass.getResource(pgCtlFile.getName))
    FileUtils.writeByteArrayToFile(pgCtlFile, body)
    pgCtlFile.setExecutable(true)

    this
  }

  private[this] def logReader(file: File, onLine: String => Unit): Unit = Future {
    while (!file.isFile) {
      Thread.sleep(100)
    }
    val br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))
    var dead = false
    while (true) {
      val line = br.readLine()
      if (line == null) {
        dead = true
      } else {
        onLine(line)
      }
    }
  }(executionContext)

  def start(): (Process, Future[Unit]) = {
    val stdout = new File(targetFolder, "stdout.log")
    val stderr = new File(targetFolder, "stderr.log")
    stdout.delete()
    stderr.delete()

    val process = runInTarget("", PgExec.PgCtl, "start")
      .redirectOutput(stdout)
      .redirectError(stderr)
      .start()
    val clusterReady = Promise[Unit]()

    logReader(stdout, line => {
      if (line.contains("database system is ready to accept connections")) {
        clusterReady.success(())
      }
      logger.debug(line)
    })

    logReader(stderr, line => {
      logger.error(line)
    })

    process.getOutputStream.close()
    (process, clusterReady.future)
  }

  def stop(): Unit = {
    val process = runInTarget("", PgExec.PgCtl, "stop").start()
    val exit = waitForProcess(process, 30 seconds)
    require(exit == 0, "Stop was not successful!")
  }
}
