import scala.util.{ Failure, Success, Try }

import com.github.ghik.silencer.silent

import reactivemongo.api.bson.{
  BSON,
  BSONDecimal,
  BSONDocument,
  BSONDocumentHandler,
  BSONDocumentReader,
  BSONDocumentWriter,
  BSONHandler,
  BSONNull,
  BSONReader,
  BSONValue,
  BSONWriter,
  Macros
}
import reactivemongo.api.bson.exceptions.TypeDoesNotMatchException

import org.specs2.matcher.MatchResult

final class MacroSpec extends org.specs2.mutable.Specification {
  "Macros" title

  import MacroTest._

  "Formatter" should {
    "handle primitives" in {
      roundtrip(
        Primitives(1.2, "hai", true, 42, Long.MaxValue),
        Macros.handler[Primitives])
    }

    "support nesting" in {
      implicit val personFormat = Macros.handler[Person]
      val doc = Pet("woof", Person("john", "doe"))

      roundtrip(doc, Macros.handler[Pet])
    }

    "support option" in {
      val format = Macros.handler[Optional]
      val some = Optional("some", Some("value"))
      val none = Optional("none", None)

      roundtrip(some, format) and roundtrip(none, format)
    }

    "not support type mismatch for optional value" in {
      Macros.reader[Optional].readTry(
        BSONDocument(
          "name" -> "invalidValueType",
          "value" -> 4)) must beFailedTry[Optional] //("BSONInteger")
    }

    "support null for optional value" in {
      Macros.reader[Optional].readTry(
        BSONDocument(
          "name" -> "name",
          "value" -> BSONNull)).
        map(_.value) must beSuccessfulTry(Option.empty[String])
    }

    "write empty option as null" in {
      Macros.writer[OptionalAsNull].writeTry(
        OptionalAsNull("asNull", None)) must beSuccessfulTry(BSONDocument(
          "name" -> "asNull",
          "value" -> BSONNull))
    }

    "support seq" in {
      roundtrip(
        WordLover("john", Seq("hello", "world")),
        Macros.handler[WordLover])
    }

    "support single member case classes" in {
      roundtrip(
        Single(BigDecimal("12.345")),
        Macros.handler[Single])
    }

    "support single member options" in {
      val f = Macros.handler[OptionalSingle]

      roundtrip(OptionalSingle(Some("foo")), f) and {
        roundtrip(OptionalSingle(None), f)
      }
    }

    "support generic case class Foo" >> {
      implicit def singleHandler = Macros.handler[Single]

      "directly" in {
        roundtrip(
          Foo(Single(BigDecimal(123L)), "ipsum"),
          Macros.handler[Foo[Single]])
      }

      "from generic function" in {
        def handler[T](implicit w: BSONDocumentWriter[T], r: BSONDocumentReader[T]) = Macros.handler[Foo[T]]

        roundtrip(Foo(Single(BigDecimal(1.23D)), "ipsum"), handler[Single])
      }
    }

    "support generic case class GenSeq" in {
      implicit def singleHandler = new BSONWriter[Single] with BSONReader[Single] with BSONHandler[Single] {
        def writeTry(single: Single) = BSONDecimal.fromBigDecimal(single.value)

        def readTry(bson: BSONValue): Try[Single] = bson match {
          case dec: BSONDecimal =>
            BSONDecimal.toBigDecimal(dec).map(Single(_))

          case _ =>
            Failure(TypeDoesNotMatchException(
              "BSONDecimal", bson.getClass.getSimpleName))
        }
      }

      implicit def optionHandler[T](implicit h: BSONHandler[T]): BSONDocumentHandler[Option[T]] = new BSONDocumentReader[Option[T]] with BSONDocumentWriter[Option[T]] with BSONHandler[Option[T]] {

        def readDocument(doc: BSONDocument): Try[Option[T]] =
          doc.getAsUnflattenedTry[T](f"$$some")

        def writeTry(single: Option[T]): Try[BSONDocument] = single match {
          case Some(v) => h.writeTry(v).map { x =>
            BSONDocument(f"$$some" -> x)
          }

          case _ => Success(BSONDocument.empty)
        }
      }

      def genSeqHandler[T: BSONDocumentHandler]: BSONDocumentHandler[GenSeq[T]] = Macros.handler[GenSeq[T]]

      val seq = GenSeq(
        items = Seq(Option.empty[Single], Option(Single(BigDecimal(1)))),
        count = 1)

      roundtrip(seq, genSeqHandler[Option[Single]])
    }

    "handle overloaded apply correctly" in {
      val doc1 = OverloadedApply("hello")
      val doc2 = OverloadedApply(List("hello", "world"))

      @silent // TODO: Remove
      val f = Macros.handler[OverloadedApply]

      roundtrip(doc1, f)
      roundtrip(doc2, f)
    }

    "handle overloaded apply with different number of arguments correctly" in {
      val doc1 = OverloadedApply2("hello", 5)
      val doc2 = OverloadedApply2("hello")
      val f = Macros.handler[OverloadedApply2]

      roundtrip(doc1, f)
      roundtrip(doc2, f)
    }

    "handle overloaded apply with 0 number of arguments correctly" in {
      val doc1 = OverloadedApply3("hello", 5)
      val doc2 = OverloadedApply3()
      val f = Macros.handler[OverloadedApply3]

      roundtrip(doc1, f)
      roundtrip(doc2, f)
    }

    "case class and handler inside trait" in {
      val t = new NestModule {}
      roundtrip(t.Nested("it works"), t.format)
    }

    "case class inside trait with handler outside" in {
      val t = new NestModule {}
      import t._ //you need Nested in scope because t.Nested won't work
      val format = Macros.handler[Nested]
      roundtrip(Nested("it works"), format)
    }

    "respect compilation options" in {
      val format = Macros.handlerOpts[Person, Macros.Options.Verbose] //more stuff in compiler log
      roundtrip(Person("john", "doe"), format)
    }

    "not persist class name for case class" in {
      val person = Person("john", "doe")
      val format = Macros.handlerOpts[Person, Macros.Options.SaveSimpleName]
      val doc = format writeTry person

      doc.flatMap(_.getAsUnflattenedTry[String]("className")).
        aka("class") must beSuccessfulTry(Option.empty[String]) and {
          roundtrip(person, format)
        }
    }

    "handle union types (ADT)" in {
      import Union._
      import Macros.Options._
      val a = UA(1)
      val b = UB("hai")

      @silent // TODO: Remove
      val format = Macros.handlerOpts[UT, UnionType[UA \/ UB \/ UC \/ UD \/ UF.type] with AutomaticMaterialization]

      format.writeTry(a).map(_.getAsOpt[String]("className")).
        aka("class #1") must beSuccessfulTry(Some("MacroTest.Union.UA")) and {
          format.writeTry(b).map(_.getAsOpt[String]("className")).
            aka("class #2") must beSuccessfulTry(Some("MacroTest.Union.UB"))
        } and roundtrip(a, format) and roundtrip(b, format)
    }

    "handle union types (ADT) with simple names" in {
      import Union._
      import Macros.Options._
      val a = UA(1)
      val b = UB("hai")

      val _ = Macros.writerOpts[UT, SimpleUnionType[UA \/ UB \/ UC \/ UD] with AutomaticMaterialization]

      val format = Macros.handlerOpts[UT, SimpleUnionType[UA \/ UB \/ UC \/ UD] with AutomaticMaterialization]

      format.writeTry(a).map(
        _.getAsOpt[String]("className")) must beSuccessfulTry(Some("UA")) and {
          format.writeTry(b).map(_.getAsOpt[String]("className")) must beSuccessfulTry(Some("UB"))
        } and roundtrip(a, format) and roundtrip(b, format)
    }

    "handle recursive structure" in {
      import TreeModule._
      //handlers defined at tree module
      val tree: Tree = Node(Leaf("hi"), Node(Leaf("hello"), Leaf("world")))
      roundtrip(tree, Tree.bson)
    }

    "grab an implicit handler for type used in union" in {
      import TreeCustom._
      val tree: Tree = Node(Leaf("hi"), Node(Leaf("hello"), Leaf("world")))
      val serialized = BSON writeDocument tree
      val deserialized = serialized.flatMap(BSON.readDocument[Tree](_))

      deserialized must beSuccessfulTry(
        Node(Leaf("hai"), Node(Leaf("hai"), Leaf("hai"))))
    }

    "handle empty case classes" in {
      val empty = Empty()
      val format = Macros.handler[Empty]

      roundtrip(empty, format)
    }

    "do nothing with objects" in {
      val format = Macros.handler[EmptyObject.type]
      roundtrip(EmptyObject, format)
    }

    "handle ADTs with objects" in {
      import IntListModule._

      roundtripImp[IntList](Tail) and {
        roundtripImp[IntList](Cons(1, Cons(2, Cons(3, Tail))))
      }
    }

    "automate Union on sealed traits" in {
      import Macros.Options._
      import Union._

      @silent //TODO:("Cannot handle object MacroTest\\.Union\\.UE" /*expected*/ )
      implicit val format = Macros.handlerOpts[UT, AutomaticMaterialization]

      format.writeTry(UA(1)).flatMap(_.getAsTry[String]("className")).
        aka("class #1") must beSuccessfulTry("MacroTest.Union.UA") and {
          format.writeTry(UB("buzz")).flatMap(
            _.getAsUnflattenedTry[String]("className")).
            aka("class #2") must beSuccessfulTry(Some("MacroTest.Union.UB"))

        } and roundtripImp[UT](UA(17)) and roundtripImp[UT](UB("foo")) and {
          roundtripImp[UT](UC("bar")) and roundtripImp[UT](UD("baz"))
        } and roundtripImp[UT](UF)
    }

    "support automatic implementations search with nested traits" in {
      import Macros.Options._
      import InheritanceModule._
      implicit val format = Macros.handlerOpts[T, AutomaticMaterialization]

      format.writeTry(A()).flatMap(_.getAsTry[String]("className")).
        aka("class #1") must beSuccessfulTry(
          "MacroTest.InheritanceModule.A") and {
            format.writeOpt(B).flatMap(_.getAsOpt[String]("className")).
              aka("class #2") must beSome("MacroTest.InheritanceModule.B")

          } and {
            roundtripImp[T](A()) and roundtripImp[T](B) and roundtripImp[T](C())
          }
    }

    "automate Union on sealed traits with simple name" in {
      import Macros.Options._
      import Union._

      @silent //TODO:("Cannot handle object MacroTest\\.Union\\.UE" /*expected*/ )
      implicit val format = Macros.handlerOpts[UT, SaveSimpleName with AutomaticMaterialization]

      format.writeTry(UA(1)).flatMap(
        _.getAsTry[String]("className")) must beSuccessfulTry("UA") and {
          format.writeOpt(UB("buzz")).flatMap(
            _.getAsOpt[String]("className")) must beSome("UB")
        } and {
          roundtripImp[UT](UA(17)) and roundtripImp[UT](UB("foo")) and {
            roundtripImp[UT](UC("bar")) and roundtripImp[UT](UD("baz"))
          }
        }
    }

    "support automatic implementations search with nested traits with simple name" in {
      import Macros.Options._
      import InheritanceModule._

      implicit val format = Macros.handlerOpts[T, SaveSimpleName]

      format.writeTry(A()).flatMap(
        _.getAsTry[String]("className")) must beSuccessfulTry("A") and {
          format.writeTry(B).flatMap(
            _.getAsTry[String]("className")) must beSuccessfulTry("B")

        } and {
          roundtripImp[T](A()) and roundtripImp[T](B) and roundtripImp[T](C())
        }
    }

    "support overriding keys with annotations" in {
      implicit val format = Macros.handler[RenamedId]
      val doc = RenamedId(value = "some value")
      val serialized = BSONDocument("_id" -> doc.myID, "value" -> doc.value)

      format.writeTry(doc) must beSuccessfulTry(serialized) and {
        format.readTry(serialized) must beSuccessfulTry(doc)
      }
    }

    "skip ignored fields" >> {
      "with Pair type" in {
        val pairHandler = Macros.handler[Pair]
        val doc = pairHandler.writeTry(Pair(left = "left", right = "right"))

        doc must beSuccessfulTry(BSONDocument("right" -> "right"))
      }

      "along with Key annotation" in {
        // TODO: Macros.reader or handler (manage Ignore with reader?)
        implicit val handler: BSONDocumentWriter[IgnoredAndKey] =
          Macros.writer[IgnoredAndKey]

        val doc = handler.writeTry(IgnoredAndKey(Person("john", "doe"), "foo"))

        doc must beSuccessfulTry(BSONDocument("second" -> "foo"))
      }
    }

    "be generated for class class with self reference" in {
      val h = Macros.handler[Bar]
      val bar1 = Bar("bar1", None)
      val doc1 = BSONDocument("name" -> "bar1")

      h.readTry(doc1) must beSuccessfulTry(bar1) and {
        h.readTry(BSONDocument("name" -> "bar2", "next" -> doc1)).
          aka("bar2") must beSuccessfulTry(Bar("bar2", Some(bar1)))

      } and (h.writeTry(bar1) must beSuccessfulTry(doc1)) and {
        h.writeTry(Bar("bar2", Some(bar1))) must beSuccessfulTry(BSONDocument(
          "name" -> "bar2", "next" -> doc1))
      }
    }

    "support @Flatten annotation" in {
      shapeless.test.illTyped("Macros.handler[InvalidRecursive]")
      shapeless.test.illTyped("Macros.handler[InvalidNonDoc]")

      roundtrip(
        LabelledRange("range1", Range(start = 2, end = 5)),
        Macros.handler[LabelledRange])
    }

    "handle case class with implicits" >> {
      val doc1 = BSONDocument("pos" -> 2, "text" -> "str")
      val doc2 = BSONDocument("ident" -> "id", "value" -> 23.456D)
      val fixture1 = WithImplicit1(2, "str")
      val fixture2 = WithImplicit2("id", 23.456D)

      def readSpec1(r: BSONDocumentReader[WithImplicit1]) =
        r.readTry(doc1) must beSuccessfulTry(fixture1)

      def writeSpec2(w: BSONDocumentWriter[WithImplicit2[Double]]) =
        w.writeTry(fixture2) must beSuccessfulTry(doc2)

      "to generate reader" in readSpec1(Macros.reader[WithImplicit1])

      "to generate writer with type parameters" in writeSpec2(
        Macros.writer[WithImplicit2[Double]])

      "to generate handler" in {
        val f1 = Macros.handler[WithImplicit1]
        val f2 = Macros.handler[WithImplicit2[Double]]

        readSpec1(f1) and {
          f1.writeTry(fixture1) must beSuccessfulTry(doc1)
        } and {
          writeSpec2(f2)
        } and {
          f2.readTry(doc2) must beSuccessfulTry(fixture2)
        }
      }
    }
  }

