import Dependencies._

ThisBuild / organization := "org.reactivemongo"

ThisBuild / autoAPIMappings := true

val baseArtifact = "reactivemongo-bson"

name := "reactivemongo-biːsən"

resolvers in ThisBuild ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/")

ThisBuild / mimaFailOnNoPrevious := false

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

val reactivemongoShaded = Def.setting[ModuleID] {
  "org.reactivemongo" % "reactivemongo-shaded" % (version in ThisBuild).value
}

val discipline = Def.setting[ModuleID] {
  if (scalaVersion.value startsWith "2.10.") {
    "org.typelevel" %% "discipline" % "0.9.0"
  } else {
    "org.typelevel" %% "discipline-specs2" % "1.0.0-RC1"
  }
}

val spireLaws = Def.setting[ModuleID] {
  val sm = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((major, minor)) => s"${major}.${minor}"
    case _ => "x"
  }

  val ver = {
    if (scalaVersion.value startsWith "2.10.") "0.15.0"
    else "0.17.0-M1"
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
      discipline.value,
      spireLaws.value,
      "com.chuusai" %% "shapeless" % "2.3.3",
      "org.slf4j" % "slf4j-simple" % "1.7.28").map(_ % Test),
    libraryDependencies ++= Seq(reactivemongoShaded.value % Provided)
  ))

lazy val monocle = (project in file("monocle")).settings(
  commonSettings ++ Seq(
    name := s"${baseArtifact}-monocle",
    description := "Monocle utilities for BSON values",
    libraryDependencies ++= Seq(
      "com.github.julien-truffaut" %% "monocle-core" % "1.6.0",
      "org.slf4j" % "slf4j-simple" % "1.7.28" % Test)
  )).dependsOn(api)

lazy val geo = (project in file("geo")).settings(
  commonSettings ++ Seq(
    name := s"${baseArtifact}-geo",
    description := "GeoJSON support for the BSON API",
    fork in Test := true,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.28" % Test)
  )
).dependsOn(api, monocle % Test)

lazy val compat = (project in file("compat")).settings(
  commonSettings ++ Seq(
    name := s"${baseArtifact}-compat",
    description := "Compatibility library between legacy & new BSON APIs",
    fork in Test := true,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.28" % Test,
      "org.reactivemongo" %% "reactivemongo-bson" % version.value % Provided)
  )
).dependsOn(api)

lazy val collection = (project in file("collection")).settings(
  commonSettings ++ Seq(
    name := s"${baseArtifact}-collection",
    description := "Collection/query library using new BSON serialization",
    fork in Test := true,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.28" % Test,
      "org.reactivemongo" %% "reactivemongo" % version.value % Provided)
  )).dependsOn(api, compat)

lazy val benchmarks = (project in file("benchmarks")).
  enablePlugins(JmhPlugin).settings(
    libraryDependencies ++= Seq(reactivemongoShaded.value),
    publish := ({}),
    publishTo := None,
  ).dependsOn(api % "compile->test")

lazy val msbCompat = (project in file("msb-compat")).settings(
  commonSettings ++ Seq(
    name := s"${baseArtifact}-msb-compat",
    description := "Compatibility library with mongo-scala-bson",
    crossScalaVersions := Seq("2.11.12", scalaVersion.value),
    libraryDependencies ++= Seq(
      "org.mongodb.scala" %% "mongo-scala-bson" % "2.6.0" % Provided),
  )
).dependsOn(api)

lazy val root = (project in file(".")).settings(
  publish := ({}),
  publishTo := None
).aggregate(api, compat, collection, benchmarks)
// !! Do not aggregate msbCompat as not 2.13
