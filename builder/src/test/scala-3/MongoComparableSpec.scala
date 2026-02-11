package reactivemongo.api.bson.builder

import reactivemongo.api.bson.{ BSONArray, BSONDouble, BSONInteger, BSONString }

import org.specs2.matcher.TypecheckMatchers.*

import TestUtils.typecheck

final class MongoComparableSpec extends org.specs2.mutable.Specification {
  "Mongo comparable".title

  import TestUtils.symbol

  val unknownSym = "unknown"

  "Comparison evidence" should {
    val resolve = TestUtils.resolver[Foo]

    "be resolved" >> {
      "strictly with success" in {
        val strict = MongoComparable.strictly(
          using BsonPath.Exists[Foo, Int]("counter")
        )

        strict must not(beNull) and {
          resolve("counter", 1) must_=== strict
        }
      }

      "with failure with missing field" in {
        typecheck(
          "TestUtils.resolver[Foo].apply(unknownSym, 2)"
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
          resolve("tags", "singleTag") must_=== it
        }
      }

      "for Set collection with success" in {
        val setTest = MongoComparable.iterable[Foo, "categories", String]

        setTest must not(beNull) and {
          resolve("categories", "category1") must_=== setTest
        }
      }

      "for numeric types" >> {
        "with Int field" in {
          val intTest = MongoComparable.strictly(
            using BsonPath.Exists[Foo, Int]("counter")
          )

          intTest must not(beNull) and {
            resolve("counter", 42) must_=== intTest
          }
        }

        "with Long field" in {
          val longTest = MongoComparable.strictly(
            using BsonPath.Exists[Foo, Long]("quantity")
          )

          longTest must not(beNull) and {
            resolve("quantity", 999L) must_=== longTest
          }
        }

        "with Double field" in {
          val doubleTest = MongoComparable.strictly(
            using BsonPath.Exists[Foo, Double]("score")
          )

          doubleTest must not(beNull) and {
            resolve("score", 3.14) must_=== doubleTest
          }
        }
      }

      "for Option type" >> {
        val opt = MongoComparable.strictly(
          using BsonPath.Exists[Foo, Option[Status]]("status")
        )

        "with success" in {
          opt must not(beNull) and {
            resolve(
              "status",
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
            resolve("status", Option.empty[Status]) must_=== opt
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
            using BsonPath.Exists[Foo, Option[Seq[String]]]("extraTags")
          )

          optSeq must not(beNull) and {
            resolve(
              "extraTags",
              Option(Seq("tag1", "tag2"))
            ) must_=== optSeq
          }
        }
      }

      "for numeric cross-type comparison" >> {
        "with Int field and Long value" in {
          val intToLong = MongoComparable.numeric[Foo, "counter", Int]

          intToLong must not(beNull) and {
            resolve("counter", 100L) must_=== intToLong
          }
        }

        "with Long field and Int value" in {
          val longToInt = MongoComparable.numeric[Foo, "quantity", Long]

          longToInt must not(beNull) and {
            resolve("quantity", 42) must_=== longToInt
          }
        }

        "with Double field and Int value" in {
          val doubleToInt = MongoComparable.numeric[Foo, "score", Double]

          doubleToInt must not(beNull) and {
            resolve("score", 10) must_=== doubleToInt
          }
        }

        "with Int field and Double value" in {
          val intToDouble = MongoComparable.numeric[Foo, "counter", Int]

          intToDouble must not(beNull) and {
            resolve("counter", 3.5) must_=== intToDouble
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

    "for Expr type" >> {
      import reactivemongo.api.bson.builder.Expr.implicits.mongoComparable

      "with String field and Expr value" in {
        val expr = Expr.unsafe[Foo, String](BSONString("test"))

        resolve("id", expr) must not(beNull)
      }

      "with Int field and Expr value" in {
        val expr = Expr.unsafe[Foo, Int](BSONInteger(42))

        resolve("counter", expr) must not(beNull)
      }

      "with Double field and Expr value" in {
        val expr = Expr.unsafe[Foo, Double](BSONDouble(3.14D))

        resolve("score", expr) must not(beNull)
      }

      "with Seq field and Expr value" in {
        val expr = Expr.unsafe[Foo, Seq[String]](
          BSONArray(
            BSONString("tag1"),
            BSONString("tag2")
          )
        )

        resolve("tags", expr) must not(beNull)
      }

      "with failure for missing field" in {
        typecheck("""{
          val expr = Expr.unsafe[Foo, String](BSONString("test"))
          TestUtils.resolver[Foo].apply(unknownSym, expr)
        }""") must failWith(
          "No field.* comparable.*"
        )
      }

      "with failure for incompatible field type" in {
        typecheck("""{
          val expr = Expr.unsafe[Foo, String](BSONString("test"))
          TestUtils.resolver[Foo].apply("counter", expr)
        }""") must failWith(
          "No field.* comparable.*"
        )
      }
    }
  }
}
