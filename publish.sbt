@inline def env(n: String): String = sys.env.get(n).getOrElse(n)

val repoName = env("PUBLISH_REPO_NAME")
val repoUrl = env("PUBLISH_REPO_URL")

ThisBuild / licenses := {
  Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
}

ThisBuild / publishMavenStyle := true

ThisBuild / publishArtifact in Test := false

ThisBuild / publishTo := Some(repoUrl).map(repoName at _)

ThisBuild / credentials += Credentials(repoName, env("PUBLISH_REPO_ID"),
  env("PUBLISH_USER"), env("PUBLISH_PASS"))

ThisBuild / pomIncludeRepository := { _ => false }

ThisBuild / homepage := Some(url("http://reactivemongo.org"))

ThisBuild / autoAPIMappings := true

ThisBuild / pomExtra := (
  <scm>
    <url>git://github.com/ReactiveMongo/ReactiveMongo-BSON.git</url>
      <connection>scm:git://github.com/ReactiveMongo/ReactiveMongo-BSON.git</connection>
      </scm>
    <developers>
    <developer>
    <id>cchantep</id>
    <name>Cedric Chantepie</name>
    <url>https://github.com/cchantep</url>
      </developer>
    </developers>)
