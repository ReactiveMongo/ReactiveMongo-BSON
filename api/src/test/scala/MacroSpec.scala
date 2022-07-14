import scala.util.{ Failure, Success, Try }

import reactivemongo.api.bson.{
  BSON,
  BSONDecimal,
  BSONDocument,
  BSONDocumentHandler,
  BSONDocumentReader,
  BSONDocumentWriter,
  BSONHandler,
  BSONInteger,
  BSONNull,
  BSONReader,
  BSONString,
  BSONValue,
  BSONWriter,
  FieldNaming,
  MacroConfiguration,
  MacroOptions,
  Macros,
  TypeNaming
}
import reactivemongo.api.bson.TestUtils.typecheck
import reactivemongo.api.bson.exceptions.{
  HandlerException,
  TypeDoesNotMatchException
}

import org.specs2.matcher.MatchResult
import org.specs2.matcher.TypecheckMatchers._

import com.github.ghik.silencer.silent

final class MacroSpec
    extends org.specs2.mutable.Specification
    with MacroExtraSpec {

  "Macros".title

  import MacroTest._

  "Utility macros" should {
    "provide 'migrationRequired' compilation error" in {
      import reactivemongo.api.bson.migrationRequired

      typecheck("""migrationRequired[String]("Foo"): String""") must failWith(
        "Migration\\ required:\\ Foo"
      )
    }
  }

  "Configuration" should {
    import reactivemongo.api.bson.MacroCompilation.WithConfig

    "be resolved from the call site" in {
      val resolved = WithConfig.resolve

      resolved must_=== MacroConfiguration(
        fieldNaming = FieldNaming.SnakeCase
      ) and {
        resolved must not(beEqualTo(MacroConfiguration()))
      }
    }

    "be resolved from implicit scope" in {
      implicit def conf: MacroConfiguration = WithConfig.config

      val resolved = WithConfig.implicitConf

      resolved must_=== MacroConfiguration(
        fieldNaming = FieldNaming.SnakeCase
      ) and {
        resolved must not(beEqualTo(MacroConfiguration()))
      }
    }
  }

  "Formatter" should {
    "handle primitives" in {
      roundtrip(
        Primitives(1.2, "hai", true, 42, Long.MaxValue),
        Macros.handler[Primitives]
      )
    }

    "support nesting" in {
      implicit val personFormat = Macros.handler[Person]

      val doc = Pet("woof", Person("john", "doe"))

      roundtrip(doc, Macros.handler[Pet])
    }

    "support optional" >> {
      val some = Optional("some", Some("value"))
      val none = Optional("none", None)

      "using default instances" in {
        val format = Macros.handler[Optional]

        roundtrip(some, format) and roundtrip(none, format)
      }

      "using custom instances" in {
        implicit def optStrW: BSONWriter[Option[String]] =
          BSONWriter[Option[String]] {
            case Some(str) =>
              BSONDocument(f"$$str" -> str)

            case _ =>
              BSONNull
          }

        implicit def optStrR: BSONReader[Option[String]] =
          BSONReader.from[Option[String]] {
            case doc: BSONDocument =>
              doc.getAsUnflattenedTry[String](f"$$str")

            case _ =>
              Success(None)
          }

        val w: BSONDocumentWriter[Optional] = Macros.writer
        val r: BSONDocumentReader[Optional] = Macros.reader
        val h: BSONDocumentHandler[Optional] = Macros.handler

        val someBson = BSONDocument(
          "name" -> "some",
          "value" -> BSONDocument(f"$$str" -> "value")
        )
        val noneBson = BSONDocument("name" -> "none", "value" -> BSONNull)

        w.writeTry(some) must beSuccessfulTry(someBson) and {
          h.writeTry(some) must beSuccessfulTry(someBson)
        } and {
          r.readTry(someBson) must beSuccessfulTry(some)
        } and {
          h.readTry(someBson) must beSuccessfulTry(some)
        } and {
          w.writeTry(none) must beSuccessfulTry(noneBson)
        } and {
          h.writeTry(none) must beSuccessfulTry(noneBson)
        } and {
          r.readTry(noneBson) must beSuccessfulTry(none)
        } and {
          h.readTry(noneBson) must beSuccessfulTry(none)
        }
      }
    }

    "not support type mismatch for optional value" in {
      Macros
        .reader[Optional]
        .readTry(
          BSONDocument("name" -> "invalidValueType", "value" -> 4)
        ) must beFailedTry[Optional] // ("BSONInteger")
    }

    "support null for optional value" in {
      Macros
        .reader[Optional]
        .readTry(BSONDocument("name" -> "name", "value" -> BSONNull))
        .map(_.value) must beSuccessfulTry(Option.empty[String])
    }

    "write empty option as null" in {
      Macros
        .writer[OptionalAsNull]
        .writeTry(OptionalAsNull("asNull", None)) must beSuccessfulTry(
        BSONDocument("name" -> "asNull", "value" -> BSONNull)
      )
    }

    "support seq" in {
      roundtrip(
        WordLover("john", Seq("hello", "world")),
        Macros.handler[WordLover]
      )
    }

    "support single member case classes" in {
      roundtrip(Single(BigDecimal("12.345")), Macros.handler[Single])
    }

    "support single member options" in {
      val f = Macros.handler[OptionalSingle]

      roundtrip(OptionalSingle(Some("foo")), f) and {
        roundtrip(OptionalSingle(None), f)
      }
    }

    "support generic optional value" >> {
      val doc1 = BSONDocument("v" -> 1)
      val none = OptionalGeneric[String](1, None)

      val doc2 = BSONDocument("v" -> 2, "opt" -> "foo")
      val some = OptionalGeneric(2, Some("foo"))

      "for reader" in {
        val reader = Macros.reader[OptionalGeneric[String]]

        reader.readTry(doc1) must beSuccessfulTry(none) and {
          reader.readTry(doc2).aka("some valid") must beSuccessfulTry(some)
        } and {
          reader
            .readTry(BSONDocument("v" -> 3, "opt" -> 4.5D))
            .aka("some invalid") must beFailedTry[OptionalGeneric[String]]
        }
      }

      "for writer" in {
        val writer = Macros.writer[OptionalGeneric[String]]

        writer.writeTry(none) must beSuccessfulTry(doc1) and {
          writer.writeTry(some) must beSuccessfulTry(doc2)
        }
      }

      "for handler" in {
        val h = Macros.handler[OptionalGeneric[String]]

        roundtrip(none, h) and roundtrip(some, h)
      }
    }

    "support generic case class Foo" >> {
      implicit def singleHandler: Handler[Single] = Macros.handler[Single]

      "directly" in {
        roundtrip(
          Foo(Single(BigDecimal(123L)), "ipsum"),
          Macros.handler[Foo[Single]]
        )
      }

      "from generic function" in {
        def handler[T](
            implicit
            w: BSONDocumentWriter[T],
            r: BSONDocumentReader[T]
          ) = Macros.handler[Foo[T]]

        roundtrip(Foo(Single(BigDecimal(1.23D)), "ipsum"), handler[Single])
      }
    }

    "support generic case class GenSeq" in {
      implicit def singleHandler: BSONHandler[Single] =
        new BSONWriter[Single]
          with BSONReader[Single]
          with BSONHandler[Single] {
          def writeTry(single: Single) =
            BSONDecimal.fromBigDecimal(single.value)

          def readTry(bson: BSONValue): Try[Single] = bson match {
            case dec: BSONDecimal =>
              BSONDecimal.toBigDecimal(dec).map(Single(_))

            case _ =>
              Failure(
                TypeDoesNotMatchException(
                  "BSONDecimal",
                  bson.getClass.getSimpleName
                )
              )
          }
        }

      implicit def optionHandler[T](
          implicit
          h: BSONHandler[T]
        ): BSONDocumentHandler[Option[T]] = new BSONDocumentHandler[Option[T]] {
        def readDocument(doc: BSONDocument): Try[Option[T]] =
          doc.getAsUnflattenedTry[T](f"$$some")

        def writeTry(single: Option[T]): Try[BSONDocument] = single match {
          case Some(v) =>
            h.writeTry(v).map { x => BSONDocument(f"$$some" -> x) }

          case _ => Success(BSONDocument.empty)
        }
      }

      def genSeqHandler[
          T: BSONDocumentHandler
        ]: BSONDocumentHandler[GenSeq[T]] = Macros.handler[GenSeq[T]]

      val seq = GenSeq(
        items = Seq(Option.empty[Single], Option(Single(BigDecimal(1)))),
        count = 1
      )

      roundtrip(seq, genSeqHandler[Option[Single]])
    }

    "support auto-materialization for property types (not recommended)" in {
      val handler = Macros.handlerOpts[
        Person2,
        MacroOptions.AutomaticMaterialization with MacroOptions.DisableWarnings
      ]

      typecheck("Macros.handler[Person2]") must failWith(
        ".*found\\ for\\ .*itemList.*"
      ) and {
        roundtrip(
          Person2(
            name = "Name",
            age = 20,
            phoneNum = 33000000000L,
            itemList = Seq(
              Item(name = "#1", number = new FooVal(10)),
              Item(name = "#2", number = new FooVal(20))
            ),
            list = Seq(1, 2, 3)
          ),
          handler
        )
      }
    }

    "handle overloaded apply correctly" in {
      val doc1 = OverloadedApply("hello")
      val doc2 = OverloadedApply(List("hello", "world"))

      val f = Macros.handlerOpts[OverloadedApply, MacroOptions.DisableWarnings]

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

    "handle case class and handler inside trait" in {
      val t = new NestModule {}
      roundtrip(t.Nested("it works"), t.format)
    }

    "handle case class with refinement type as field" in {
      val pref = Preference[String](
        key = "_id",
        kind = PrefKind.of[String]("ID"),
        value = "unique"
      )

      val h = Macros.handler[Preference[String]]

      roundtrip(pref, h)
    }

    "handle case class inside trait with handler outside" in {
      val t = new NestModule {}
      import t._ // you need Nested in scope because t.Nested won't work
      val format = Macros.handler[Nested]
      roundtrip(Nested("it works"), format)
    }

    "respect compilation options" in {
      val format = Macros.handlerOpts[Person, MacroOptions.Verbose] // more stuff in compiler log

      roundtrip(Person("john", "doe"), format)
    }

    "not persist class name for case class" in {
      val person = Person("john", "doe")
      val format = Macros.handler[Person]

      val doc = format writeTry person

      doc
        .flatMap(_.getAsUnflattenedTry[String]("className"))
        .aka("class") must beSuccessfulTry(Option.empty[String]) and {
        roundtrip(person, format)
      }
    }

    "respect field naming" >> {
      val person = Person("Jane", "doe")

      def spec(
          handler: BSONDocumentHandler[Person],
          expectedBson: BSONDocument
        ) = {
        handler.writeTry(person) must beSuccessfulTry(expectedBson) and {
          handler.readTry(expectedBson) must beSuccessfulTry(person)
        }
      }

      "with default configuration" in {
        spec(
          handler = Macros.handler[Person],
          expectedBson =
            BSONDocument("firstName" -> "Jane", "lastName" -> "doe")
        )

      }

      "with implicit configuration (PascalCase)" in {
        implicit def cfg: MacroConfiguration =
          MacroConfiguration(fieldNaming = FieldNaming.PascalCase)

        spec(
          handler = Macros.handler[Person],
          expectedBson =
            BSONDocument("FirstName" -> "Jane", "LastName" -> "doe")
        )

      }

      "with macro-configured handler (SnakeCase)" in {
        spec(
          handler = Macros
            .configured(
              MacroConfiguration(fieldNaming = FieldNaming.SnakeCase)
            )
            .handler[Person],
          expectedBson =
            BSONDocument("first_name" -> "Jane", "last_name" -> "doe")
        )
      }
    }

    "handle union types (ADT)" >> {
      import Union._
      import MacroOptions._

      "with custom discriminator" in {
        val a = UA(1)
        val b = UB("hai")

        implicit val cfg: MacroConfiguration.Aux[MacroOptions.Default] =
          MacroConfiguration(discriminator = "_type")

        val format = Macros.handlerOpts[UT, UnionType[
          UA \/ UB \/ UC \/ UD \/ UF.type
        ] with AutomaticMaterialization]

        format
          .writeTry(a)
          .map(_.getAsOpt[String]("_type"))
          .aka("class #1") must beSuccessfulTry(
          Some("MacroTest.Union.UA")
        ) and {
          format
            .writeTry(b)
            .map(_.getAsOpt[String]("_type"))
            .aka("class #2") must beSuccessfulTry(Some("MacroTest.Union.UB"))
        } and roundtrip(a, format) and roundtrip(b, format)
      }

      "with simple type names" in {
        import Union._
        import MacroOptions._
        val a = UA(1)
        val b = UB("hai")

        implicit def config: MacroConfiguration.Aux[MacroOptions.Default] =
          MacroConfiguration.simpleTypeName

        val format = Macros.handlerOpts[UT, UnionType[
          UA \/ UB \/ UC \/ UD
        ] with AutomaticMaterialization]

        format
          .writeTry(a)
          .flatMap(_.getAsTry[String]("className"))
          .aka("discriminator UA") must beSuccessfulTry("UA") and {
          format
            .writeTry(b)
            .flatMap(_.getAsTry[String]("className"))
            .aka("discriminator UB") must beSuccessfulTry("UB")
        } and roundtrip(a, format) and roundtrip(b, format) and {
          // UE & UF not supported due to the UnionType specification

          format.writeTry(UE) must beFailedTry[BSONDocument] and {
            format.writeTry(UF) must beFailedTry[BSONDocument]
          } and {
            format.readTry(
              BSONDocument("className" -> UE.getClass.getSimpleName)
            ) must beFailedTry[UT]
          } and {
            format.readTry(
              BSONDocument("className" -> UF.getClass.getSimpleName)
            ) must beFailedTry[UT]
          }
        }
      }

      "without sealed trait using UnionType specification" in {
        import Union._
        import MacroOptions._

        val a = UA2(1)
        val b = UB2("hai")

        val format = Macros
          .handlerOpts[UT2, UnionType[UA2 \/ UB2] with AutomaticMaterialization]

        format
          .writeTry(a)
          .map(_.getAsOpt[String]("className"))
          .aka("class #1") must beSuccessfulTry(
          Some("MacroTest.Union.UA2")
        ) and {
          format
            .writeTry(b)
            .map(_.getAsOpt[String]("className"))
            .aka("class #2") must beSuccessfulTry(Some("MacroTest.Union.UB2"))
        } and roundtrip(a, format) and roundtrip(b, format)
      }

      "when invalid" >> {
        import InvalidUnion._

        implicit lazy val ah: BSONDocumentHandler[UA] = Macros.handler
        @silent implicit def bh[T]: BSONDocumentHandler[UB[T]] = ???
        implicit lazy val ch: BSONDocumentHandler[UC.type] = Macros.handler

        "without restriction" in {
          val expectedMsg =
            "Generic type MacroTest.InvalidUnion\\.UB.* is not supported.*"

          typecheck("Macros.reader[UT]") must failWith(expectedMsg) and {
            typecheck("Macros.writer[UT]") must failWith(expectedMsg)
          } and {
            typecheck("Macros.handler[UT]") must failWith(expectedMsg)
          }
        }

        "with restriction" in {
          import MacroOptions._

          @silent // Unused as dealiased at compile-time
          type Opts = UnionType[UA \/ UC.type] // Test macro options dealiased

          val reader = Macros.readerOpts[UT, Opts]
          val writer = Macros.writerOpts[UT, Opts]
          val handler = Macros.handlerOpts[UT, Opts]

          val adoc = BSONDocument(
            "n" -> 1,
            "className" -> "MacroTest.InvalidUnion.UA"
          )

          val cdoc = BSONDocument("className" -> "MacroTest.InvalidUnion.UC")

          writer.writeTry(UA(1)) must beSuccessfulTry(adoc) and {
            writer.writeTry(UC) must beSuccessfulTry(cdoc)
          } and {
            reader.readTry(adoc) must beSuccessfulTry(UA(1))
          } and {
            reader.readTry(cdoc) must beSuccessfulTry[UT](UC)
          } and {
            handler.writeTry(UA(1)) must beSuccessfulTry(adoc)
          } and {
            handler.writeTry(UC) must beSuccessfulTry(cdoc)
          } and {
            handler.readTry(adoc) must beSuccessfulTry(UA(1))
          } and {
            handler.readTry(cdoc) must beSuccessfulTry[UT](UC)
          }
        }
      }
    }

    "handle recursive structure" >> {
      import TreeModule.{ Node, Leaf, Tree }
      import MacroOptions._

      val tree: Tree = Node(Leaf("hi"), Node(Leaf("hello"), Leaf("world")))

      "with explicit handlers" in {
        roundtrip(tree, TreeModuleImplicits.handler)
      }

      "with recursive auto-materialization" in {
        val doc = BSONDocument(
          "className" -> "MacroTest.TreeModule.Node",
          "left" -> BSONDocument(
            "className" -> "MacroTest.TreeModule.Leaf",
            "data" -> "hi"
          ),
          "right" -> BSONDocument(
            "className" -> "MacroTest.TreeModule.Node",
            "left" -> BSONDocument(
              "className" -> "MacroTest.TreeModule.Leaf",
              "data" -> "hello"
            ),
            "right" -> BSONDocument(
              "className" -> "MacroTest.TreeModule.Leaf",
              "data" -> "world"
            )
          )
        )

        @silent // Unused as dealias
        type Opts = AutomaticMaterialization with MacroOptions.DisableWarnings

        typecheck("implicitly[BSONDocumentReader[Leaf]]") must failWith(
          ".*"
        ) and {
          typecheck("implicitly[BSONDocumentReader[Node]]") must failWith(".*")
        } and {
          val writer = Macros.writerOpts[Tree, Opts]

          writer.writeTry(tree) must beSuccessfulTry(doc)
        } and {
          val reader = Macros.readerOpts[Tree, Opts]

          reader.readTry(doc) must beSuccessfulTry(tree)
        } and {
          val handler = Macros.handlerOpts[Tree, Opts]

          handler.writeTry(tree) must beSuccessfulTry(doc) and {
            handler.readTry(doc) must beSuccessfulTry(tree)
          }
        }
      }
    }

    "grab an implicit handler for type used in union" in {
      import TreeCustom._
      val tree: Tree = Node(Leaf("hi"), Node(Leaf("hello"), Leaf("world")))
      val serialized = BSON writeDocument tree
      val deserialized = serialized.flatMap(BSON.readDocument[Tree](_))

      deserialized must beSuccessfulTry(
        Node(Leaf("hai"), Node(Leaf("hai"), Leaf("hai")))
      )
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

    "automate Union on sealed traits" >> {
      import MacroOptions._
      import Union._

      "but fail when the subtype is not provided with implicit instance" in {
        typecheck("Macros.handler[UT]") must failWith(".*not\\ found.*U.*")
      }

      "with auto-materialization & default configuration" in {
        implicit val format =
          Macros.handlerOpts[UT, AutomaticMaterialization with DisableWarnings /* disabled: Cannot handle object MacroTest\\.Union\\.UE */ ]

        format
          .writeTry(UA(1))
          .flatMap(_.getAsTry[String]("className"))
          .aka("class #1") must beSuccessfulTry("MacroTest.Union.UA") and {
          format
            .writeTry(UB("buzz"))
            .flatMap(_.getAsUnflattenedTry[String]("className"))
            .aka("class #2") must beSuccessfulTry(Some("MacroTest.Union.UB"))

        } and roundtripImp[UT](UA(17)) and roundtripImp[UT](UB("foo")) and {
          roundtripImp[UT](UC("bar")) and roundtripImp[UT](UD("baz"))
        } and roundtripImp[UT](UF)
      }

      "with custom configuration" in {
        // For sub-type UA
        implicit def aReader: BSONDocumentReader[UA] = Macros.reader[UA]
        implicit def aWriter: BSONDocumentWriter[UA] = Macros.writer[UA]

        // Sub-type UC, UD, UF
        implicit def bHandler: BSONDocumentHandler[UB] = Macros.handler[UB]
        implicit def cHandler: BSONDocumentHandler[UC] = Macros.handler[UC]
        implicit def dHandler: BSONDocumentHandler[UD] = Macros.handler[UD]
        implicit def eHandler: BSONDocumentHandler[UE.type] =
          Macros.handler[UE.type]

        implicit def fHandler: BSONDocumentHandler[UF.type] =
          Macros.handler[UF.type]

        @silent("Cannot handle object MacroTest\\.Union\\.UE" /*expected*/ )
        implicit val format: BSONDocumentHandler[UT] = {
          implicit val cfg: MacroConfiguration = MacroConfiguration(
            discriminator = "_type",
            typeNaming = TypeNaming.SimpleName.andThen(_.toLowerCase)
          )

          Macros.handler[UT]
        }

        format
          .writeTry(UA(1))
          .flatMap(_.getAsTry[String]("_type"))
          .aka("custom discriminator") must beSuccessfulTry("ua") and {
          format
            .writeTry(UB("fuzz"))
            .flatMap(_.getAsUnflattenedTry[String]("_type"))
            .aka("class #2") must beSuccessfulTry(Some("ub"))

        } and roundtripImp[UT](UA(17)) and roundtripImp[UT](UB("foo")) and {
          roundtripImp[UT](UC("bar")) and roundtripImp[UT](UD("baz"))
        } and roundtripImp[UT](UF)

      }
    }

    "support automatic implementations search with nested traits" in {
      import MacroOptions._
      import InheritanceModule._
      implicit val format = Macros.handlerOpts[T, AutomaticMaterialization]

      format
        .writeTry(A())
        .flatMap(_.getAsTry[String]("className"))
        .aka("class #1") must beSuccessfulTry(
        "MacroTest.InheritanceModule.A"
      ) and {
        format
          .writeOpt(B)
          .flatMap(_.getAsOpt[String]("className"))
          .aka("class #2") must beSome("MacroTest.InheritanceModule.B")

      } and {
        roundtripImp[T](A()) and roundtripImp[T](B) and roundtripImp[T](C())
      }
    }

    "automate Union on sealed traits with simple name" in {
      import MacroOptions._
      import Union._

      val configuredMacros = Macros.configured(
        MacroConfiguration[AutomaticMaterialization](
          typeNaming = TypeNaming.SimpleName
        )
      )

      @silent("Cannot handle object MacroTest\\.Union\\.UE" /*expected*/ )
      implicit val format: BSONDocumentHandler[UT] =
        configuredMacros.handler[UT]

      format
        .writeTry(UA(1))
        .flatMap(_.getAsTry[String]("className")) must beSuccessfulTry(
        "UA"
      ) and {
        format
          .writeOpt(UB("buzz"))
          .flatMap(_.getAsOpt[String]("className")) must beSome("UB")
      } and {
        roundtripImp[UT](UA(17)) and roundtripImp[UT](UB("foo")) and {
          roundtripImp[UT](UC("bar")) and roundtripImp[UT](UD("baz"))
        }
      }
    }

    "support automatic implementations search with nested traits with simple name" in {
      import InheritanceModule._

      implicit val format: BSONDocumentHandler[T] =
        Macros.configured(MacroConfiguration.simpleTypeName).handler[T]

      format
        .writeTry(A())
        .flatMap(_.getAsTry[String]("className")) must beSuccessfulTry(
        "A"
      ) and {
        format
          .writeTry(B)
          .flatMap(_.getAsTry[String]("className")) must beSuccessfulTry("B")

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
      "with error if no default value for read" in {
        val writer = Macros.writer[NotIgnorable]

        val expectedReadErr =
          "Cannot\\ ignore\\ 'MacroTest\\.NotIgnorable\\.title'"

        writer.writeTry(NotIgnorable("foo", 1)) must beSuccessfulTry(
          BSONDocument("score" -> 1)
        ) and {
          typecheck("Macros.reader[NotIgnorable]") must failWith(
            expectedReadErr
          )
        } and {
          typecheck("Macros.handler[NotIgnorable]") must failWith(
            expectedReadErr
          )
        }
      }

      "with Pair type having a default field value" in {
        val pairHandler = Macros.handler[Pair]
        val pairWriter = Macros.writer[Pair]
        val pairReader = Macros.reader[Pair]

        val pair1 = Pair(left = "_1", right = "right1")
        val pair2 = Pair(left = "_2", right = "right2")

        val doc1 = BSONDocument("right" -> "right1")
        val doc2 = BSONDocument("right" -> "right2")

        pairHandler.writeTry(pair1) must beSuccessfulTry(doc1) and {
          pairWriter.writeTry(pair2) must beSuccessfulTry(doc2)
        } and {
          pairHandler.readTry(doc2) must beSuccessfulTry(
            Pair(
              left = "_left", // from default field value
              right = pair2.right
            )
          )
        } and {
          pairReader.readTry(doc1) must beSuccessfulTry(
            Pair(
              left = "_left", // from default field value
              right = pair1.right
            )
          )
        }
      }

      "along with Key annotation" in {
        implicit val writer: BSONDocumentWriter[IgnoredAndKey] =
          Macros.writer[IgnoredAndKey]

        implicit val reader: BSONDocumentReader[IgnoredAndKey] =
          Macros.reader[IgnoredAndKey]

        implicit val handler: BSONDocumentHandler[IgnoredAndKey] =
          Macros.handler[IgnoredAndKey]

        val expected = BSONDocument("second" -> "foo")
        val v = IgnoredAndKey(Person("john", "doe"), "foo")

        val withDefault = IgnoredAndKey(
          a = Person("first", "last"), // from @DefaultValue
          b = "foo"
        )

        writer.writeTry(v) must beSuccessfulTry(expected) and {
          handler.writeTry(v) must beSuccessfulTry(expected)
        } and {
          handler.readTry(expected) must beSuccessfulTry(withDefault)
        } and {
          reader.readTry(expected) must beSuccessfulTry(withDefault)
        }
      }
    }

    "be generated for class class with self reference" in {
      val h = Macros.handler[Bar]
      val bar1 = Bar("bar1", None)
      val doc1 = BSONDocument("name" -> "bar1")

      h.readTry(doc1) must beSuccessfulTry(bar1) and {
        h.readTry(BSONDocument("name" -> "bar2", "next" -> doc1))
          .aka("bar2") must beSuccessfulTry(Bar("bar2", Some(bar1)))

      } and {
        h.writeTry(bar1) must beSuccessfulTry(doc1)
      } and {
        h.writeTry(Bar("bar2", Some(bar1))) must beSuccessfulTry(
          BSONDocument("name" -> "bar2", "next" -> doc1)
        )
      }
    }

    "support @Flatten annotation" in {
      typecheck("Macros.handler[InvalidRecursive]") must failWith(
        "Cannot\\ flatten\\ .*\\ for\\ 'MacroTest\\.InvalidRecursive\\.parent':\\ recursive\\ type"
      ) and {
        typecheck("Macros.handler[InvalidNonDoc]") must failWith(
          ".*doesn't\\ conform\\ BSONDocument.*"
        )
      } and {
        roundtrip(
          LabelledRange("range1", Range(start = 2, end = 5)),
          Macros.handler[LabelledRange]
        )
      }
    }

    "support @Reader & @Writer annotations" in {
      val reader1 = Macros.reader[PerField1[String]]
      val writer = Macros.writerOpts[PerField1[String], MacroOptions.Verbose]

      val expectedVal = PerField1[String](
        id = 1L,
        status = "on",
        score = 2.35F,
        description = Some("foo"),
        range = Range(3, 5),
        foo = "1"
      )

      val expectedDoc =
        BSONDocument(
          "id" -> 1L,
          "status" -> "on",
          "score" -> "2.35",
          "description" -> "foo",
          "range" -> Seq(3, 5),
          "foo" -> "1"
        )

      writer.writeTry(expectedVal) must beSuccessfulTry(expectedDoc) and {
        reader1.readTry(expectedDoc) must beFailedTry
          .withThrowable[HandlerException]( // As status is asymetric ...
            // ... with just a custom @Writer but no corresponding @Reader
            "Fails\\ to\\ handle\\ 'score':\\ BSONString\\ !=\\ <float>"
          )
      } and {
        // Define a BSONReader for 'score: Float' corresponding to @Writer
        implicit def floatReader: BSONReader[Float] =
          BSONReader.collect[Float] { case BSONString(f) => f.toFloat }

        val reader2 = Macros.reader[PerField1[String]]

        reader2.readTry(expectedDoc) must beSuccessfulTry(expectedVal)
      }
    }

    "be generated for Value class" in {
      val handler = Macros.valueHandler[FooVal]

      typecheck("Macros.valueHandler[Person]") must failWith(".*Person.*") and {
        typecheck("Macros.valueHandler[BarVal]") must failWith(
          ".*not found.*BSON(Writer|Reader)\\[.*Exception\\]"
        )
      } and {
        handler.readTry(BSONInteger(1)) must beSuccessfulTry(new FooVal(1))
      } and {
        handler.readOpt(BSONInteger(2)) must beSome(new FooVal(2))
      } and {
        handler.readTry(BSONString("oof")) must beFailedTry
      } and {
        handler.readOpt(BSONString("bar")) must beNone
      } and {
        handler.writeTry(new FooVal(1)) must beSuccessfulTry(BSONInteger(1))
      } and {
        handler.writeOpt(new FooVal(2)) must beSome(BSONInteger(2))
      }
    }
  }

  "Reader" should {
    "throw meaningful exception if required field is missing" in {
      val personDoc = BSONDocument("firstName" -> "joe")

      Macros.reader[Person].readTry(personDoc) must beFailedTry[Person].like {
        case e => e.getMessage must contain("lastName")
      }
    }

    "be generated to supported similar numeric BSON types" in {
      val primitivesDoc = BSONDocument(
        "dbl" -> 2D,
        "str" -> "str",
        "bl" -> true,
        "int" -> 2D,
        "long" -> 2L
      )

      Macros
        .reader[Primitives]
        .readTry(primitivesDoc)
        .aka("read") must beSuccessfulTry(
        Primitives(dbl = 2D, str = "str", bl = true, int = 2, long = 2L)
      )
    }

    "be generated for a generic case class" in {
      implicit def singleReader: BSONDocumentReader[Single] =
        Macros.reader[Single]

      val r = Macros.reader[Foo[Single]]
      val big = BigDecimal(1.23D)

      r.readTry(
        BSONDocument("bar" -> BSONDocument("value" -> big), "lorem" -> "ipsum")
      ) must beSuccessfulTry(Foo(Single(big), "ipsum"))
    }

    "be generated for case class with self reference" in {
      val r = Macros.reader[Bar]
      val bar1 = Bar("bar1", None)
      val doc1 = BSONDocument("name" -> "bar1")

      r.readTry(doc1) must beSuccessfulTry(bar1) and {
        r.readTry(BSONDocument("name" -> "bar2", "next" -> doc1))
          .aka("bar2") must beSuccessfulTry(Bar("bar2", Some(bar1)))
      }
    }

    "be generated for case class with refinement type as field" in {
      val pref = Preference(
        key = "expires",
        kind = PrefKind.of[Int]("Expiry"),
        value = 123
      )

      val reader = Macros.handler[Preference[Int]]

      reader.readTry(
        BSONDocument(
          "key" -> "expires",
          "kind" -> BSONDocument("name" -> "Expiry"),
          "value" -> 123
        )
      ) must beSuccessfulTry(pref)
    }

    "be generated with @Flatten annotation" in {
      typecheck("Macros.reader[InvalidRecursive]") must failWith(
        "Cannot\\ flatten\\ reader\\ for\\ 'MacroTest\\.InvalidRecursive\\.parent':\\ recursive\\ type"
      ) and {
        typecheck("Macros.reader[InvalidNonDoc]") must failWith(
          "doesn't\\ conform\\ BSONDocumentReader"
        )
      } and {
        val r = Macros.reader[LabelledRange]
        val doc = BSONDocument("name" -> "range1", "start" -> 2, "end" -> 5)
        val lr = LabelledRange("range1", Range(start = 2, end = 5))

        r.readTry(doc) must beSuccessfulTry(lr)
      }
    }

    "be generated for case class with default values" >> {
      "using class defaults" in {
        val r1: BSONDocumentReader[WithDefaultValues1] =
          Macros
            .using[MacroOptions.ReadDefaultValues]
            .reader[WithDefaultValues1]

        val r2: BSONDocumentReader[WithDefaultValues1] = {
          implicit val cfg: MacroConfiguration.Aux[MacroOptions.ReadDefaultValues] =
            MacroConfiguration[MacroOptions.ReadDefaultValues]()

          Macros.reader[WithDefaultValues1]
        }

        val minimalDoc = BSONDocument("id" -> 1)
        val expected = WithDefaultValues1(id = 1)

        r1.readTry(minimalDoc) must beSuccessfulTry(expected) and {
          r2.readTry(minimalDoc) must beSuccessfulTry(expected)
        }
      }

      "using annotation @DefaultValue" in {
        val r1: BSONDocumentReader[WithDefaultValues2] =
          Macros
            .using[MacroOptions.ReadDefaultValues]
            .reader[WithDefaultValues2]

        val r2: BSONDocumentReader[WithDefaultValues2] = {
          implicit val cfg: MacroConfiguration.Aux[MacroOptions.ReadDefaultValues] =
            MacroConfiguration[MacroOptions.ReadDefaultValues]()

          Macros.reader[WithDefaultValues2]
        }

        val minimalDoc = BSONDocument("id" -> 1)
        val expected = WithDefaultValues2(
          id = 1,
          title = "default2",
          score = Some(45.6F),
          range = Range(7, 11)
        )

        r1.readTry(minimalDoc) must beSuccessfulTry(expected) and {
          r2.readTry(minimalDoc) must beSuccessfulTry(expected)
        } and {
          typecheck("Macros.reader[WithDefaultValues3]") must failWith(
            "Invalid\\ annotation\\ .*DefaultValue.* for\\ 'MacroTest\\.WithDefaultValues3\\.name':\\ String\\ .*expected"
          )
        }
      }
    }

    "support @Reader annotation" in {
      val reader = Macros.readerOpts[PerField1[Int], MacroOptions.Verbose]

      reader.readTry(
        BSONDocument(
          "id" -> 1L,
          "status" -> 1,
          "score" -> 2.34F,
          "description" -> "foo",
          "range" -> Seq(3, 5),
          "foo" -> 1
        )
      ) must beSuccessfulTry(
        PerField1[Int](
          id = 1L,
          status = "on",
          score = 2.34F,
          description = Some("foo"),
          range = Range(3, 5),
          foo = 1
        )
      ) and {
        reader.readTry(
          BSONDocument(
            "id" -> 2L,
            "status" -> 0,
            "score" -> 45.6F,
            "description" -> 0,
            "range" -> Seq(7, 11),
            "foo" -> 2
          )
        ) must beSuccessfulTry(
          PerField1[Int](
            id = 2L,
            status = "off",
            score = 45.6F,
            description = None,
            range = Range(7, 11),
            foo = 2
          )
        )
      } and {
        typecheck("Macros.reader[PerField2]") must failWith(
          "Invalid\\ annotation\\ @Reader.*\\ for\\ 'MacroTest\\.PerField2\\.name':\\ Reader\\[String\\]"
        )
      }
    }

    "be generated for Value class" in {
      val reader = Macros.valueReader[FooVal]

      typecheck("Macros.valueReader[Person]") must failWith(".*Person.*") and {
        typecheck("Macros.valueReader[BarVal]") must failWith(
          ".*not found.*BSONReader\\[.*Exception\\]"
        )
      } and {
        reader.readTry(BSONInteger(1)) must beSuccessfulTry(new FooVal(1))
      } and {
        reader.readOpt(BSONInteger(2)) must beSome(new FooVal(2))
      } and {
        reader.readTry(BSONString("oof")) must beFailedTry
      } and {
        reader.readOpt(BSONString("bar")) must beNone
      }
    }

    "be generated for Map property" >> {
      "with Locale keys" in {
        import java.util.Locale

        val reader = Macros.reader[WithMap1]

        reader.readTry(
          BSONDocument(
            "name" -> "Foo",
            "localizedDescription" -> BSONDocument("fr-FR" -> "French")
          )
        ) must beSuccessfulTry(
          WithMap1(
            name = "Foo",
            localizedDescription = Map(Locale.FRANCE -> "French")
          )
        )

      }

      "with custom KeyReader for FooVal keys" in {
        import reactivemongo.api.bson.KeyReader

        implicit def keyReader: KeyReader[FooVal] =
          KeyReader.from[FooVal] { str => Try(str.toInt).map(new FooVal(_)) }

        val reader = Macros.reader[WithMap2]

        reader.readTry(
          BSONDocument(
            "name" -> "Bar",
            "values" -> BSONDocument("1" -> "Lorem")
          )
        ) must beSuccessfulTry(
          WithMap2(name = "Bar", values = Map((new FooVal(1)) -> "Lorem"))
        )

      }
    }

    "respect field naming" >> {
      val person = Person("Jane", "doe")

      def spec(
          reader: BSONDocumentReader[Person],
          doc: BSONDocument
        ) = reader.readTry(doc) must beSuccessfulTry(person)

      "with default configuration" in {
        spec(
          reader = Macros.reader[Person],
          doc = BSONDocument("firstName" -> "Jane", "lastName" -> "doe")
        )
      }

      "with implicit configuration (PascalCase)" in {
        implicit def cfg: MacroConfiguration =
          MacroConfiguration(fieldNaming = FieldNaming.PascalCase)

        spec(
          reader = Macros.reader[Person],
          doc = BSONDocument("FirstName" -> "Jane", "LastName" -> "doe")
        )
      }

      "with macro-configured handler (SnakeCase)" in {
        spec(
          reader = Macros
            .configured(
              MacroConfiguration(fieldNaming = FieldNaming.SnakeCase)
            )
            .reader[Person],
          doc = BSONDocument("first_name" -> "Jane", "last_name" -> "doe")
        )
      }
    }
  }

  "Writer" should {
    "be generated for a generic case class" in {
      implicit def singleWriter: BSONDocumentWriter[Single] =
        Macros.writer[Single]

      val w = Macros.writer[Foo[Single]]

      w.writeTry(Foo(Single(BigDecimal(1)), "ipsum")) must beSuccessfulTry(
        BSONDocument(
          "bar" -> BSONDocument("value" -> BigDecimal(1)),
          "lorem" -> "ipsum"
        )
      )
    }

    "be generated for non-case class" in {
      import Union.UB

      val w = UB.handler

      w.writeTry(new UB("foo")) must beSuccessfulTry(BSONDocument("s" -> "foo"))
    }

    "be generated for singleton" in {
      val w1 = Macros.writer[Union.UE.type]
      val w2 = Macros.writer[Union.UF.type]

      w1.writeTry(Union.UE) must beSuccessfulTry[BSONDocument](
        BSONDocument.empty
      ) and {
        w2.writeTry(Union.UF) must beSuccessfulTry[BSONDocument](
          BSONDocument.empty
        )
      }
    }

    "be generated for class class with self reference" in {
      val w = Macros.writer[Bar]
      val bar1 = Bar("bar1", None)
      val doc1 = BSONDocument("name" -> "bar1")

      w.writeTry(bar1) must beSuccessfulTry(doc1) and {
        w.writeTry(Bar("bar2", Some(bar1))) must beSuccessfulTry(
          BSONDocument("name" -> "bar2", "next" -> doc1)
        )
      }
    }

    "be generated for case class with refinement type as field" in {
      val pref = Preference(
        key = "expires",
        kind = PrefKind.of[Int]("Expiry"),
        value = 123
      )

      val writer = Macros.writer[Preference[Int]]

      writer.writeTry(pref) must beSuccessfulTry(
        BSONDocument(
          "key" -> "expires",
          "kind" -> BSONDocument("name" -> "Expiry"),
          "value" -> 123
        )
      )
    }

    "be generated with @Flatten annotation" in {
      typecheck("Macros.writer[InvalidRecursive]") must failWith(
        "Cannot\\ flatten\\ writer\\ for\\ 'MacroTest\\.InvalidRecursive\\.parent':\\ recursive\\ type"
      ) and {
        typecheck("Macros.writer[InvalidNonDoc]") must failWith(
          "doesn't\\ conform\\ BSONDocumentWriter"
        )
      } and {
        val w = Macros.writer[LabelledRange]
        val lr = LabelledRange("range2", Range(start = 1, end = 3))
        val doc = BSONDocument("name" -> "range2", "start" -> 1, "end" -> 3)

        w.writeTry(lr) must beSuccessfulTry(doc)
      }
    }

    "support @Writer annotation" in {
      val writer = Macros.writer[PerField1[String]]

      writer.writeTry(
        PerField1[String](
          id = 1L,
          status = "on",
          score = 2.34F,
          description = Some("foo"),
          range = Range(3, 5),
          foo = "1"
        )
      ) must beSuccessfulTry(
        BSONDocument(
          "id" -> 1L,
          "status" -> "on",
          "score" -> "2.34",
          "description" -> "foo",
          "range" -> Seq(3, 5),
          "foo" -> "1"
        )
      ) and {
        writer.writeTry(
          PerField1[String](
            id = 2L,
            status = "off",
            score = 45.6F,
            description = None,
            range = Range(7, 11),
            foo = "2"
          )
        ) must beSuccessfulTry(
          BSONDocument(
            "id" -> 2L,
            "status" -> "off",
            "score" -> "45.6",
            "range" -> Seq(7, 11),
            "foo" -> "2",
            "description" -> 0
          )
        )

      } and {
        typecheck("Macros.writer[PerField2]") must failWith(
          "Invalid\\ annotation\\ @Writer.*\\ for\\ 'MacroTest.*\\.PerField2\\.name':\\ Writer\\[.*String\\]"
        )
      }
    }

    "be generated for Value class" in {
      val writer = Macros.valueWriter[FooVal]

      typecheck("Macros.valueWriter[Person]") must failWith(".*Person.*") and {
        typecheck("Macros.valueWriter[BarVal]") must failWith(
          ".*not found.*BSONWriter\\[.*Exception\\]"
        )
      } and {
        writer.writeTry(new FooVal(1)) must beSuccessfulTry(BSONInteger(1))
      } and {
        writer.writeOpt(new FooVal(2)) must beSome(BSONInteger(2))
      }
    }

    "be generated for Map property" >> {
      "with Locale keys" in {
        import java.util.Locale

        val writer = Macros.writer[WithMap1]

        writer.writeTry(
          WithMap1(
            name = "Foo",
            localizedDescription = Map(Locale.FRANCE -> "French")
          )
        ) must beSuccessfulTry(
          BSONDocument(
            "name" -> "Foo",
            "localizedDescription" -> BSONDocument("fr-FR" -> "French")
          )
        )

      }

      "with custom KeyWriter for FooVal keys" in {
        import reactivemongo.api.bson.KeyWriter

        implicit def keyWriter: KeyWriter[FooVal] =
          KeyWriter[FooVal](_.v.toString)

        val writer = Macros.writer[WithMap2]

        writer.writeTry(
          WithMap2(name = "Bar", values = Map((new FooVal(1)) -> "Lorem"))
        ) must beSuccessfulTry(
          BSONDocument(
            "name" -> "Bar",
            "values" -> BSONDocument("1" -> "Lorem")
          )
        )
      }
    }

    "be generated for sealed family" >> {
      "but fail when the subtype is not provided with implicit instance" in {
        typecheck("Macros.writer[Union.UT]") must failWith(
          ".*not\\ found.*U[ABCDEF].*"
        )
      }

      "when instances are defined for subtypes" in {
        import Union._

        implicit val uaw = Macros.writer[UA]
        implicit val ucw = Macros.writer[UC]
        implicit val udw = Macros.writer[UD]
        implicit val uew = Macros.writer[UE.type]
        implicit val ufw = Macros.writer[UF.type]
        val w = Macros.writer[UT]

        w.writeTry(UA(1)) must beSuccessfulTry(
          BSONDocument(
            "n" -> 1,
            "className" -> "MacroTest.Union.UA"
          )
        ) and {
          w.writeTry(UB("bar")) must beSuccessfulTry(
            BSONDocument(
              "s" -> "bar",
              "className" -> "MacroTest.Union.UB"
            )
          )
        } and {
          w.writeTry(UC("lorem")) must beSuccessfulTry(
            BSONDocument(
              "s" -> "lorem",
              "className" -> "MacroTest.Union.UC"
            )
          )
        } and {
          w.writeTry(UD("ipsum")) must beSuccessfulTry(
            BSONDocument(
              "s" -> "ipsum",
              "className" -> "MacroTest.Union.UD"
            )
          )
        } and {
          w.writeTry(UE) must beSuccessfulTry(
            BSONDocument("className" -> "MacroTest.Union.UE")
          )
        } and {
          w.writeTry(UF) must beSuccessfulTry(
            BSONDocument("className" -> "MacroTest.Union.UF")
          )
        }
      }
    }

    "respect field naming" >> {
      val person = Person("Jane", "doe")

      def spec(
          writer: BSONDocumentWriter[Person],
          expectedBson: BSONDocument
        ) = writer.writeTry(person) must beSuccessfulTry(expectedBson)

      "with default configuration" in {
        spec(
          writer = Macros.writer[Person],
          expectedBson =
            BSONDocument("firstName" -> "Jane", "lastName" -> "doe")
        )
      }

      "with implicit configuration (PascalCase)" in {
        implicit def cfg: MacroConfiguration =
          MacroConfiguration(fieldNaming = FieldNaming.PascalCase)

        spec(
          writer = Macros.writer[Person],
          expectedBson =
            BSONDocument("FirstName" -> "Jane", "LastName" -> "doe")
        )
      }

      "with macro-configured handler (SnakeCase)" in {
        spec(
          writer = Macros
            .configured(
              MacroConfiguration(fieldNaming = FieldNaming.SnakeCase)
            )
            .writer[Person],
          expectedBson =
            BSONDocument("first_name" -> "Jane", "last_name" -> "doe")
        )
      }
    }
  }

  // ---

  def roundtrip[A](
      original: A
    )(implicit
      reader: BSONReader[A],
      writer: BSONWriter[A]
    ): MatchResult[Any] = {
    def serialized = writer writeTry original
    def deserialized = serialized.flatMap(reader.readTry(_))

    deserialized must beSuccessfulTry(original)
  }

  def roundtrip[A](
      original: A,
      format: BSONReader[A] with BSONWriter[A]
    ): MatchResult[Any] = roundtrip(original)(format, format)

  def roundtripImp[A](
      data: A
    )(implicit
      reader: BSONReader[A],
      writer: BSONWriter[A]
    ) = roundtrip(data)
}
