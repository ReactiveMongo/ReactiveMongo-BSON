import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

import com.typesafe.tools.mima.plugin.MimaKeys.mimaFailOnNoPrevious

object Common extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin

  val silencerVersion = "1.4.2"

  override def projectSettings = Seq(
    organization := "org.reactivemongo",
    autoAPIMappings := true,
    scalacOptions ++= {
      if (scalaBinaryVersion.value != "2.10") {
        Seq("-P:silencer:globalFilters=.*value\\ macro.*\\ is never used;class\\ Response\\ in\\ package\\ protocol\\ is\\ deprecated;pattern\\ var\\ macro.*\\ is\\ never\\ used")
      } else {
        Seq.empty
      }
    },
    scalacOptions ++= {
      val v = scalaBinaryVersion.value

      if (v == "2.12") {
        Seq(
          "-Ywarn-numeric-widen",
          "-Ywarn-dead-code",
          "-Ywarn-value-discard",
          "-Ywarn-infer-any",
          "-Ywarn-unused",
          "-Ywarn-unused-import",
          "-Ywarn-macros:after"
        )
      } else if (v == "2.11") {
        Seq("-Yopt:_", "-Ydead-code", "-Yclosure-elim", "-Yconst-opt")
      } else if (v != "2.10") {
        Seq("-Wmacros:after")
      } else {
        Seq.empty
      }
    },
    scalacOptions in (Compile, doc) := (scalacOptions in Test).value ++ Seq(
      "-unchecked", "-deprecation",
      /*"-diagrams", */"-implicits", "-skip-packages", "highlightextractor") ++
      Opts.doc.title(name.value),
    unmanagedSourceDirectories in Compile += {
      val base = (sourceDirectory in Compile).value

      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 13 => base / "scala-2.13+"
        case _                       => base / "scala-2.13-"
      }
    },
    resolvers ++= Seq(
      Resolver.sonatypeRepo("snapshots"),
      "Typesafe repository releases" at "https://repo.typesafe.com/typesafe/releases/"),
    mimaFailOnNoPrevious := false,
    libraryDependencies ++= {
      if (scalaBinaryVersion.value != "2.10") {
        Seq(
          compilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVersion),
          "com.github.ghik" %% "silencer-lib" % silencerVersion % Provided
        )
      } else {
        Seq.empty
      }
    }
  )
}
