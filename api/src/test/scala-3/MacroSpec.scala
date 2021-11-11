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
import reactivemongo.api.bson.Macros
import reactivemongo.api.bson.exceptions.{
  HandlerException,
  TypeDoesNotMatchException
}

import org.specs2.execute._
import org.specs2.matcher.MatchResult
import org.specs2.matcher.TypecheckMatchers._

import com.github.ghik.silencer.silent

import Typecheck._

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
    }

    // TODO

    "support generic optional value" >> {
      val doc1 = BSONDocument("v" -> 1)
      val none = OptionalGeneric[String](1, None)

      val doc2 = BSONDocument("v" -> 2, "opt" -> "foo")
      val some = OptionalGeneric(2, Some("foo"))

      // TODO

      "for writer" in {
        val writer = Macros.writer[OptionalGeneric[String]]

        writer.writeTry(none) must beSuccessfulTry(doc1) and {
          writer.writeTry(some) must beSuccessfulTry(doc2)
        }
      }

      // TODO
    }
  }
end MacroSpec
