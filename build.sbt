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
    .exclude("org.typelevel", s"discipline-scalatest_${sm}")
}

ThisBuild / libraryDependencies ++= {
  if (scalaBinaryVersion.value == "2.12") {
    specsDeps.value.map { d =>
      (d % Test).exclude("org.scala-lang.modules", "*")
    }
  } else {
    specsDeps.value.map(_ % Test)
  }
}

lazy val api = (project in file("api"))
  .enablePlugins(VelocityPlugin)
  .settings(
    commonSettings ++ Seq(
      name := s"${baseArtifact}-api",
      description := "New BSON API",
      libraryDependencies ++= Seq(
        "org.specs2" %% "specs2-scalacheck" % specsVer.value,
        "org.specs2" %% "specs2-matcher-extra" % specsVer.value,
        "org.typelevel" %% "discipline-specs2" % "1.1.3",
        spireLaws.value
      ).map(_.cross(CrossVersion.for3Use2_13 /* TODO: Remove */ ) % Test),
      libraryDependencies ++= Seq(
        slf4jSimple % Test,
        "org.slf4j" % "slf4j-api" % slf4jVersion
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
    libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always,
    scalacOptions ++= {
      if (scalaBinaryVersion.value startsWith "3") {
        Seq(
          "-Wconf:msg=.*has\\ been\\ deprecated.*\\ uninitialized.*:s"
        )
      } else {
        Seq.empty
      }
    },
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

lazy val builder = (project in file("builder"))
  .settings(
    commonSettings ++ Seq(
      name := s"${baseArtifact}-builder",
      description := "BSON builder utilities",
      mimaPreviousArtifacts := Set.empty,
      scalacOptions := scalacOptions.value.filter { o =>
        o != "-Ywarn-unused" && !o.startsWith("-Xlint")
      },
      scalacOptions ++= {
        if (scalaBinaryVersion.value == "2.13") {
          Seq(
            "-Wconf:msg=.*parameter\\ (i[0-9]|head|tail|notOption)\\ .*is\\ never\\ used:s"
          )
        } else {
          Seq.empty
        }
      },
      scaladocExtractorIncludes := {
        if (scalaBinaryVersion.value startsWith "2.") {
          "*.scala"
        } else {
          "*Compat.scala"
        }
      },
      doc / includeFilter := {
        if (scalaBinaryVersion.value startsWith "2.") {
          "*.md"
        } else {
          "*.disabled"
        }
      },
      Test / scalacOptions ++= {
        if (scalaBinaryVersion.value == "2.13") {
          Seq(
            "-Wconf:msg=.*local\\ val\\ .*is\\ never\\ used:s"
          )
        } else {
          Seq.empty
        }
      },
      libraryDependencies ++= {
        if (scalaBinaryVersion.value startsWith "2.") {
          Seq(
            "com.chuusai" %% "shapeless" % "2.3.3"
          )
        } else {
          Seq.empty
        }
      },
      libraryDependencies ++= Seq(
        "org.specs2" %% "specs2-scalacheck" % specsVer.value,
        "org.specs2" %% "specs2-matcher-extra" % specsVer.value
      ).map(_.cross(CrossVersion.for3Use2_13) % Test)
    )
  )
  .dependsOn(api)

lazy val root = (project in file("."))
  .disablePlugins(HighlightExtractorPlugin)
  .settings(
    publish := ({}),
    publishTo := None,
    mimaPreviousArtifacts := Set.empty
  )
  .aggregate(api, specs2, benchmarks, msbCompat, geo, monocle, builder)
  .dependsOn(api, specs2 /* for compiled code samples */ )
// !! Do not aggregate msbCompat as not 2.13
