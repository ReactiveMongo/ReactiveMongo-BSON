ThisBuild / scalafmtOnCompile := true

// Scalafix
inThisBuild(
  List(
    semanticdbVersion := scalafixSemanticdb.revision
  )
)
