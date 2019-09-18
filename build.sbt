// ### BASIC SETTINGS ### //
organization := "org.pgscala.embedded"
name := "pgscala-embedded"

unmanagedSourceDirectories in Compile := Seq((scalaSource in Compile).value)
unmanagedSourceDirectories in Test := Seq((scalaSource in Test).value)

// ### DEPENDENCIES ### //
libraryDependencies ++= Seq(
  "org.apache.commons"         %  "commons-compress" % "1.19",
  "commons-io"                 %  "commons-io"       % "2.6",
  "commons-codec"              % "commons-codec"     % "1.13",
  "com.typesafe.scala-logging" %% "scala-logging"    % "3.9.2",

  "org.postgresql" %  "postgresql"      % "42.2.8" % Test,
  "org.specs2"     %% "specs2-core"     % "4.7.1"  % Test,
  "ch.qos.logback" %  "logback-classic" % "1.2.3"  % Test,
  "com.lihaoyi"    %% "sourcecode"      % "0.1.7"  % Test,
  "org.jsoup"      %  "jsoup"           % "1.12.1" % Test,
)

// ### COMPILE SETTINGS ### //
crossScalaVersions := Seq("2.13.1", "2.12.10", "2.11.12")
scalaVersion := crossScalaVersions.value.head
scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:_",
  "-unchecked",
  "-Xlint",
  "-Xverify",
  "-Yrangepos",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
)

scalacOptions in (Compile, doc) ++= Seq(
  "-no-link-warnings",
  "-sourcepath", (scalaSource in Compile).value.toString,
  "-doc-source-url", s"""https://github.com/oradian/pgscala-embedded/blob/v${version.value}/src/main/scala\u20AC{FILE_PATH}.scala""",
)

fork in Test := true
