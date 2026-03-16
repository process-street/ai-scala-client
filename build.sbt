import sbt.Keys.test

// Supported versions
val scala212 = "2.12.18"
val scala213 = "2.13.11"
val scala3 = "3.2.2"

ThisBuild / organization := "io.cequence"
ThisBuild / scalaVersion := scala213
ThisBuild / version := "1.3.0.RC.2"
ThisBuild / isSnapshot := false

lazy val commonSettings = Seq(
  libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.16",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.16" % Test,
  libraryDependencies += "org.scalatestplus" %% "mockito-4-11" % "3.2.16.0" % Test,
  libraryDependencies ++= extraTestDependencies(scalaVersion.value),
  crossScalaVersions := List(scala212, scala213, scala3)
)

def extraTestDependencies(scalaVersion: String) =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 12)) =>
      Seq(
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.1" % Test
      )

    case Some((2, 13)) =>
      Seq(
        "org.apache.pekko" %% "pekko-actor-testkit-typed" % "1.1.5" % Test
      )

    case Some((3, _)) =>
      Seq(
        "org.apache.pekko" % "pekko-actor-testkit-typed_2.13" % "1.1.5" % Test
      )

    case _ =>
      Nil
  }

// ws-client submodule settings (from ws-client/build.sbt CD-648 branch)

lazy val playJsonVersion = settingKey[String]("Play JSON version to use")

inThisBuild(
  playJsonVersion := {
    scalaVersion.value match {
      case "2.12.18" => "2.8.2"
      case "2.13.11" => "3.0.4"
      case "3.2.2"   => "2.10.0-RC6"
      case _         => "3.0.4"
    }
  }
)

val pekkoVersion = "1.1.5"
val pekkoHttpVersion = "1.1.0"

lazy val pekkoStreamLibs = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) =>
      Seq(
        "com.typesafe.akka" %% "akka-stream" % "2.6.1" exclude("com.typesafe.play", "play-json")
      )
    case Some((2, 13)) =>
      Seq(
        "org.apache.pekko" %% "pekko-stream" % pekkoVersion
      )
    case Some((3, 2)) =>
      Seq(
        "org.apache.pekko" % "pekko-stream_2.13" % pekkoVersion
      )
    case _ =>
      throw new Exception("Unsupported scala version")
  }
}

val wsLoggingLibs = Def.setting {
  Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
    "ch.qos.logback" % "logback-classic" % "1.4.14"
  )
}

def typesafePlayWS(version: String) = Seq(
  "com.typesafe.play" %% "play-ahc-ws-standalone" % version exclude("com.typesafe.play", "play-json"),
  "com.typesafe.play" %% "play-ws-standalone-json" % version exclude("com.typesafe.play", "play-json")
)

def orgPlayWS(version: String) = Seq(
  "org.playframework" %% "play-ahc-ws-standalone" % version,
  "org.playframework" %% "play-ws-standalone-json" % version
)

lazy val playWsDependencies = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => typesafePlayWS("2.1.11")
    case Some((2, 13)) => orgPlayWS("3.0.10")
    case Some((3, 2))  => typesafePlayWS("2.2.0-M2")
    case Some((3, 3))  => orgPlayWS("3.0.10")
    case _             => orgPlayWS("3.0.10")
  }
}

lazy val playJsonDependency = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => "com.typesafe.play" %% "play-json" % playJsonVersion.value
    case _             => "org.playframework" %% "play-json" % playJsonVersion.value
  }
}

lazy val wsCommonSettings = Seq(
  crossScalaVersions := List(scala212, scala213, scala3),
  publish / skip := true
)

lazy val ws_client_core =
  (project in file("ws-client/ws-client-core")).settings(
    wsCommonSettings,
    name := "ws-client-core",
    libraryDependencies += playJsonDependency.value,
    libraryDependencies += "com.typesafe" % "config" % "1.4.3",
    libraryDependencies ++= wsLoggingLibs.value
  )

lazy val ws_client_core_akka =
  (project in file("ws-client/ws-client-core-akka")).settings(
    wsCommonSettings,
    name := "ws-client-core-akka",
    libraryDependencies ++= pekkoStreamLibs.value
  ).dependsOn(ws_client_core)

lazy val json_repair =
  (project in file("ws-client/json-repair")).settings(
    wsCommonSettings,
    name := "json-repair",
    libraryDependencies += playJsonDependency.value,
    libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.16",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.16" % Test,
    libraryDependencies ++= wsLoggingLibs.value
  )

