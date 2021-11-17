import reactivemongo.api.bson.{ BSONDocument, BSONDocumentWriter, Macros }

import reactivemongo.api.bson.TestUtils.typecheck

import org.specs2.matcher.TypecheckMatchers._

case class Lorem(
    name: String,
    kpi: Int *: Int *: EmptyTuple,
    tup: Tuple2[Float, Double])

object Lorem {
  import reactivemongo.api.bson._ // TODO: Move implicit not to have this

  val x = summon[BSONWriter[Int *: Int *: EmptyTuple]]

  val writer = Macros.writer[Lorem] // TODO: Test
}

trait MacroExtraSpec { self: MacroSpec =>
  import MacroTest._

  "Union types" should {
    "be supported" >> {
      val personDoc = BSONDocument(
        "className" -> "MacroTest.Person",
        "firstName" -> "Foo",
        "lastName" -> "Bar"
      )

      val barDoc = BSONDocument("className" -> "MacroTest.Bar", "name" -> "foo")

      given personWriter: BSONDocumentWriter[Person] = Macros.writer[Person]
      given barWriter: BSONDocumentWriter[Bar] = Macros.writer[Bar]

      "for alias" in {
        type Alias1 = Person | Bar

        val writer: BSONDocumentWriter[Alias1] = Macros.writer[Alias1]

        writer.writeTry(
          Person(firstName = "Foo", lastName = "Bar")
        ) must beSuccessfulTry(personDoc) and {
          writer.writeTry(Bar("foo", None)) must beSuccessfulTry(barDoc)
        }
      }

      "directly" in {
        val writer = Macros.writer[Person | Bar]

        writer.writeTry(
          Person(firstName = "Foo", lastName = "Bar")
        ) must beSuccessfulTry(personDoc) and {
          writer.writeTry(Bar("foo", None)) must beSuccessfulTry(barDoc)
        }
      }
    }

    "not be supported" >> {
      "when not applied on non-class types" in {
        typecheck(
          "type Alias2 = String | Int; Macros.writer[Alias2]"
        ) must failWith(
          "Type\\ MacroExtraSpec\\._Alias2\\ is\\ not\\ a\\ supported\\ union"
        )
      }

      "when includes Int" in {
        typecheck("Macros.writer[Person | Int]") must failWith(
          "Type\\ MacroTest\\.Person\\ \\|\\ scala\\.Int\\ is\\ not\\ a\\ supported\\ union"
        )
      }
    }
  }
}
