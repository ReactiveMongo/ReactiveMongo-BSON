ThisBuild / scalaVersion := "2.12.12"

ThisBuild / crossScalaVersions := Seq(
  "2.11.12", scalaVersion.value, "2.13.3")

ThisBuild / crossVersion := CrossVersion.binary

ThisBuild / scalacOptions ++= Seq(
  "-encoding", "UTF-8", "-target:jvm-1.8",
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Xlint",
  "-g:vars",
  "-Xfatal-warnings"
)

ThisBuild / scalacOptions ++= {
  if (scalaBinaryVersion.value == "2.12") {
    Seq(
      "-Xmax-classfile-name", "128",
      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-value-discard",
      "-Ywarn-infer-any",
      "-Ywarn-unused",
      "-Ywarn-unused-import",
      "-Ywarn-macros:after"
    )
  } else if (scalaBinaryVersion.value == "2.11") {
    Seq(
      "-Xmax-classfile-name", "128",
      "-Yopt:_", "-Ydead-code", "-Yclosure-elim", "-Yconst-opt")
  } else {
    Seq("-Wmacros:after")
  }
}

ThisBuild / scalacOptions in (Compile, console) ~= {
  _.filterNot(o =>
    o.startsWith("-X") || o.startsWith("-Y") || o.startsWith("-P:silencer"))
}

scalacOptions in Test ~= {
  _.filterNot(_ == "-Xfatal-warnings")
}

scalacOptions in (Compile, console) ~= {
  _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
}

scalacOptions in (Test, console) ~= {
  _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
}

// Silencer
ThisBuild / libraryDependencies ++= {
  val silencerVersion = {
    if (scalaBinaryVersion.value == "2.11") "1.7.1"
    else "1.7.0"
  }

  Seq(
    compilerPlugin(("com.github.ghik" %% "silencer-plugin" % silencerVersion).
      cross(CrossVersion.full)),
    ("com.github.ghik" %% "silencer-lib" % silencerVersion % Provided).
      cross(CrossVersion.full)
  )
}

ThisBuild / scalacOptions ++= Seq("-P:silencer:globalFilters=.*value\\ macro.*\\ is never used;class\\ Response\\ in\\ package\\ protocol\\ is\\ deprecated;pattern\\ var\\ macro.*\\ is\\ never\\ used")
