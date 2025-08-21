package reactivemongo.api.bson

import scala.deriving.Mirror

import reactivemongo.api.bson.TestUtils.typecheck

import org.specs2.matcher.MatchResult
import org.specs2.matcher.TypecheckMatchers.*

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
            "(val age,List(Apply(TypeApply(Select(New(Select(Select(Ident(Macros),Annotations),DefaultValue)),<init>),List(TypeTree[TypeRef(ThisType(TypeRef(NoPrefix,module class scala)),class Int)])),List(Literal(Constant(18))))),TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class <root>)),object scala),class Int))"
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
        ) must_=== "reactivemongo.api.bson.Foo/Foo(1,2)"
      }

      "from generic Bar" in {
        testWithTuple(
          Bar[Double]("bar1", None, Seq(1.2D, 34.5D))
        ) must_=== "reactivemongo.api.bson.Bar[scala.Double]/Bar(bar1,None,List(1.2, 34.5))" and {
          testWithTuple(
            Bar[Float]("bar2", Some(1.23F), Seq(0D))
          ) must_=== "reactivemongo.api.bson.Bar[scala.Float]/Bar(bar2,Some(1.23),List(0.0))"
        }
      }

      "from BigFat" in {
        testWithTuple[BigFat](BigFat.example).mustEqual(
          "reactivemongo.api.bson.BigFat/BigFat(1,2.0,3.0,d,List(1, 2, 3),6,7.0,8.0,i,List(4, 5),10,11.0,12.0,n,List(6, 7),13,14.0,15.0,s,List(8),16.0,v,List(9, 10, 11),12,List(13, 14),15.0)"
        )
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
          ) must_=== "scala.Tuple2[scala.Predef.String, scala.Int]/(name,2)"
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

      "when BigFat" in {
        testWithFields(BigFat.example).mustEqual(
          "e=List(1, 2, 3),n=n,t=List(8),a=1,m=12.0,i=i,v=v,p=13,r=15.0,w=List(9, 10, 11),k=10,s=s,x=12,j=List(4, 5),y=List(13, 14),u=16.0,f=6,q=14.0,b=2.0,g=7.0,l=11.0,c=3.0,h=8.0,o=List(6, 7),z=15.0,d=d"
        )
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
    }
  }
end QuotesSpec

case class Foo(bar: String, lorem: Int)

case class Bar[T](name: String, opt: Option[T], scores: Seq[Double])

case class BigFat(
    a: Int,
    b: Double,
    c: Float,
    d: String,
    e: Seq[Int],
    f: Int,
    g: Double,
    h: Float,
    i: String,
    j: Seq[Int],
    k: Int,
    l: Double,
    m: Float,
    n: String,
    o: Seq[Int],
    p: Int,
    q: Double,
    r: Float,
    s: String,
    t: Seq[Int],
    u: Float,
    v: String,
    w: Seq[Int],
    x: Int,
    y: Seq[Int],
    z: Double)

object BigFat {

  def example = BigFat(
    a = 1,
    b = 2D,
    c = 3F,
    d = "d",
    e = Seq(1, 2, 3),
    f = 6,
    g = 7D,
    h = 8F,
    i = "i",
    j = Seq(4, 5),
    k = 10,
    l = 11D,
    m = 12F,
    n = "n",
    o = Seq(6, 7),
    p = 13,
    q = 14D,
    r = 15F,
    s = "s",
    t = Seq(8),
    u = 16F,
    v = "v",
    w = Seq(9, 10, 11),
    x = 12,
    y = Seq(13, 14),
    z = 15D
  )
}

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
