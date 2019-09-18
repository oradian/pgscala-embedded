package org.pgscala.embedded

import java.io._
import java.net.{HttpURLConnection, URI}
import java.nio.ByteBuffer
import java.nio.channels.{Channels, FileChannel}
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

object Downloader {
  final val MaxThreads = 4
  final val MaxRetries = 10

  final val BufferSize = 64 * 1024

  case class ProgressUpdate(streamId: Int, retry: Int, size: Long, from: Long, to: Long, index: Long, thisUpdate: Int, soFar: Long)
  type ProgressListener = Option[ProgressUpdate => Unit]
  type PostDownloadHook = Option[(FileChannel, Long) => Unit]

  def resolveSize(url: URI): Long =
    url.toURL.openConnection().getContentLengthLong
}

case class Downloader(url: URI, file: File, size: Long, maxRetries: Int = Downloader.MaxRetries, threads: Int = Downloader.MaxThreads) {
  import Downloader._

  private[this] class Worker(streamId: Int, oc: FileChannel, from: Long, to: Long, soFar: AtomicLong, progressListener: ProgressListener) {
    /** returns last successfully read byte index */
    private[this] def pipeStream(retry: Int, is: InputStream): Long = {
      val ic = Channels.newChannel(new BufferedInputStream(is, BufferSize))
      val bb = ByteBuffer.allocateDirect(BufferSize)
      var index = from
      try {
        while (index < to) {
          val read = ic.read(bb)
          bb.flip()
          oc.write(bb, index)
          bb.compact()

          val readWithoutOverflow = if (index + read <= to) read.toLong else to - index
          index += readWithoutOverflow

          if (progressListener.isDefined) {
            val current = soFar.addAndGet(readWithoutOverflow)
            progressListener.get(ProgressUpdate(streamId, retry, size, from, to, index, read, current))
          }
        }
        index
      } catch {
        case NonFatal(_) =>
          index
      } finally {
        ic.close()
      }
    }

    /** retries to download a range by retrying (reopening the connection) for a couple times */
    def downloadRange(retry: Int): Unit = {
      val conn = url.toURL.openConnection().asInstanceOf[HttpURLConnection]
      try {
        conn.setRequestProperty("Range", s"bytes=${from}-${to - 1}")
        try {
          val newFrom = pipeStream(retry, conn.getInputStream)
          if (newFrom < to) {
            if (retry < maxRetries) {
              downloadRange(retry + 1)
            } else {
              throw new IOException(s"Could not download $url, range [$from-$to), even after $maxRetries retries")
            }
          }
        } catch {
          case NonFatal(t) =>
            if (retry < maxRetries) {
              downloadRange(retry + 1)
            } else {
              throw t
            }
        }
      } finally {
        conn.disconnect()
      }
    }
  }

  private[this] def parallelDownload(oc: FileChannel, soFar: AtomicLong, progressListener: ProgressListener): Unit = {
    val pool = Executors.newFixedThreadPool(threads)
    val executionContext = ExecutionContext.fromExecutor(pool)

    val workers = (0 until threads) map { workerIndex =>
      Future {
        val from = size * workerIndex / threads
        val to = size * (workerIndex + 1) / threads
        new Worker(workerIndex, oc, from, to, soFar, progressListener).downloadRange(0)
      }(executionContext)
    }

    pool.shutdown()

    for (worker <- workers) {
      Await.result(worker, Duration.Inf)
    }
  }

  def download(progressListener: ProgressListener, postDownloadHook: PostDownloadHook): Unit = {
    if (!file.isFile) {
      file.createNewFile()
    }
    val raf = new RandomAccessFile(file, "rw")
    try {
      val oc = raf.getChannel
      try {
        val lock = oc.lock()
        try {
          raf.setLength(0L)
          val soFar = new AtomicLong(0L)
          parallelDownload(oc, soFar, progressListener)
          for (hook <- postDownloadHook) {
            hook(oc, soFar.get)
          }
        } finally {
          lock.release()
        }
      } finally {
        oc.close()
      }
    } finally {
      raf.close()
    }
  }
}
