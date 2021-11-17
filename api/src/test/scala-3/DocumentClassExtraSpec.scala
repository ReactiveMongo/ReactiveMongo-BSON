import reactivemongo.api.bson.DocumentClass

import reactivemongo.api.bson.TestUtils.typecheck

import org.specs2.matcher.TypecheckMatchers._

trait DocumentClassExtraSpec { self: DocumentClassSpec =>
  /* TODO
  import MacroTest._

  "Non-case class" should {
    "be supported Product conversion & ProductOf" in {
      summon[DocumentClass[Union.UB]] must not(beNull)
    }

    "not be supported" in {
      typecheck("DocumentClass.evidence[UnsupportedExtraClass]") must failWith(
        "Type\\ UnsupportedExtraClass\\ is\\ not\\ a\\ document\\ one"
      )
    }
  }

  "Union types" should {
    "be supported" >> {
      "for alias" in {
        type Alias1 = Person | Bar

        summon[DocumentClass[Alias1]] must not(beNull)
      }

      "directly" in {
        summon[DocumentClass[Person | Bar]] must not(beNull)
      }
    }

    "not be supported" >> {
      "when not applied on non-class types" in {
        typecheck(
          "type Alias2 = String | Int; DocumentClass.evidence[Alias2]"
        ) must failWith(
          ".*Type\\ .*String\\ \\|\\ .*Int\\ is\\ not\\ a\\ document.*"
        )
      }

      "when includes Int" in {
        typecheck("DocumentClass.evidence[Person | Int]") must failWith(
          ".*Type\\ .*Person\\ \\|\\ .*Int\\ is\\ not\\ a\\ document.*"
        )
      }
    }
  }
   */
}

final class UnsupportedExtraClass(val name: String, val age: Int)
