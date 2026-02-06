ThisBuild / scalaVersion := "2.12.20"

val scala3Lts = "3.3.7"

ThisBuild / crossScalaVersions := Seq(
  "2.11.12",
  scalaVersion.value,
  "2.13.17",
  scala3Lts
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
      "-Xlint",
      "-g:vars"
    )
  } else {
    Seq(
      "-Wconf:msg=.*unused.*:s",
      "-Wconf:msg=.*duplicated\\ at\\ each\\ inline\\ site.*:s",
      "-Wconf:msg=.*should\\ not\\ .*infix\\ operator.*:s",
      "-Wconf:msg=.*vararg\\ splices.*:s",
      "-Wconf:msg=.*with\\ as\\ a\\ type\\ operator.*:s",
      "-Wconf:msg=.*deprecated\\ for\\ wildcard\\ arguments.*:s"
    )
  }
}

ThisBuild / scalacOptions ++= {
  val sv = scalaBinaryVersion.value

  if (sv == "2.12") {
    Seq(
      "-target:jvm-1.8",
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
      "-target:jvm-1.8",
      "-Xmax-classfile-name",
      "128",
      "-Yopt:_",
      "-Ydead-code",
      "-Yclosure-elim",
      "-Yconst-opt"
    )
  } else if (sv == "2.13") {
    Seq(
      "-release",
      "8",
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
    Seq("-release", "8", "-Wunused:all", "-language:implicitConversions")
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
  val v = scalaBinaryVersion.value

  if (!v.startsWith("3")) {
    val silencerVersion: String = {
      if (v == "2.11") {
        "1.17.13"
      } else {
        "1.7.19"
      }
    }

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
    if (ver == "2.13") {
      Seq(
        "-Wconf:msg=.*inferred\\ to\\ be.*(Any|Object).*:is",
        "-Wconf:msg=.*parameter\\ macro(Val|Doc)\\ .*is\\ never\\ used:is",
        "-Wconf:msg=.*pattern\\ var\\ macro.*\\ is\\ never\\ used:is",
        "-Wconf:msg=.*bh\\ .*MacroSpec.*:is",
        "-Wconf:msg=.*local\\ type\\ Opts\\ is\\ never.*:is",
        "-Wconf:msg=.*WithImplicit2\\ .*never\\ used.*:is"
      )
    } else {
      Seq("-P:silencer:globalFilters=.*value\\ macro.*\\ is never used;class\\ Response\\ in\\ package\\ protocol\\ is\\ deprecated;pattern\\ var\\ macro.*\\ is\\ never\\ used")
    }
  } else Seq.empty
}
