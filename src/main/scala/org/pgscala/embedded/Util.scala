package org.pgscala.embedded

import java.security.MessageDigest
import java.util.Locale
import javax.xml.bind.DatatypeConverter

object Util {
  def bin2Hex(binary: Array[Byte]): String =
    DatatypeConverter.printHexBinary(binary).toLowerCase(Locale.ROOT)

  def digest(text: String): Array[Byte] = {
    val md = MessageDigest.getInstance("SHA-256")
    md.digest(text.getBytes("UTF-8"))
  }
}
