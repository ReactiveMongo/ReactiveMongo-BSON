import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

import com.typesafe.tools.mima.plugin.MimaKeys._
import com.typesafe.tools.mima.core._, ProblemFilters._

object Common extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin

  val useShaded = settingKey[Boolean](
    "Use ReactiveMongo-Shaded (see system property 'reactivemongo.shaded')"
  )

  override def projectSettings = Seq(
    organization := "org.reactivemongo",
    autoAPIMappings := true,
    useShaded := sys.env.get("REACTIVEMONGO_SHADED").fold(true)(_.toBoolean),
    version := {
      val ver = (ThisBuild / version).value
      val suffix = {
        if (useShaded.value) "" // default ~> no suffix
        else "noshaded"
      }

      if (suffix.isEmpty) {
        ver
      } else {
        ver.span(_ != '-') match {
          case (_, "") => s"${ver}.${suffix}"

          case (a, b) =>
            s"${a}-${suffix}.${b drop 1}"
        }
      }
    },
    target := {
      if (useShaded.value) target.value / "shaded"
      else target.value / "noshaded"
    },
    testFrameworks ~= { _.filterNot(_ == TestFrameworks.ScalaTest) },
    Compile / doc / scalacOptions := {
      val opts = (Test / scalacOptions).value ++ Seq(
        "-unchecked",
        "-deprecation"
      ) ++ Opts.doc.title(name.value)

      if (scalaBinaryVersion.value startsWith "3") {
        opts ++ Seq("-skip-by-id", "com.github.ghik.silencer")
      } else {
        opts ++ Seq("-skip-packages", "highlightextractor", "-implicits")
      }
    },
    credentials ++= Seq(
      Credentials(
        "", // Empty realm credential - this one is actually used by Coursier!
        "central.sonatype.com",
        Publish.env("SONATYPE_USER"),
        Publish.env("SONATYPE_PASS")
      )
    ),
    resolvers ++= Seq(
      "Central Testing repository" at "https://central.sonatype.com/api/v1/publisher/deployments/download"
    ),
    resolvers += Resolver.typesafeRepo("releases"),
    mimaFailOnNoPrevious := false,
    mimaPreviousArtifacts := {
      if (scalaBinaryVersion.value startsWith "2.") {
        Set(organization.value %% name.value.toLowerCase % "1.0.0")
      } else {
        Set.empty
      }
    },
    mimaBinaryIssueFilters ++= Seq(missingMethodInOld),
    Test / compile / scalacOptions ~= {
      val excluded = Set("-Xfatal-warnings")

      _.filterNot(excluded.contains)
    }
  )

  private val missingMethodInOld: ProblemFilter = {
    case ReversedAbstractMethodProblem(_) | ReversedMissingMethodProblem(_) =>
      false

    case DirectMissingMethodProblem(old)         => old.nonAccessible
    case InheritedNewAbstractMethodProblem(_, _) => false
    case IncompatibleResultTypeProblem(old, _)   => old.nonAccessible
    case IncompatibleMethTypeProblem(old, _)     => old.nonAccessible
    case MissingClassProblem(old)                => !old.isPublic
    case MissingTypesProblem(_, old)             => !old.exists(_.isPublic)
    case AbstractClassProblem(old)               => !old.isPublic
    case UpdateForwarderBodyProblem(old)         => !old.isPublic
    case _: NewMixinForwarderProblem             => false
    case _                                       => true
  }
}
