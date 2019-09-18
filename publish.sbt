publishTo := Some(
  if (version.value endsWith "-SNAPSHOT")
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

credentials ++= {
  val creds = Path.userHome / ".config" / "pgscala-embedded" / "nexus.config"
  if (creds.exists) Some(Credentials(creds)) else None
}.toSeq

licenses += (("MIT License", url("https://opensource.org/licenses/MIT")))
startYear := Some(2017)

scmInfo := Some(ScmInfo(
  url("https://github.com/oradian/pgscala-embedded"),
  "scm:git:https://github.com/oradian/pgscala-embedded.git",
  Some("scm:git:git@github.com:oradian/pgscala-embedded.git"),
))

pomExtra :=
<developers>
  <developer>
    <id>melezov</id>
    <name>Marko Elezovi&#263;</name>
    <url>https://github.com/melezov</url>
  </developer>
</developers>

publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ => false }

homepage := Some(url("https://github.com/oradian/pgscala-embedded"))
