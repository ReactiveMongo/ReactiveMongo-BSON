ThisBuild / scalaVersion := "2.12.17"

ThisBuild / crossScalaVersions := Seq(
  "2.11.12",
  scalaVersion.value,
  "2.13.10",
  "3.4.2"
)

crossVersion := CrossVersion.binary

ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Xfatal-warnings"
)

ThisBuild / scalacOptions ++= {
  if (scalaBinaryVersion.value startsWith "2.") {
    Seq(
      "-target:jvm-1.8",
      "-Xlint",
      "-g:vars"
    )
  } else Seq(
    "-Wconf:msg=.*should\\ not\\ .*infix\\ operator.*:s",
    "-Wconf:msg=.*vararg\\ splices.*:s",
    "-Wconf:msg=.*with\\ as\\ a\\ type\\ operator.*:s",
    "-Wconf:msg=.*deprecated\\ for\\ wildcard\\ arguments.*:s"
  )
}

ThisBuild / scalacOptions ++= {
  val sv = scalaBinaryVersion.value

  if (sv == "2.12") {
    Seq(
      "-Xmax-classfile-name",
      "128",
      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-value-discard",
      "-Ywarn-infer-any",
      "-Ywarn-unused",
      "-Ywarn-unused-import",
      "-Ywarn-macros:after"
    )
  } else if (sv == "2.11") {
    Seq(
      "-Xmax-classfile-name",
      "128",
      "-Yopt:_",
      "-Ydead-code",
      "-Yclosure-elim",
      "-Yconst-opt"
    )
  } else if (sv == "2.13") {
    Seq(
      "-explaintypes",
      "-Werror",
      "-Wnumeric-widen",
      "-Wdead-code",
      "-Wvalue-discard",
      "-Wextra-implicit",
      "-Wmacros:after",
      "-Wunused"
    )
  } else {
    Seq("-Wunused:all", "-language:implicitConversions")
  }
}

Compile / console / scalacOptions ~= {
  _.filterNot(o =>
    o.startsWith("-X") || o.startsWith("-Y") || o.startsWith("-P:silencer")
  )
}

val filteredScalacOpts: Seq[String] => Seq[String] = {
  _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
}

Compile / console / scalacOptions ~= filteredScalacOpts

Test / console / scalacOptions ~= filteredScalacOpts

// Silencer
ThisBuild / libraryDependencies ++= {
  if (!scalaBinaryVersion.value.startsWith("3")) {
    val silencerVersion = "1.17.13"

    Seq(
      compilerPlugin(
        ("com.github.ghik" %% "silencer-plugin" % silencerVersion)
          .cross(CrossVersion.full)
      ),
      ("com.github.ghik" %% "silencer-lib" % silencerVersion % Provided)
        .cross(CrossVersion.full)
    )
  } else Seq.empty
}

ThisBuild / scalacOptions ++= {
  val ver = scalaBinaryVersion.value

  if (ver startsWith "2.") {
    val base =
      "-P:silencer:globalFilters=.*value\\ macro.*\\ is never used;class\\ Response\\ in\\ package\\ protocol\\ is\\ deprecated;pattern\\ var\\ macro.*\\ is\\ never\\ used"

    if (ver == "2.13") {
      Seq(s"${base};a\\ type\\ was\\ inferred\\ to\\ be.*(Any|Object).*")
    } else {
      Seq(base)
    }
  } else Seq.empty
}
