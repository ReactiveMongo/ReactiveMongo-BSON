package reactivemongo.api.bson.builder

import reactivemongo.api.bson.{ BSONArray, BSONDouble, BSONInteger, BSONString }

import org.specs2.matcher.TypecheckMatchers._

import TestUtils.typecheck

final class MongoComparableSpec extends org.specs2.mutable.Specification {
  "Mongo comparable".title

  val unknownSym = TestUtils.symbol("unknown")

  "Comparison evidence" should {
    "be resolved" >> {
      val resolve = TestUtils.resolver[Foo]

      "strictly with success" in {
        val strict = MongoComparable.strictly(
          BsonPath.Exists[Foo, Int](Symbol("counter"))
        )

        strict must not(beNull) and {
          resolve(Symbol("counter"), 1) must_=== strict
        }
      }

      "with failure with missing field" in {
        typecheck("""resolve(unknownSym, 2)""") must failWith(
          "No field.* comparable with type Int"
        )
      }

      "with failure with incompatible type" in {
        val sym = TestUtils.symbol("counter")

        typecheck("""resolve(sym, "foo")""") must failWith(
          "No field.* comparable.*"
        )
      }

      "for iterable with success" in {
        val it = MongoComparable.iterable(
          BsonPath.Exists[Foo, Seq[String]](Symbol("tags"))
        )

        it must not(beNull) and {
          resolve(Symbol("tags"), "singleTag") must_=== it
        }
      }

      "for Set collection with success" in {
        val setTest = MongoComparable.iterable(
          BsonPath.Exists[Foo, Set[String]](Symbol("categories"))
        )

        setTest must not(beNull) and {
          resolve(Symbol("categories"), "category1") must_=== setTest
        }
      }

      "for numeric types" >> {
        "with Int field" in {
          val intTest = MongoComparable.strictly(
            BsonPath.Exists[Foo, Int](Symbol("counter"))
          )

          intTest must not(beNull) and {
            resolve(Symbol("counter"), 42) must_=== intTest
          }
        }

        "with Long field" in {
          val longTest = MongoComparable.strictly(
            BsonPath.Exists[Foo, Long](Symbol("quantity"))
          )

          longTest must not(beNull) and {
            resolve(Symbol("quantity"), 999L) must_=== longTest
          }
        }

        "with Double field" in {
          val doubleTest = MongoComparable.strictly(
            BsonPath.Exists[Foo, Double](Symbol("score"))
          )

          doubleTest must not(beNull) and {
            resolve(Symbol("score"), 3.14) must_=== doubleTest
          }
        }
      }

      "for Option type" >> {
        val opt = MongoComparable.strictly(
          BsonPath.Exists[Foo, Option[Status]](Symbol("status"))
        )

        "with success" in {
          opt must not(beNull) and {
            resolve(
              Symbol("status"),
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
            resolve(Symbol("status"), Option.empty[Status]) must_=== opt
          }
        }

        "with failure when missing field" in {
          typecheck("""resolve(unknownSym, Option("value"))""") must failWith(
            "No field.* comparable.*"
          )
        }

        "with failure on incompatible inner type" in {
          val sym = TestUtils.symbol("status")

          typecheck("""resolve(sym, "notAnOption")""") must failWith(
            "No field.* comparable.*"
          )
        }

        "with iterable with success" in {
          val optSeq = MongoComparable.strictly(
            BsonPath.Exists[Foo, Option[Seq[String]]](Symbol("extraTags"))
          )

          optSeq must not(beNull) and {
            resolve(
              Symbol("extraTags"),
              Option(Seq("tag1", "tag2"))
            ) must_=== optSeq
          }
        }
      }

      "for numeric cross-type comparison" >> {
        "with Int field and Long value" in {
          val intToLong = MongoComparable.numeric(
            BsonPath.Exists[Foo, Int](Symbol("counter")),
            implicitly[Numeric[Long]],
            implicitly[Numeric[Int]]
          )

          intToLong must not(beNull) and {
            resolve(Symbol("counter"), 100L) must_=== intToLong
          }
        }

        "with Long field and Int value" in {
          val longToInt = MongoComparable.numeric(
            BsonPath.Exists[Foo, Long](Symbol("quantity")),
            implicitly[Numeric[Int]],
            implicitly[Numeric[Long]]
          )

          longToInt must not(beNull) and {
            resolve(Symbol("quantity"), 42) must_=== longToInt
          }
        }

        "with Double field and Int value" in {
          val doubleToInt = MongoComparable.numeric(
            BsonPath.Exists[Foo, Double](Symbol("score")),
            implicitly[Numeric[Int]],
            implicitly[Numeric[Double]]
          )

          doubleToInt must not(beNull) and {
            resolve(Symbol("score"), 10) must_=== doubleToInt
          }
        }

        "with Int field and Double value" in {
          val intToDouble = MongoComparable.numeric(
            BsonPath.Exists[Foo, Int](Symbol("counter")),
            implicitly[Numeric[Double]],
            implicitly[Numeric[Int]]
          )

          intToDouble must not(beNull) and {
            resolve(Symbol("counter"), 3.5) must_=== intToDouble
          }
        }

        "with failure for non-numeric field" in {
          val sym = TestUtils.symbol("id")

          typecheck("""resolve(sym, 123)""") must failWith(
            "No field.* comparable.*"
          )
        }
      }
    }

    "for Expr type" >> {
      import Expr.implicits.mongoComparable

      val resolve = TestUtils.resolver[Foo]

      "with String field and Expr value" in {
        val expr = Expr.unsafe[Foo, String](BSONString("test"))

        resolve(Symbol("id"), expr) must not(beNull)
      }

      "with Int field and Expr value" in {
        val expr = Expr.unsafe[Foo, Int](BSONInteger(42))

        resolve(Symbol("counter"), expr) must not(beNull)
      }

      "with Double field and Expr value" in {
        val expr = Expr.unsafe[Foo, Double](BSONDouble(3.14))

        resolve(Symbol("score"), expr) must not(beNull)
      }

      "with Seq field and Expr value" in {
        val expr = Expr.unsafe[Foo, Seq[String]](
          BSONArray(
            BSONString("tag1"),
            BSONString("tag2")
          )
        )

        resolve(Symbol("tags"), expr) must not(beNull)
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
        val sym = TestUtils.symbol("counter")

        typecheck("""{
          val expr = Expr.unsafe[Foo, String](BSONString("test"))
          TestUtils.resolver[Foo].apply(sym, expr)
        }""") must failWith(
          "No field.* comparable.*"
        )
      }
    }
  }
}
