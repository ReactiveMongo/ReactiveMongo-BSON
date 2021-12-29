import reactivemongo.api.bson.{
  BSONDateTime,
  BSONDocument,
  BSONLong,
  BSONValue,
  DocumentClass
}
import reactivemongo.api.bson.TestUtils.typecheck

import org.specs2.matcher.TypecheckMatchers._

abstract class AbstractClassA

final class DocumentClassSpec
    extends org.specs2.mutable.Specification
    with DocumentClassExtraSpec {

  "Document class".title

  import MacroTest._

  "Evidence" should {
    "be proved for" >> {
      "BSONDocument" in {
        implicitly[DocumentClass[BSONDocument]] must not(beNull)
      }

      "class Union.UT" in {
        implicitly[DocumentClass[Union.UT]] must not(beNull)
      }
    }

    "not be proved for" >> {
      "class Person" in {
        implicitly[DocumentClass[Person]] must not(beNull)
      }

      "class AbstractClassA" in {
        typecheck("implicitly[DocumentClass[AbstractClassA]]") must failWith(
          "no.*\\ implicit\\ .*DocumentClass\\[.*AbstractClassA\\].*"
        )
      }

      "value class FooVal" in {
        typecheck("implicitly[DocumentClass[FooVal]]") must failWith(
          "no.*\\ implicit\\ .*DocumentClass\\[.*FooVal\\].*"
        )
      }

      "Int" in {
        typecheck("implicitly[DocumentClass[Int]]") must failWith(
          "no.*\\ implicit\\ .*DocumentClass\\[Int\\].*"
        )
      }

      "BSONValue" in {
        typecheck("implicitly[DocumentClass[BSONValue]]") must failWith(
          "no.*\\ implicit\\ .*DocumentClass\\[.*BSONValue\\].*"
        )
      }

      "BSONDateTime" in {
        typecheck("implicitly[DocumentClass[BSONDateTime]]") must failWith(
          "no.*\\ implicit\\ .*DocumentClass\\[.*BSONDateTime\\].*"
        )
      }

      "BSONLong" in {
        typecheck("implicitly[DocumentClass[BSONLong]]") must failWith(
          "no.*\\ implicit\\ .*DocumentClass\\[.*BSONLong\\].*"
        )
      }
    }
  }
}
