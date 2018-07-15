organization := "cchantep" // "org.reactivemongo"

name := "reactivemongo-bson"

scalaVersion in ThisBuild := "2.12.6"

crossScalaVersions in ThisBuild := Seq("2.11.12", scalaVersion.value)

crossVersion in ThisBuild := CrossVersion.binary

scalacOptions ++= Seq(
  "-encoding", "UTF-8", "-target:jvm-1.8",
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Xfatal-warnings",
  "-Xlint",
  "-Ywarn-numeric-widen",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard",
  "-Ywarn-infer-any",
  "-Ywarn-unused",
  "-Ywarn-unused-import",
  "-g:vars"
)

scalacOptions in Compile ++= {
  if (!scalaVersion.value.startsWith("2.11.")) Nil
  else Seq(
    "-Yconst-opt",
    "-Yclosure-elim",
    "-Ydead-code",
    "-Yopt:_"
  )
}

scalacOptions in Test ~= {
  _.filterNot(_ == "-Xfatal-warnings")
}

scalacOptions in (Compile, doc) := (scalacOptions in Test).value

scalacOptions in (Compile, console) ~= {
  _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
}

scalacOptions in (Test, console) ~= {
  _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
}

scalacOptions in (Compile, doc) ++= Seq(
  "-Ywarn-dead-code", "-Ywarn-unused-import", "-unchecked", "-deprecation",
  /*"-diagrams", */"-implicits", "-skip-packages", "samples") ++
  Opts.doc.title("ReactiveMongo BSON API")

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/")

// Test
fork in Test := true

val specsVer = "4.2.0"

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2-core" % specsVer,
  "org.specs2" %% "specs2-scalacheck" % specsVer,
  "org.typelevel" %% "discipline" % "0.9.0",
  "org.typelevel" %% "spire-laws" % "0.15.0",
  "org.slf4j" % "slf4j-simple" % "1.7.13").map(_ % Test)

lazy val publishSettings = {
  @inline def env(n: String): String = sys.env.get(n).getOrElse(n)

  val repoName = env("PUBLISH_REPO_NAME")
  val repoUrl = env("PUBLISH_REPO_URL")

  Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    publishTo := Some(repoUrl).map(repoName at _),
    credentials += Credentials(repoName, env("PUBLISH_REPO_ID"),
        env("PUBLISH_USER"), env("PUBLISH_PASS")),
    pomIncludeRepository := { _ => false },
    licenses := {
      Seq("Apache 2.0" ->
        url("http://www.apache.org/licenses/LICENSE-2.0"))
    },
    homepage := Some(url("http://reactivemongo.org")),
    autoAPIMappings := true,
    pomExtra := (
      <scm>
        <url>git://github.com/ReactiveMongo/ReactiveMongo-Play-Json.git</url>
        <connection>scm:git://github.com/ReactiveMongo/ReactiveMongo-Play-Json.git</connection>
      </scm>
      <developers>
        <developer>
          <id>cchantep</id>
          <name>Cedric Chantepie</name>
          <url>http://github.org/cchantep</url>
        </developer>
      </developers>))
}

// Scalariform
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

ScalariformKeys.preferences := ScalariformKeys.preferences.value.
  setPreference(AlignParameters, false).
  setPreference(AlignSingleLineCaseStatements, true).
  setPreference(CompactControlReadability, false).
  setPreference(CompactStringConcatenation, false).
  setPreference(DoubleIndentConstructorArguments, true).
  setPreference(FormatXml, true).
  setPreference(IndentLocalDefs, false).
  setPreference(IndentPackageBlocks, true).
  setPreference(IndentSpaces, 2).
  setPreference(MultilineScaladocCommentsStartOnFirstLine, false).
  setPreference(PreserveSpaceBeforeArguments, false).
  setPreference(DanglingCloseParenthesis, Preserve).
  setPreference(RewriteArrowSymbols, false).
  setPreference(SpaceBeforeColon, false).
  setPreference(SpaceInsideBrackets, false).
  setPreference(SpacesAroundMultiImports, true).
  setPreference(SpacesWithinPatternBinders, true)

/*
scapegoatVersion in ThisBuild := "1.3.4"

scapegoatReports in ThisBuild := Seq("xml")
 */

lazy val root = (project in file(".")).
  settings(publishSettings)
