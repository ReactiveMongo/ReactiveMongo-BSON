package reactivemongo.api.bson

final class QuotesSpec extends org.specs2.mutable.Specification:
  "Quotes".title

  import TestMacros._

  "Product" should {
    "be inspected for elements" >> {
      "when Foo" in {
        testProductElements[Foo] must_=== List(
          "(val bar,TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class scala)),object Predef),type String))",
          "(val lorem,TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class <root>)),object scala),class Int))"
        )
      }

      "when generic Bar" in {
        testProductElements[Bar[Int]] must_=== List(
          "(val name,TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class scala)),object Predef),type String))",
          "(val opt,AppliedType(TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class <root>)),object scala),class Option),List(TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class <root>)),object scala),class Int))))",
          "(val scores,AppliedType(TypeRef(ThisType(TypeRef(NoPrefix,module class immutable)),trait Seq),List(TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class <root>)),object scala),class Double))))"
        )
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
end QuotesSpec

case class Foo(bar: String, lorem: Int)

case class Bar[T](name: String, opt: Option[T], scores: Seq[Double])
