package reactivemongo.api.bson.builder

import org.specs2.matcher.TypecheckMatchers._

import _root_.shapeless.{ <:!<, Witness }

import TestUtils.typecheck

final class BsonPathSpec extends org.specs2.mutable.Specification {
  "BSON path".title

  "Field" should {
    import BsonPath.Exists

    lazy val ev1 = Exists[Foo, String](Symbol("id"))

    "exist" in {
      ev1 must not(beNull) and {
        BsonPath.from[Foo].exists(Symbol("id")) must not(beNull)
      }
    }

    {
      val sym: Witness.Lt[Symbol] = Symbol("id")

      s"not exist given type with symbol ${sym.value.name}" in {
        typecheck("Exists[Foo, Int](sym)") must failWith(
          "No field.*of type Int"
        )
      }
    }

    "be aliased" in {
      val aliasSym = Symbol("_id")
      implicit val alias = ev1.unsafeAlias(aliasSym)

      alias must not(beNull) and {
        implicitly[Exists[Foo, aliasSym.type, String]] must not(beNull)
      } and {
        typecheck("implicitly[Exists[Foo, aliasSym.type, Int]]") must failWith(
          "No field.* of type Int"
        )
      }
    }
  }

  "Lookup" should {
    import BsonPath.{ Exists, Lookup }

    "resolve direct field" in {
      val lookup = Lookup.deriveHNil(
        Exists[Foo, String](Symbol("id")),
        implicitly[<:!<[String, Option[_]]]
      )

      lookup must not(beNull) and {
        typecheck("val _: Int = lookup") must failWith(
          """Lookup\[.+Foo,.+id.+,String\]"""
        )
      }
    }

    "resolve optional field" in {
      val lookup =
        Lookup.deriveHNilOption(Exists[Foo, Option[Status]](Symbol("status")))

      lookup must not(beNull) and {
        typecheck("val _: Int = lookup") must failWith(
          """Lookup\[.+Foo,.+status.+,.+Status\]"""
        )
      }
    }
  }
}
