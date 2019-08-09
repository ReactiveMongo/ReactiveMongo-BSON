import sbt._

object Dependencies {
  val specsVer = "4.7.0"

  val specsDeps = Seq(
    "org.specs2" %% "specs2-core" % specsVer,
    "org.specs2" %% "specs2-junit" % specsVer)

}
