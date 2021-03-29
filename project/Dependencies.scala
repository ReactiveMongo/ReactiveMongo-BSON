import sbt._
import sbt.Keys._

import dotty.tools.sbtplugin.DottyPlugin.autoImport._

object Dependencies {
  val specsVer = "4.10.6"

  val specsDeps = Def.setting {
    Seq(
      "org.specs2" %% "specs2-core" % specsVer,
      "org.specs2" %% "specs2-junit" % specsVer).
      map(_.withDottyCompat(scalaVersion.value))
  }

  val slf4jApi = "org.slf4j" % "slf4j-simple" % "1.7.31"
}
