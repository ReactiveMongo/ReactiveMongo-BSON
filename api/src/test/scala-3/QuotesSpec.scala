package reactivemongo.api.bson

import scala.deriving.Mirror

import reactivemongo.api.bson.TestUtils.typecheck

import org.specs2.matcher.MatchResult
import org.specs2.matcher.TypecheckMatchers._

final class QuotesSpec extends org.specs2.mutable.Specification:
  "Quotes".title

  import TestMacros._

  "Product" should {
    "be inspected for elements" >> {
      "when Foo" in {
        testProductElements[Foo] must_=== List(
          "(val bar,List(),TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class scala)),object Predef),type String))",
          "(val lorem,List(),TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class <root>)),object scala),class Int))"
        )
      }

      "when generic Bar" in {
        testProductElements[Bar[Int]] must_=== List(
          "(val name,List(),TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class scala)),object Predef),type String))",
          "(val opt,List(),AppliedType(TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class <root>)),object scala),class Option),List(TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class <root>)),object scala),class Int))))",
          "(val scores,List(),AppliedType(TypeRef(ThisType(TypeRef(NoPrefix,module class immutable)),trait Seq),List(TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class <root>)),object scala),class Double))))"
        )
      }

      "of non-case class" >> {
        "when there is no ProductOf" in {
          testProductElements[TestUnion.UC] must beEmpty
        }

        "if it's defined a ill-typed ProductOf" in {
          // Bad refinement type, so element labels/types cannot be resolved
          given pof: Mirror.ProductOf[TestUnion.UC] = TestUnion.ProductOfUC

          testProductElements[TestUnion.UC] must beEmpty
        }

        "if it's defined a well-typed ProductOf" >> {
          val expected = List(
            "(val name,List(),TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class scala)),object Predef),type String))",
            "(val age,List(Apply(TypeApply(Select(New(Select(Select(Ident(Macros),Annotations),DefaultValue)),<init>),List(TypeTree[TypeVar(TypeParamRef(T) -> TypeRef(ThisType(TypeRef(NoPrefix,module class scala)),class Int))])),List(Literal(Constant(18))))),TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class <root>)),object scala),class Int))"
          )

          "by import" in {
            import TestUnion.Implicits.productOfUC

            testProductElements[TestUnion.UC] must_=== expected
          }

          "by local val" in {
            implicit val pof = TestUnion.ProductOfUC

            testProductElements[TestUnion.UC] must_=== expected
          }
        }
      }
    }

    "be created" >> {
      "from Foo" in {
        testWithTuple(
          Foo("1", 2)
        ) must_=== "scala.Tuple2[scala.Predef.String, scala.Int]/Foo(1,2)"
      }

      "from generic Bar" in {
        testWithTuple(
          Bar[Double]("bar1", None, Seq(1.2D, 34.5D))
        ) must_=== "scala.Tuple3[scala.Predef.String, scala.Option[scala.Double], scala.collection.immutable.Seq[scala.Double]]/Bar(bar1,None,List(1.2, 34.5))" and {
          testWithTuple(
            Bar[Float]("bar2", Some(1.23F), Seq(0D))
          ) must_=== "scala.Tuple3[scala.Predef.String, scala.Option[scala.Float], scala.collection.immutable.Seq[scala.Double]]/Bar(bar2,Some(1.23),List(0.0))"
        }
      }

      "from non-case class" >> {
        "fail when there is no Conversion[T, _ <: Product]" in {
          typecheck(
            """testWithTuple(new TestUnion.UC("name", 2))"""
          ) must failWith(".*Conversion.*")
        }

        "fail when Conversion[T, _ <: Product] defined without ProductOf" in {
          implicit val conv = TestUnion.ucAsProduct

          testWithTuple(
            new TestUnion.UC("name", 2)
          ) must_=== "scala.Tuple$package.EmptyTuple/(name,2)"
        }

        "be successful when conversion is provided" in {
          import TestUnion.Implicits.productOfUC
          implicit val conv = TestUnion.ucAsProduct

          testWithTuple(
            new TestUnion.UC("name", 2)
          ) must_=== "scala.Tuple2[scala.Predef.String, scala.Int]/(name,2)"
        }
      }
    }

    "be transformed" >> {
      "when Foo" in {
        testWithFields(Foo("3", 4)) must_=== "bar=3,lorem=4"
      }

      "when generic Bar" in {
        testWithFields(
          Bar("bar3", Some("opt2"), Seq(3D, 4.5D))
        ) must_=== "name=bar3,opt=Some(opt2),scores=List(3.0, 4.5)"
      }
    }
  }

  "Direct known subtypes" should {
    "be resolved for sealed trait" in {
      testKnownSubtypes[TestUnion.UT] must_=== List(
        "reactivemongo.api.bson.TestUnion.UA",
        "reactivemongo.api.bson.TestUnion.UB",
        "reactivemongo.api.bson.TestUnion.UC",
        "reactivemongo.api.bson.TestUnion.UD",
        "reactivemongo.api.bson.TestUnion.UE" // through UTT sub-trait
      )
    } tag "wip"
  }
end QuotesSpec

case class Foo(bar: String, lorem: Int)

case class Bar[T](name: String, opt: Option[T], scores: Seq[Double])

object TestUnion:
  sealed trait UT
  case object UA extends UT
  case class UB(name: String) extends UT

  class UC(val name: String, @Macros.Annotations.DefaultValue(18) val age: Int)
      extends UT

  object UD extends UT

  sealed trait UTT extends UT
  case class UE() extends UTT

  object ProductOfUC extends Mirror.Product {
    type MirroredType = TestUnion.UC
    type MirroredElemTypes = Tuple2[String, Int]
    type MirroredMonoType = TestUnion.UC
    type MirroredLabel = "UC"
    type MirroredElemLabels = Tuple2["name", "age"]

    def fromProduct(p: Product): MirroredMonoType =
      new TestUnion.UC(
        p.productElement(0).asInstanceOf[String],
        p.productElement(1).asInstanceOf[Int]
      )
  }

  object Implicits:
    implicit val productOfUC: ProductOfUC.type = ProductOfUC
  end Implicits

  val ucAsProduct: Conversion[TestUnion.UC, Tuple2[String, Int]] =
    (uc: TestUnion.UC) => uc.name -> uc.age

end TestUnion
