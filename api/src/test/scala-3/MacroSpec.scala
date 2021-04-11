import scala.util.{ Failure, Success, Try }

import reactivemongo.api.bson.{ BSON, BSONDecimal, BSONDocument, BSONDocumentHandler, BSONDocumentReader, BSONDocumentWriter, BSONHandler, BSONInteger, BSONNull, BSONReader, BSONString, BSONValue, BSONWriter }
import reactivemongo.api.bson.exceptions.{
  HandlerException,
  TypeDoesNotMatchException
}

import org.specs2.execute._
import org.specs2.matcher.MatchResult
import org.specs2.matcher.TypecheckMatchers._

import com.github.ghik.silencer.silent

import Typecheck._

// TODO: Common MacroSpec
class MacroSpec extends org.specs2.mutable.Specification:
  "Macros".title

  "Utility macros" should {
    "provide 'migrationRequired' compilation error" in {
      import reactivemongo.api.bson.migrationRequired

      reactivemongo.api.bson.TestUtils.typecheck(
        """migrationRequired[String]("Foo"): String""") must failWith(
          "Migration\\ required:\\ Foo")
    }
  }
