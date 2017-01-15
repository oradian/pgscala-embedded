// ### BASIC SETTINGS ### //
organization := "org.pgscala.embedded"
name := "pgscala-embedded"
version := "0.0.1"

unmanagedSourceDirectories in Compile := Seq((scalaSource in Compile).value)
unmanagedSourceDirectories in Test := Seq((scalaSource in Test).value)

// ### DEPENDENCIES ### //
libraryDependencies ++= Seq(
  "ch.qos.logback" %  "logback-classic" % "1.1.8" % Test
)

// ### COMPILE SETTINGS ### //
crossScalaVersions := Seq("2.12.1", "2.11.8", "2.10.6")
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
