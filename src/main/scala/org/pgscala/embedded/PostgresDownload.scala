package org.pgscala.embedded

private object PostgresDownload {
  private final val DownloadUrl = "https://get.enterprisedb.com/postgresql/"
}

case class PostgresDownload(version: PostgresVersion, os: OS) {
  import PostgresDownload._

  val archiveName = s"postgresql-${version}-1-${os.name.classifier}${os.architecture.classifier}-binaries.${os.name.archiveMode}"
  val downloadUrl = DownloadUrl + archiveName
}