  "Reader" should {
    "throw meaningful exception if required field is missing" in {
      val personDoc = BSONDocument("firstName" -> "joe")

      Macros.reader[Person].readTry(personDoc) must beFailedTry[Person].
        like { case e => e.getMessage must contain("lastName") }
    }

    "throw meaningful exception if field has another type" in {
      val primitivesDoc = BSONDocument(
        "dbl" -> 2D, "str" -> "str", "bl" -> true, "int" -> 2D, "long" -> 2L)

      Macros.reader[Primitives].readTry(primitivesDoc).
        aka("read") must beFailedTry[Primitives].like {
          case e =>
            e.getMessage must contain("int: BSONDouble != BSONInteger")
        }
    }

    "be generated for a generic case class" in {
      implicit def singleReader = Macros.reader[Single]
      val r = Macros.reader[Foo[Single]]
      val big = BigDecimal(1.23D)

      r.readTry(BSONDocument(
        "bar" -> BSONDocument("value" -> big),
        "lorem" -> "ipsum")) must beSuccessfulTry(Foo(Single(big), "ipsum"))
    }

    "be generated for class class with self reference" in {
      val r = Macros.reader[Bar]
      val bar1 = Bar("bar1", None)
      val doc1 = BSONDocument("name" -> "bar1")

      r.readTry(doc1) must beSuccessfulTry(bar1) and {
        r.readTry(BSONDocument("name" -> "bar2", "next" -> doc1)).
          aka("bar2") must beSuccessfulTry(Bar("bar2", Some(bar1)))
      }
    }

    "be generated with @Flatten annotation" in {
      shapeless.test.illTyped("Macros.reader[InvalidRecursive]")
      shapeless.test.illTyped("Macros.reader[InvalidNonDoc]")

      val r = Macros.reader[LabelledRange]
      val doc = BSONDocument("name" -> "range1", "start" -> 2, "end" -> 5)
      val lr = LabelledRange("range1", Range(start = 2, end = 5))

      r.readTry(doc) must beSuccessfulTry(lr)
    }
  }

