ThisBuild / scalaVersion := "2.12.8"

ThisBuild / crossScalaVersions := Seq(
  "2.11.12", scalaVersion.value, "2.13.0")

ThisBuild / crossVersion := CrossVersion.binary

ThisBuild / scalacOptions ++= Seq(
  "-encoding", "UTF-8", "-target:jvm-1.8",
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Xlint",
  "-g:vars",
  "-P:silencer:globalFilters=.*value\\ macro.*\\ is never used;class\\ Response\\ in\\ package\\ protocol\\ is\\ deprecated;pattern\\ var\\ macro.*\\ is\\ never\\ used",
  "-Xfatal-warnings"
)

ThisBuild / scalacOptions ++= {
  if (scalaVersion.value startsWith "2.12") {
    Seq(
      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-value-discard",
      "-Ywarn-infer-any",
      "-Ywarn-unused",
      "-Ywarn-unused-import",
      "-Ywarn-macros:after"
    )
  } else if (scalaVersion.value startsWith "2.11.") {
    Seq("-Yopt:_", "-Ydead-code", "-Yclosure-elim", "-Yconst-opt")
  } else {
    Seq("-Ywarn-macros:after")
  }
}

ThisBuild / scalacOptions in (Compile, console) ~= {
  _.filterNot(o =>
    o.startsWith("-X") || o.startsWith("-Y") || o.startsWith("-P:silencer"))
}

val silencerVersion = "1.4.1"

libraryDependencies in ThisBuild ++= Seq(
  compilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVersion),
  "com.github.ghik" %% "silencer-lib" % silencerVersion % Provided
)

scalacOptions in Test ~= {
  _.filterNot(_ == "-Xfatal-warnings")
}

scalacOptions in (Compile, console) ~= {
  _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
}

scalacOptions in (Test, console) ~= {
  _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
}