lazy val ws_client_play =
  (project in file("ws-client/ws-client-play")).settings(
    wsCommonSettings,
    name := "ws-client-play",
    libraryDependencies ++= playWsDependencies.value
  ).dependsOn(ws_client_core_akka)
   .aggregate(ws_client_core, ws_client_core_akka, json_repair)

lazy val ws_client_play_stream =
  (project in file("ws-client/ws-client-play-stream")).settings(
    wsCommonSettings,
    name := "ws-client-play-stream",
    libraryDependencies += "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion
  ).dependsOn(ws_client_core_akka, ws_client_play)
   .aggregate(ws_client_core, ws_client_core_akka, ws_client_play)

// ai-scala-client modules

lazy val core =
  (project in file("openai-core"))
    .settings(commonSettings *)
    .dependsOn(ws_client_core_akka, json_repair)

lazy val client =
  (project in file("openai-client"))
    .settings(commonSettings *)
    .dependsOn(core, ws_client_core, ws_client_play)
    .aggregate(core)

lazy val client_stream = (project in file("openai-client-stream"))
  .settings(commonSettings *)
  .dependsOn(client, ws_client_core, ws_client_play, ws_client_play_stream)
  .aggregate(client)

// note that for anthropic_client we provide a streaming extension within the module as well
lazy val anthropic_client = (project in file("anthropic-client"))
  .settings(commonSettings *)
  .dependsOn(core, ws_client_core, ws_client_play, ws_client_play_stream)
  .aggregate(core, client, client_stream)

lazy val google_vertexai_client = (project in file("google-vertexai-client"))
  .settings(commonSettings *)
  .dependsOn(core)
  .aggregate(core, client, client_stream)

lazy val google_gemini_client = (project in file("google-gemini-client"))
  .settings(commonSettings *)
  .dependsOn(core, ws_client_core, ws_client_play, ws_client_play_stream)
  .aggregate(core, client, client_stream)

// note that for perplexity_client we provide a streaming extension within the module as well
lazy val perplexity_sonar_client = (project in file("perplexity-sonar-client"))
  .settings(commonSettings *)
  .dependsOn(core, ws_client_core, ws_client_play, ws_client_play_stream)
  .aggregate(core, client, client_stream)

lazy val count_tokens = (project in file("openai-count-tokens"))
  .settings(
    (commonSettings ++ Seq(definedTestNames in Test := Nil)) *
  )
  .dependsOn(client)
  .aggregate(
    anthropic_client,
    google_vertexai_client,
    perplexity_sonar_client,
    google_gemini_client
  )

lazy val all = (project in file("openai-all"))
  .settings(commonSettings *)
  .dependsOn(
    client_stream,
    anthropic_client,
    google_vertexai_client,
    google_gemini_client,
    perplexity_sonar_client,
    count_tokens
  )

lazy val guice = (project in file("openai-guice"))
  .settings(commonSettings *)
  .dependsOn(client)
  .aggregate(count_tokens, all)

lazy val examples = (project in file("openai-examples"))
  .settings(commonSettings *)
  .dependsOn(
    client_stream,
    anthropic_client,
    google_vertexai_client,
    perplexity_sonar_client,
    google_gemini_client
  )
  .aggregate(
    client_stream,
    anthropic_client,
    google_vertexai_client,
    perplexity_sonar_client,
    google_gemini_client
  )

// POM settings for Sonatype
ThisBuild / homepage := Some(
  url("https://github.com/cequence-io/openai-scala-client")
)

ThisBuild / sonatypeProfileName := "io.cequence"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/cequence-io/openai-scala-client"),
    "scm:git@github.com:cequence-io/openai-scala-client.git"
  )
)

ThisBuild / developers := List(
  Developer(
    "bnd",
    "Peter Banda",
    "peter.banda@protonmail.com",
    url("https://peterbanda.net")
  )
)

ThisBuild / licenses += "MIT" -> url("https://opensource.org/licenses/MIT")

ThisBuild / publishMavenStyle := true

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

ThisBuild / publishTo := sonatypePublishToBundle.value

addCommandAlias(
  "validateCode",
  List(
    "scalafix",
    "scalafmtSbtCheck",
    "scalafmtCheckAll",
    "test:scalafix",
    "test:scalafmtCheckAll"
  ).mkString(";")
)

addCommandAlias(
  "formatCode",
  List(
    "scalafmt",
    "scalafmtSbt",
    "Test/scalafmt"
  ).mkString(";")
)

addCommandAlias(
  "testWithCoverage",
  List(
    "coverage",
    "test",
    "coverageReport"
  ).mkString(";")
)

inThisBuild(
  List(
    scalacOptions += "-Ywarn-unused",
//    scalaVersion := scala3,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)
