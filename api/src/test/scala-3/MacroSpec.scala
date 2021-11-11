import scala.util.{ Failure, Success, Try }

import reactivemongo.api.bson.{
  BSON,
  BSONDateTime,
  BSONDecimal,
  BSONDocument,
  BSONDocumentHandler,
  BSONDocumentReader,
  BSONDocumentWriter,
  BSONHandler,
  BSONInteger,
  BSONLong,
  BSONNull,
  BSONReader,
  BSONString,
  BSONValue,
  BSONWriter,
  FieldNaming,
  MacroConfiguration,
  MacroAnnotations,
  Macros
}

import reactivemongo.api.bson.exceptions.{
  HandlerException,
  TypeDoesNotMatchException
}

import org.specs2.execute._
import org.specs2.matcher.MatchResult
import org.specs2.matcher.TypecheckMatchers._

import com.github.ghik.silencer.silent

import Typecheck._

import reactivemongo.api.bson.TestUtils.typecheck

// TODO: Common MacroSpec
class MacroSpec extends org.specs2.mutable.Specification:
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
    "write empty option as null" in {
      Macros
        .writer[OptionalAsNull]
        .writeTry(OptionalAsNull("asNull", None)) must beSuccessfulTry(
        BSONDocument("name" -> "asNull", "value" -> BSONNull)
      )
    }

    // TODO

    "support generic optional value" >> {
      val doc1 = BSONDocument("v" -> 1)
      val none = OptionalGeneric[String](1, None)

      val doc2 = BSONDocument("v" -> 2, "opt" -> "foo")
      val some = OptionalGeneric(2, Some("foo"))

      // TODO

      "for writer" in {
        val writer = Macros.writer[OptionalGeneric[String]]

        writer.writeTry(none) must beSuccessfulTry(doc1) and {
          writer.writeTry(some) must beSuccessfulTry(doc2)
        }
      }

      // TODO
    }

    // TODO

    "skip ignored fields" >> {
      "with error if no default value for read" in {
        val writer = Macros.writer[NotIgnorable]

        val expectedReadErr =
          "Cannot\\ ignore\\ 'MacroTest\\.NotIgnorable\\.title'"

        writer.writeTry(NotIgnorable("foo", 1)) must beSuccessfulTry(
          BSONDocument("score" -> 1)
        ) and {
          /* TODO:
          typecheck("Macros.reader[NotIgnorable]") must failWith(
            expectedReadErr
           ) */
          ok
        } and {
          /* TODO:
          typecheck("Macros.handler[NotIgnorable]") must failWith(
            expectedReadErr
          )
           */
          ok
        }
      }

      "with Pair type having a default field value" in {
        //val pairHandler = Macros.handler[Pair]
        val pairWriter = Macros.writer[Pair]
        //val pairReader = Macros.reader[Pair]

        val pair1 = Pair(left = "_1", right = "right1")
        val pair2 = Pair(left = "_2", right = "right2")

        val doc1 = BSONDocument("right" -> "right1")
        val doc2 = BSONDocument("right" -> "right2")

        /* TODO: pairHandler.writeTry(pair1) must beSuccessfulTry(doc1) and*/
        {
          pairWriter.writeTry(pair2) must beSuccessfulTry(doc2)
        } and {
          /* TODO
          pairHandler.readTry(doc2) must beSuccessfulTry(
            Pair(
              left = "_left", // from default field value
              right = pair2.right
            )
          )
           */
          ok
        } and {
          /* TODO:
          pairReader.readTry(doc1) must beSuccessfulTry(
            Pair(
              left = "_left", // from default field value
              right = pair1.right
            )
          )
           */
          ok
        }
      }

      "along with Key annotation" in {
        implicit val writer: BSONDocumentWriter[IgnoredAndKey] =
          Macros.writer[IgnoredAndKey]

        /* TODO
        implicit val reader: BSONDocumentReader[IgnoredAndKey] =
          Macros.reader[IgnoredAndKey]

        implicit val handler: BSONDocumentHandler[IgnoredAndKey] =
          Macros.handler[IgnoredAndKey]
         */

        val expected = BSONDocument("second" -> "foo")
        val v = IgnoredAndKey(Person("john", "doe"), "foo")

        val withDefault = IgnoredAndKey(
          a = Person("first", "last"), // from @DefaultValue
          b = "foo"
        )

        writer.writeTry(v) must beSuccessfulTry(expected) and {
          ok /* TODO: handler.writeTry(v) must beSuccessfulTry(expected) */
        } /* TODO: and {
          handler.readTry(expected) must beSuccessfulTry(withDefault)
        } and {
          reader.readTry(expected) must beSuccessfulTry(withDefault)
        } */
      }
    }

    "be generated for class class with self reference" in {
      val h = Macros.writer[Bar] // TODO: Macros.handler[Bar]
      val bar1 = Bar("bar1", None)
      val doc1 = BSONDocument("name" -> "bar1")

      /* TODO:
      h.readTry(doc1) must beSuccessfulTry(bar1) and {
        h.readTry(BSONDocument("name" -> "bar2", "next" -> doc1))
          .aka("bar2") must beSuccessfulTry(Bar("bar2", Some(bar1)))

      } and */
      (h.writeTry(bar1) must beSuccessfulTry(doc1)) and {
        h.writeTry(Bar("bar2", Some(bar1))) must beSuccessfulTry(
          BSONDocument("name" -> "bar2", "next" -> doc1)
        )
      }
    }

    // TODO

    "handle case class with implicits" >> {
      "should fail on Scala3 without custom ProductOf" in {
        typecheck("Macros.writer[WithImplicit2[Double]]") must failWith(
          ".*ProductOf\\[MacroTest\\$\\.WithImplicit2\\]"
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

    "be generated with @Flatten annotation" in {
      Macros.writer[InvalidRecursive]

      /* TODO
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
       */
      ok
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

    /* TODO
    "be generated for Value class" in {
      val writer = Macros.valueWriter[FooVal]

      typecheck("Macros.valueWriter[Person]") must failWith(
        "Person.* do not conform to.* AnyVal"
      ) and {
        typecheck("Macros.valueWriter[BarVal]") must failWith(
          "Implicit not found for 'Exception': .*BSONWriter\\[java\\.lang\\.Exception\\]"
        )
      } and {
        writer.writeTry(new FooVal(1)) must beSuccessfulTry(BSONInteger(1))
      } and {
        writer.writeOpt(new FooVal(2)) must beSome(BSONInteger(2))
      }
    }
     */

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

      /* TODO
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

      } */
    }
  }
end MacroSpec
