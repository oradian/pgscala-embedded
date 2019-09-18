package org.pgscala.embedded

import java.io.File
import java.util.concurrent.Executors

import com.typesafe.scalalogging.StrictLogging
import org.specs2.Specification

import scala.concurrent.ExecutionContext

object EmbeddedSpec {
  val projectRoot: File = new File(implicitly[sourcecode.File].value
    .replace('\\', '/')
    .replaceFirst("/src/test/scala/.*?$", "")).getAbsoluteFile

  private[this] val cachedThreadPool = Executors.newCachedThreadPool()
  val executionContext: ExecutionContext = ExecutionContext.fromExecutor(cachedThreadPool)

  def shutdown(): Unit = {
    cachedThreadPool.shutdownNow()
  }
}

trait EmbeddedSpec extends Specification with StrictLogging {
  implicit val executionContext: ExecutionContext = EmbeddedSpec.executionContext
}
