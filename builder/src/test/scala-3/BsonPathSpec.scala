package reactivemongo.api.bson.builder

import org.specs2.matcher.TypecheckMatchers.*

import TestUtils.typecheck

final class BsonPathSpec extends org.specs2.mutable.Specification {
  "BSON path".title

  "Field" should {
    import BsonPath.Exists

    lazy val ev1 = Exists[Foo, String]("id")

    "exist" in {
      ev1 must not(beNull) and {
        BsonPath.from[Foo].exists("id") must not(beNull)
      }
    }

    {
      s"not exist given type with symbol 'id'" in {
        typecheck("""Exists[Foo, Int]("id")""") must failWith(
          "No field.*of type Int"
        )
      }
    }

    "be aliased" in {
      given alias: Exists[Foo, "_id", String] = ev1.unsafeAlias("_id")

      alias must not(beNull) and {
        implicitly[Exists[Foo, "_id", String]] must not(beNull)
      } and {
        typecheck("""implicitly[Exists[Foo, "_id", Int]]""") must failWith(
          "No field.* of type Int"
        )
      }
    }
  }

  "Lookup" should {
    import BsonPath.{ Exists, Lookup, <:!< }

    "resolve direct field" in {
      typecheck("""
        val lookup = Lookup.deriveHNil(
          using Exists[Foo, String]("id"),
          implicitly[<:!<[String, Option[_]]]
        )

        val _: Int = lookup
      """) must failWith(
        """Lookup.*\[.+Foo,.+id.+String.+ Int.*"""
      )
    }

    "resolve optional field" in {
      typecheck("""
        val lookup = Lookup.deriveHNilOption[Foo, "status"]
        val _: Int = lookup
      """) must failWith(
        """Lookup.*Foo.*status.*Status.*Required.*Int"""
      )
    }
  }
}
