import Dependencies._

organization := "org.reactivemongo"

autoAPIMappings := true

val baseArtifact = "reactivemongo-bson"

name := "reactivemongo-biːsən"

val commonSettings = Seq(
  Compile / unmanagedSourceDirectories += {
    val base = (Compile / sourceDirectory).value

    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, n)) if n >= 13 =>
        base / "scala-2.13+"

      case Some((3, _)) =>
        base / "scala-2.13+"

      case _ =>
        base / "scala-2.13-"
    }
  }
)

val reactivemongoShaded = Def.setting[Seq[ModuleID]] {
  val v = (ThisBuild / version).value

  if (Common.useShaded.value) {
    Seq("org.reactivemongo" % "reactivemongo-shaded" % v % Provided)
  } else {
    Seq(
      "io.netty" % "netty-handler" % "4.1.51.Final" % Provided,
      "org.reactivemongo" %% "reactivemongo-alias" % v % Provided
    )
  }
}

val spireLaws = Def.setting[ModuleID] {
  val sm = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((major, minor)) => s"${major}.${minor}"
    case _                    => "x"
  }
  val ver = {
    if (scalaBinaryVersion.value == "2.11") "0.16.2"
    else "0.17.0"
  }

  ("org.typelevel" %% "spire-laws" % ver)
    .exclude("org.typelevel", s"discipline-scalatest_${sm}"),
}

ThisBuild / libraryDependencies ++= specsDeps.value.map(_ % Test)

lazy val api = (project in file("api"))
  .enablePlugins(VelocityPlugin)
  .settings(
    commonSettings ++ Seq(
      name := s"${baseArtifact}-api",
      description := "New BSON API",
      Compile / compile / scalacOptions ++= {
        // !! Cannot be set in `compile.sbt`
        if (scalaBinaryVersion.value == "3") {
          Seq("-Ysafe-init")
        } else {
          Seq.empty
        }
      },
      libraryDependencies ++= Seq(
        "org.specs2" %% "specs2-scalacheck" % specsVer,
        "org.specs2" %% "specs2-matcher-extra" % specsVer,
        "org.typelevel" %% "discipline-specs2" % "1.1.3",
        spireLaws.value
      ).map(_.cross(CrossVersion.for3Use2_13) % Test),
      libraryDependencies ++= Seq(
        slf4jSimple % Test,
        "org.slf4j" % "slf4j-api" % "2.0.4"
      ),
      libraryDependencies ++= {
        if (scalaBinaryVersion.value startsWith "2.") {
          Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
        } else {
          Seq(
            "org.scala-lang" %% "scala3-compiler" % scalaVersion.value % Provided
          )
        }
      },
      libraryDependencies ++= reactivemongoShaded.value,
      mimaBinaryIssueFilters ++= {
        // TODO: Purge after release
        import com.typesafe.tools.mima.core._

        val fmt = ProblemFilters.exclude[FinalMethodProblem](_)

        Seq(
          fmt("reactivemongo.api.bson.BSONIdentityLowPriorityHandlers#BSONValueIdentity.readOpt"),
          fmt("reactivemongo.api.bson.BSONIdentityLowPriorityHandlers#BSONValueIdentity.readTry")
        )
      },
      // Mock silencer for Scala3
      Test / doc / scalacOptions ++= List("-skip-packages", "com.github.ghik"),
      Compile / packageBin / mappings ~= {
        _.filter { case (_, path) => !path.startsWith("com/github/ghik") }
      },
      Compile / packageSrc / mappings ~= {
        _.filter { case (_, path) => path != "silent.scala" }
      }
    )
  )

lazy val specs2 = (project in file("specs2"))
  .settings(
    commonSettings ++ Seq(
      name := s"${baseArtifact}-specs2",
      description := "Specs2 utility for BSON",
      libraryDependencies ++= specsDeps.value
    )
  )
  .dependsOn(api)

lazy val monocle = (project in file("monocle"))
  .enablePlugins(VelocityPlugin)
  .settings(
    commonSettings ++ Seq(
      name := s"${baseArtifact}-monocle",
      description := "Monocle utilities for BSON values",
      libraryDependencies ++= Seq(
        ("com.github.julien-truffaut" %% "monocle-core" % {
          val ver = scalaBinaryVersion.value

          if (ver == "2.11") "1.6.0-M1"
          else "2.1.0"
        }).cross(CrossVersion.for3Use2_13),
        slf4jSimple % Test
      )
    )
  )
  .dependsOn(api)

lazy val geo = (project in file("geo"))
  .settings(
    commonSettings ++ Seq(
      name := s"${baseArtifact}-geo",
      description := "GeoJSON support for the BSON API",
      Test / fork := true,
      libraryDependencies += slf4jSimple % Test
    )
  )
  .dependsOn(api, monocle % Test)

lazy val benchmarks = (project in file("benchmarks"))
  .enablePlugins(JmhPlugin)
  .settings(
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= reactivemongoShaded.value,
    publish := ({}),
    publishTo := None
  )
  .dependsOn(api % "compile->test")

lazy val msbCompat = (project in file("msb-compat"))
  .settings(
    commonSettings ++ Seq(
      name := s"${baseArtifact}-msb-compat",
      description := "Compatibility library with mongo-scala-bson",
      libraryDependencies += {
        val sv = scalaBinaryVersion.value
        val v = {
          if (sv == "2.13" || sv.startsWith("3.")) "4.3.3"
          else "2.8.0"
        }

        ("org.mongodb.scala" %% "mongo-scala-bson" % v % Provided)
          .cross(CrossVersion.for3Use2_13)
      },
      scalacOptions := (Def.taskDyn {
        val opts = scalacOptions.value
        val sv = scalaBinaryVersion.value

        Def.task {
          if (sv == "2.13" || sv.startsWith("3.")) {
            opts.filterNot(_.startsWith("-W"))
          } else {
            opts
          }
        }
      }).value
    )
  )
  .dependsOn(api)

lazy val root = (project in file("."))
  .settings(
    publish := ({}),
    publishTo := None,
    mimaPreviousArtifacts := Set.empty
  )
  .aggregate(api, specs2, benchmarks, msbCompat, geo, monocle)
  .dependsOn(api, specs2 /* for compiled code samples */ )
// !! Do not aggregate msbCompat as not 2.13
