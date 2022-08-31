import sbt._
import sbt.Keys._

object Dependencies {
  val specsVer = "4.10.6"

  val specsDeps = Def.setting {
    Seq(
      "org.specs2" %% "specs2-core" % specsVer,
      "org.specs2" %% "specs2-junit" % specsVer
    ).map(_.cross(CrossVersion.for3Use2_13))
  }

  val slf4jSimple = "org.slf4j" % "slf4j-simple" % "2.0.0"
}
