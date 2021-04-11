import scala.util.{ Failure, Success, Try }

import reactivemongo.api.bson.{ BSON, BSONDateTime, BSONDecimal, BSONDocument, BSONDocumentHandler, BSONDocumentReader, BSONDocumentWriter, BSONHandler, BSONInteger, BSONLong, BSONNull, BSONReader, BSONString, BSONValue, BSONWriter }
import reactivemongo.api.bson.exceptions.{
  HandlerException,
  TypeDoesNotMatchException
}

import org.specs2.execute._
import org.specs2.matcher.MatchResult
import org.specs2.matcher.TypecheckMatchers._

import com.github.ghik.silencer.silent

import Typecheck._

case class Person(firstName: String, lastName: String)

object Union {
  abstract class UT
}

import reactivemongo.api.bson.TestUtils.typecheck

// TODO: Common MacroSpec
class MacroSpec extends org.specs2.mutable.Specification:
  "Macros".title

  "DocumentClass" should {
    import reactivemongo.api.bson.DocumentClass

    "be proved for case class Person" in {
      implicitly[DocumentClass[Person]] must not(beNull)
    }

    "be proved for sealed trait UT" in {
      implicitly[DocumentClass[Union.UT]] must not(beNull)
    }

    "be proved for BSONDocument" in {
      implicitly[DocumentClass[BSONDocument]] must not(beNull)
    }

    "not be proved for" >> {
      import scala.compiletime.testing.*

      "Int" in {
        typecheck("implicitly[DocumentClass[Int]]") must failWith(
          "no\\ implicit\\ .*DocumentClass\\[Int\\].*")
      }

      "BSONValue" in {
        typecheck("implicitly[DocumentClass[BSONValue]]") must failWith(
          "no\\ implicit\\ .*DocumentClass\\[.*BSONValue\\].*")
      }

      "BSONDateTime" in {
        typecheck("implicitly[DocumentClass[BSONDateTime]]") must failWith(
          "no\\ implicit\\ .*DocumentClass\\[.*BSONDateTime\\].*")
      }

      "BSONLong" in {
        typecheck("implicitly[DocumentClass[BSONLong]]") must failWith(
          "no\\ implicit\\ .*DocumentClass\\[.*BSONLong\\].*")
      }
    }
  }

  "Utility macros" should {
    "provide 'migrationRequired' compilation error" in {
      import reactivemongo.api.bson.migrationRequired

      typecheck(
        """migrationRequired[String]("Foo"): String""") must failWith(
          "Migration\\ required:\\ Foo")
    }
  }