  "Writer" should {
    "be generated for a generic case class" in {
      implicit def singleWriter = Macros.writer[Single]
      val w = Macros.writer[Foo[Single]]

      w.writeTry(Foo(Single(BigDecimal(1)), "ipsum")) must beSuccessfulTry(
        BSONDocument(
          "bar" -> BSONDocument("value" -> BigDecimal(1)),
          "lorem" -> "ipsum"))
    }

    "be generated for class class with self reference" in {
      val w = Macros.writer[Bar]
      val bar1 = Bar("bar1", None)
      val doc1 = BSONDocument("name" -> "bar1")

      w.writeTry(bar1) must beSuccessfulTry(doc1) and {
        w.writeTry(Bar("bar2", Some(bar1))) must beSuccessfulTry(BSONDocument(
          "name" -> "bar2", "next" -> doc1))
      }
    }

    "be generated with @Flatten annotation" in {
      shapeless.test.illTyped("Macros.writer[InvalidRecursive]")
      shapeless.test.illTyped("Macros.writer[InvalidNonDoc]")

      val w = Macros.writer[LabelledRange]
      val lr = LabelledRange("range2", Range(start = 1, end = 3))
      val doc = BSONDocument("name" -> "range2", "start" -> 1, "end" -> 3)

      w.writeTry(lr) must beSuccessfulTry(doc)
    }
  }

  // ---

  def roundtrip[A](original: A)(implicit reader: BSONReader[A], writer: BSONWriter[A]): MatchResult[Any] = {
    def serialized = writer writeTry original
    def deserialized = serialized.flatMap(reader.readTry(_))

    deserialized must beSuccessfulTry(original)
  }

  def roundtrip[A](original: A, format: BSONReader[A] with BSONWriter[A]): MatchResult[Any] = roundtrip(original)(format, format)

  def roundtripImp[A](data: A)(implicit reader: BSONReader[A], writer: BSONWriter[A]) = roundtrip(data)

}
