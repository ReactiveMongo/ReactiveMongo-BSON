import Dependencies._

organization := "org.reactivemongo"

autoAPIMappings := true

val baseArtifact = "reactivemongo-bson"

name := "reactivemongo-biːsən"

resolvers in ThisBuild ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  "Typesafe repository releases" at "https://repo.typesafe.com/typesafe/releases/")

val commonSettings = Seq(
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
  }
)

val reactivemongoShaded = Def.setting[Seq[ModuleID]] {
  val v = (version in ThisBuild).value

  if (Common.useShaded.value) {
    Seq("org.reactivemongo" % "reactivemongo-shaded" % v % Provided)
  } else {
    Seq(
      "io.netty" % "netty-handler" % "4.1.51.Final" % Provided,
      "org.reactivemongo" %% "reactivemongo-alias" % v % Provided)
  }
}

val spireLaws = Def.setting[ModuleID] {
  val sm = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((major, minor)) => s"${major}.${minor}"
    case _ => "x"
  }
  val ver = {
    if (scalaBinaryVersion.value == "2.11") "0.16.2"
    else "0.17.0"
  }

  ("org.typelevel" %% "spire-laws" % ver).
    exclude("org.typelevel", s"discipline-scalatest_${sm}"),
}

libraryDependencies in ThisBuild ++= specsDeps.map(_ % Test)

lazy val api = (project in file("api")).settings(
  commonSettings ++ Seq(
    name := s"${baseArtifact}-api",
    description := "New BSON API",
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-scalacheck" % specsVer,
      "org.specs2" %% "specs2-matcher-extra" % specsVer,
      "org.typelevel" %% "discipline-specs2" % "1.1.3",
      spireLaws.value,
      "org.slf4j" % "slf4j-simple" % "1.7.32").map(_ % Test),
    libraryDependencies ++= reactivemongoShaded.value
  ))

lazy val specs2 = (project in file("specs2")).settings(
  commonSettings ++ Seq(
    name := s"${baseArtifact}-specs2",
    description := "Specs2 utility for BSON",
    libraryDependencies ++= specsDeps)
).dependsOn(api)

lazy val monocle = (project in file("monocle")).settings(
  commonSettings ++ Seq(
    name := s"${baseArtifact}-monocle",
    description := "Monocle utilities for BSON values",
    libraryDependencies ++= Seq(
      "com.github.julien-truffaut" %% "monocle-core" % {
        val ver = scalaBinaryVersion.value

        if (ver == "2.11") "1.6.0-M1"
        else "2.0.0-RC1"
      },
      slf4jApi % Test)
  )).dependsOn(api)

lazy val geo = (project in file("geo")).settings(
  commonSettings ++ Seq(
    name := s"${baseArtifact}-geo",
    description := "GeoJSON support for the BSON API",
    fork in Test := true,
    libraryDependencies += slf4jApi % Test
  )
).dependsOn(api, monocle % Test)

lazy val benchmarks = (project in file("benchmarks")).
  enablePlugins(JmhPlugin).settings(
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= reactivemongoShaded.value,
    publish := ({}),
    publishTo := None,
  ).dependsOn(api % "compile->test")

lazy val msbCompat = (project in file("msb-compat")).settings(
  commonSettings ++ Seq(
    name := s"${baseArtifact}-msb-compat",
    description := "Compatibility library with mongo-scala-bson",
    libraryDependencies += {
      val v = if (scalaBinaryVersion.value != "2.13") "2.8.0" else "4.2.3"

      "org.mongodb.scala" %% "mongo-scala-bson" % v % Provided
    },
    scalacOptions := (Def.taskDyn {
      val opts = scalacOptions.value


      Def.task {
        if (scalaBinaryVersion.value == "2.13") {
          opts.filterNot(_.startsWith("-W"))
        } else {
          opts
        }
      }
    }).value
  )
).dependsOn(api)

lazy val root = (project in file(".")).settings(
  publish := ({}),
  publishTo := None,
  mimaPreviousArtifacts := Set.empty
).aggregate(api, specs2, benchmarks, msbCompat, geo, monocle).
  dependsOn(api, specs2/* for compiled code samples */)
// !! Do not aggregate msbCompat as not 2.13
