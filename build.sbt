// ### BASIC SETTINGS ### //
organization := "org.pgscala.embedded"
name := "pgscala-embedded"

unmanagedSourceDirectories in Compile := Seq((scalaSource in Compile).value)
unmanagedSourceDirectories in Test := Seq((scalaSource in Test).value)

// ### DEPENDENCIES ### //
libraryDependencies ++= Seq(
  "org.apache.commons"         %  "commons-compress" % "1.14"
, "commons-io"                 %  "commons-io"       % "2.5"
, "com.typesafe.scala-logging" %% "scala-logging"    % "3.5.0"

, "org.postgresql" %  "postgresql"      % "42.1.1" % Test
, "org.specs2"     %% "specs2-core"     % "3.9.1"  % Test
, "ch.qos.logback" %  "logback-classic" % "1.2.3"  % Test
)

// ### COMPILE SETTINGS ### //
crossScalaVersions := Seq("2.12.2", "2.11.11")
scalaVersion := crossScalaVersions.value.head
scalacOptions ++= Seq(
  "-deprecation"
, "-encoding", "UTF-8"
, "-feature"
, "-language:_"
, "-unchecked"
, "-Xfuture"
, "-Xlint"
, "-Xverify"
, "-Yno-adapted-args"
, "-Yrangepos"
, "-Yrepl-sync"
, "-Ywarn-dead-code"
, "-Ywarn-numeric-widen"
)

scalacOptions in (Compile, doc) ++= Seq(
  "-no-link-warnings"
, "-sourcepath", (scalaSource in Compile).value.toString
, "-doc-source-url", s"""https://github.com/oradian/pgscala-embedded/blob/v${version.value}/src/main/scala\u20AC{FILE_PATH}.scala"""
)
