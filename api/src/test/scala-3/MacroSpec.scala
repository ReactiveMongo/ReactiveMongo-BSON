import scala.util.{ Failure, Success, Try }

import reactivemongo.api.bson.{
  BSON,
  BSONDateTime,
  BSONDecimal,
  BSONDocument,
  BSONDocumentHandler,
  BSONDocumentReader,
  BSONDocumentWriter,
  BSONHandler,
  BSONInteger,
  BSONLong,
  BSONNull,
  BSONReader,
  BSONString,
  BSONValue,
  BSONWriter,
  FieldNaming,
  MacroConfiguration
}
import reactivemongo.api.bson.Macros, Macros.Annotations.NoneAsNull
import reactivemongo.api.bson.exceptions.{
  HandlerException,
  TypeDoesNotMatchException
}

import org.specs2.execute._
import org.specs2.matcher.MatchResult
import org.specs2.matcher.TypecheckMatchers._

import com.github.ghik.silencer.silent

import Typecheck._

object MacroTest {
  case class Person(firstName: String, lastName: String)

  object Union {
    sealed trait UT
  }

  final class FooVal(val v: Int) extends AnyVal

  case class OptionalAsNull(name: String, @NoneAsNull value: Option[String])
}

import reactivemongo.api.bson.TestUtils.typecheck

// TODO: Common MacroSpec
class MacroSpec extends org.specs2.mutable.Specification:
  "Macros".title

  import MacroTest._

  "Utility macros" should {
    "provide 'migrationRequired' compilation error" in {
      import reactivemongo.api.bson.migrationRequired

      typecheck("""migrationRequired[String]("Foo"): String""") must failWith(
        "Migration\\ required:\\ Foo"
      )
    }
  }

  "Configuration" should {
    import reactivemongo.api.bson.MacroCompilation.WithConfig

    "be resolved from the call site" in {
      val resolved = WithConfig.resolve

      resolved must_=== MacroConfiguration(
        fieldNaming = FieldNaming.SnakeCase
      ) and {
        resolved must not(beEqualTo(MacroConfiguration()))
      }
    }

    "be resolved from implicit scope" in {
      implicit def conf: MacroConfiguration = WithConfig.config

      val resolved = WithConfig.implicitConf

      resolved must_=== MacroConfiguration(
        fieldNaming = FieldNaming.SnakeCase
      ) and {
        resolved must not(beEqualTo(MacroConfiguration()))
      }
    }
  }

  "Formatter" should {
    "write empty option as null" in {
      Macros
        .writer[OptionalAsNull]
        .writeTry(OptionalAsNull("asNull", None)) must beSuccessfulTry(
        BSONDocument("name" -> "asNull", "value" -> BSONNull)
      )
    } tag "wip"
  }
end MacroSpec
