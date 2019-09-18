package org.pgscala.embedded

import java.net.{ConnectException, Socket}
import java.security.MessageDigest
import java.nio.charset.StandardCharsets._

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.codec.binary.Hex

object Util extends StrictLogging {
  def digest(text: String): String = {
    val md = MessageDigest.getInstance("SHA-256")
    Hex.encodeHexString(md.digest(text.getBytes(UTF_8)))
  }

  lazy val isWindows: Boolean = OS.Name.resolved match {
    case Some(OS.Name.Windows) => true
    case _ => false
  }

  lazy val isUnix: Boolean = OS.Name.resolved match {
    case Some(OS.Name.Linux) | Some(OS.Name.OSX) => true
    case _ => false
  }

  private[this] def socketIsFree(host: String, port: Int): Boolean =
    try {
      logger.trace(s"Checking if port $port is free...")
      new Socket(host, port).close()
      logger.debug(s"Port $port is free, choosing it for the cluster")
      false
    } catch {
      case _: ConnectException =>
        true
    }

  def findFreePort(host: String, portRange: Range): Int =
    portRange.find(port => socketIsFree(host, port))
      .getOrElse(sys.error(s"Could not find free port in range: [${portRange.head},${portRange.last}]"))
}
