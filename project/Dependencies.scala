import sbt._
import sbt.Keys._

object Dependencies {

  val specsVer = Def.setting {
    if (scalaBinaryVersion.value == "2.11") "4.10.6"
    else "4.19.0"
  }

  val specsDeps = Def.setting {
    Seq(
      "org.specs2" %% "specs2-core" % specsVer.value,
      "org.specs2" %% "specs2-junit" % specsVer.value
    ).map(_.cross(CrossVersion.for3Use2_13))
  }

  val slf4jVersion = "2.0.7"

  val slf4jSimple = "org.slf4j" % "slf4j-simple" % slf4jVersion
}
