package org.pgscala.embedded

import java.io.{BufferedReader, File, InputStream, InputStreamReader}
import java.nio.charset.Charset
import java.util.Locale
import java.util.regex.{Matcher, Pattern}

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.io.{FileUtils, IOUtils}
import org.pgscala.embedded.Util._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

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

  private def processPostgresqlConf(body: String, settings: Map[String, String]): String =
    settings.foldLeft(body) { case (current, (key, value)) =>
      current.replaceFirst(
        s"""#?${Pattern.quote(key)}\\s*=\\s*\\S+""",
        Matcher.quoteReplacement {
          val line = key + " = " + value
          logger.debug("Configuring cluster with: " + line)
          line
        }
      ).ensuring(_ != current, "Could not configure cluster with: " + key + " = " + value)
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
      println("Cache exists: " + cachedArchive.getAbsolutePath)
    }
  }

  def unpack(targetFolder: File): Unit = {
    resolveCached()
    ArchiveUnpacker.unpack(cachedArchive, targetFolder)
  }

  def initialize(superuser: String, superuserPassword: String, port: Int): this.type = {
    FileUtils.deleteDirectory(targetFolder)
    unpack(targetFolder)

    val passwordFile = new File(targetFolder, "password.txt")
    FileUtils.writeStringToFile(passwordFile, superuserPassword, "UTF-8")

    val execFolder = new File(targetFolder, "pgsql")
    val dataFolder = new File(targetFolder, "data")

    val pb = new ProcessBuilder("initdb.exe", s"-U$superuser", "-Apassword", s"--pwfile=${passwordFile.getPath}", "-Eutf8", s"-D${dataFolder.getPath}")
    pb.directory(execFolder)
    val process = pb.start()

    import scala.concurrent.{Await, ExecutionContext}
    import scala.concurrent.duration._
    import java.util.concurrent.Executors
    val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

    val errorFut = Future {
      IOUtils.toString(process.getErrorStream, Charset.defaultCharset)
    }(ec)
    val outputFut = Future {
      IOUtils.toString(process.getInputStream, Charset.defaultCharset)
    }(ec)

    process.getOutputStream.close()
    val error = Await.result(errorFut, 30 seconds)
    val output = Await.result(outputFut, 30 seconds)
    val exit = process.exitValue()

    require(exit == 0, "Initialization was not successful:\n" + output + "\n" + error)

    println("-" * 50)
    println(output)
    println("e" * 50)
    println(error)
    println("=" * 50)

    val postgresqlConf = new File(dataFolder, "postgresql.conf")
    val oldConfig = FileUtils.readFileToString(postgresqlConf, "UTF-8")
    val newConfig = processPostgresqlConf(oldConfig, settings)
    FileUtils.writeStringToFile(postgresqlConf, newConfig, "UTF-8")

    FileUtils.writeStringToFile(new File(targetFolder, "pg_start.bat"),
      "\"%~dp0pgsql\\bin\\pg_ctl.exe\" \"-D%~dp0data\" start\r\n", "windows-1252")
    FileUtils.writeStringToFile(new File(targetFolder, "pg_stop.bat"),
      "\"%~dp0pgsql\\bin\\pg_ctl.exe\" \"-D%~dp0data\" stop\r\n", "windows-1252")

    this
  }

  def start(): Unit = {
    val pb = new ProcessBuilder("cmd", "/c", "pg_start.bat")
    pb.directory(targetFolder)
    val process = pb.start()

    def mybr(is: InputStream) = new BufferedReader(new InputStreamReader(is) {
      override def read(cbuf: Array[Char], offset: Int, length: Int): Int = {
//        println(s"Reading ($offset, $length)")
        super.read(cbuf, offset, length)
      }
    })

    import scala.concurrent.{Await, ExecutionContext}
    import java.util.concurrent.Executors
    val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

    val errorFut = Future {
      val br = mybr(process.getErrorStream)
      var dead = false
      println("STARTED ERR!")
      while (true) {
        val line = br.readLine()
        if (line == null) {
          dead = true
        } else {
          logger.debug("PG [err]: {}", line)
        }
      }
      println("EXITED ERR!")
    }(ec)
    val outputFut = Future {
      th = Thread.currentThread()
      ooo = process.getInputStream
      Try {
        val br = mybr(ooo)
        println("STARTED OUT!")
        var dead = false
        while (true) {
          val line = br.readLine()
          if (line == null) {
            dead = true
          } else {
            logger.debug("PG [out]: {}", line)
          }
        }
      } match {
        case Success(x) => println("SUC" + x)
        case Failure(f) => println("FAL" + f)
      }
      println("EXITED OUT!")
    }(ec)

    process.getOutputStream.close()
  }

  var th: Thread = null
  var ooo: InputStream = null

  def stop(): Unit = {
    val pb = new ProcessBuilder("cmd", "/c", "pg_stop.bat")
    pb.directory(targetFolder)
    val process = pb.start()

    import scala.concurrent.{Await, ExecutionContext}
    import scala.concurrent.duration._
    import java.util.concurrent.Executors
    val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

    val errorFut = Future {
      IOUtils.toString(process.getErrorStream, Charset.defaultCharset)
    }(ec)
    val outputFut = Future {
      IOUtils.toString(process.getInputStream, Charset.defaultCharset)
    }(ec)

    process.getOutputStream.close()
    val error = Await.result(errorFut, 30 seconds)
    val output = Await.result(outputFut, 30 seconds)
    val exit = process.exitValue()

    require(exit == 0, "Stop was not successful:\n" + output + "\n" + error)
  }
}
