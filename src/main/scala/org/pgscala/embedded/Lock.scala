package org.pgscala.embedded

import java.io.{File, RandomAccessFile}
import java.nio.ByteBuffer

import com.typesafe.scalalogging.StrictLogging

object Lock extends StrictLogging {
  val DefaultBuffer = 32
  def apply(file: File, bufferSize: Int = DefaultBuffer): Builder = new Builder(file, bufferSize)

  class Builder(file: File, bufferSize: Int) {
    def whenFirst[U](whenFirst: => (String, U)): WithFirst[U] = new WithFirst[U](() => whenFirst)
    def whenOther[U](whenOther: String => U): WithOther[U] = new WithOther[U](whenOther)

    class WithFirst[U] private[Builder](whenFirst: () => (String, U)) {
      def whenOther(whenOther: String => U): WithFirstAndOther[U] = new WithFirstAndOther[U](whenFirst, whenOther)
    }

    class WithOther[U] private[Builder](whenOther: String => U) {
      def whenFirst(whenFirst: () => (String, U)): WithFirstAndOther[U] = new WithFirstAndOther[U](whenFirst, whenOther)
    }

    class WithFirstAndOther[U] private[Builder](whenFirst: () => (String, U), whenOther: String => U) {
      def lock(): U = {
        if (!file.isFile) {
          logger.trace(s"Lock file did not exist - creating a new lock: ${file}")
          file.createNewFile()
        }
        val channel = new RandomAccessFile(file, "rw").getChannel
        try {
          val fileLock = channel.lock()
          try {
            val array = new Array[Byte](bufferSize)
            val buffer = ByteBuffer.wrap(array)
            val read = channel.read(buffer)
            if (read <= 0) {
              val (firstPayload, firstResult) = whenFirst()
              buffer.put(firstPayload.getBytes("ISO-8859-1"))
              buffer.flip()
              channel.write(buffer)
              firstResult
            } else {
              val lockBody = new String(array, 0, read, "ISO-8859-1")
              whenOther(lockBody)
            }
          } finally {
            fileLock.release()
          }
        } finally {
          channel.close()
        }
      }
    }
  }
}
