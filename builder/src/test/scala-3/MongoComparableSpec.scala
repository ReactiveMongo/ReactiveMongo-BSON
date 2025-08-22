package reactivemongo.api.bson.builder

import org.specs2.matcher.TypecheckMatchers.*

import TestUtils.typecheck

final class MongoComparableSpec extends org.specs2.mutable.Specification {
  "Mongo comparable".title

  import TestUtils.symbol

  val unknownSym = symbol("unknown")

  "Comparison evidence" should {
    "be resolved" >> {
      val resolve = TestUtils.resolver[Foo]

      "strictly with success" in {
        val strict = MongoComparable.strictly(
          using BsonPath.Exists[Foo, Int](symbol("counter"))
        )

        strict must not(beNull) and {
          resolve(symbol("counter"), 1) must_=== strict
        }
      }

      "with failure with missing field" in {
        typecheck(
          """TestUtils.resolver[Foo].apply(unknownSym, 2)"""
        ) must failWith(
          "No field.* comparable with type Int"
        )
      }

      "with failure with incompatible type" in {
        typecheck(
          """TestUtils.resolver[Foo].apply("counter", "foo")"""
        ) must failWith(
          "No field.* comparable.*"
        )
      }

      "for iterable with success" in {
        val it = MongoComparable.iterable[Foo, "tags", String]

        it must not(beNull) and {
          resolve(symbol("tags"), "singleTag") must_=== it
        }
      }

      "for Set collection with success" in {
        val setTest = MongoComparable.iterable[Foo, "categories", String]

        setTest must not(beNull) and {
          resolve(symbol("categories"), "category1") must_=== setTest
        }
      }

      "for numeric types" >> {
        "with Int field" in {
          val intTest = MongoComparable.strictly(
            using BsonPath.Exists[Foo, Int](symbol("counter"))
          )

          intTest must not(beNull) and {
            resolve(symbol("counter"), 42) must_=== intTest
          }
        }

        "with Long field" in {
          val longTest = MongoComparable.strictly(
            using BsonPath.Exists[Foo, Long](symbol("quantity"))
          )

          longTest must not(beNull) and {
            resolve(symbol("quantity"), 999L) must_=== longTest
          }
        }

        "with Double field" in {
          val doubleTest = MongoComparable.strictly(
            using BsonPath.Exists[Foo, Double](symbol("score"))
          )

          doubleTest must not(beNull) and {
            resolve(symbol("score"), 3.14) must_=== doubleTest
          }
        }
      }

      "for Option type" >> {
        val opt = MongoComparable.strictly(
          using BsonPath.Exists[Foo, Option[Status]](symbol("status"))
        )

        "with success" in {
          opt must not(beNull) and {
            resolve(
              symbol("status"),
              Option(
                Status(
                  "active",
                  java.time.OffsetDateTime.now(),
                  Details("", "")
                )
              )
            ) must_=== opt
          }
        }

        "with None value" in {
          opt must not(beNull) and {
            resolve(symbol("status"), Option.empty[Status]) must_=== opt
          }
        }

        "with failure when missing field" in {
          typecheck(
            """TestUtils.resolver[Foo].apply(unknownSym, Option("value"))"""
          ) must failWith(
            "No field.* comparable.*"
          )
        }

        "with failure on incompatible inner type" in {

          typecheck(
            """TestUtils.resolver[Foo].apply("status", "notAnOption")"""
          ) must failWith(
            "No field.* comparable.*"
          )
        }

        "with iterable with success" in {
          val optSeq = MongoComparable.strictly(
            using BsonPath.Exists[Foo, Option[Seq[String]]](symbol("extraTags"))
          )

          optSeq must not(beNull) and {
            resolve(
              symbol("extraTags"),
              Option(Seq("tag1", "tag2"))
            ) must_=== optSeq
          }
        }
      }

      "for numeric cross-type comparison" >> {
        "with Int field and Long value" in {
          val intToLong = MongoComparable.numeric[Foo, "counter", Int]

          intToLong must not(beNull) and {
            resolve(symbol("counter"), 100L) must_=== intToLong
          }
        }

        "with Long field and Int value" in {
          val longToInt = MongoComparable.numeric[Foo, "quantity", Long]

          longToInt must not(beNull) and {
            resolve(symbol("quantity"), 42) must_=== longToInt
          }
        }

        "with Double field and Int value" in {
          val doubleToInt = MongoComparable.numeric[Foo, "score", Double]

          doubleToInt must not(beNull) and {
            resolve(symbol("score"), 10) must_=== doubleToInt
          }
        }

        "with Int field and Double value" in {
          val intToDouble = MongoComparable.numeric[Foo, "counter", Int]

          intToDouble must not(beNull) and {
            resolve(symbol("counter"), 3.5) must_=== intToDouble
          }
        }

        "with failure for non-numeric field" in {
          typecheck(
            """TestUtils.resolver[Foo].apply("id", 123)"""
          ) must failWith(
            "No field.* comparable.*"
          )
        }
      }
    }
  }
}
