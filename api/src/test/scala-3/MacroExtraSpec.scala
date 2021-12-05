import reactivemongo.api.bson.{
  BSONDocument,
  BSONDocumentWriter,
  BSONDouble,
  BSONInteger,
  BSONWriter,
  Macros
}

import reactivemongo.api.bson.TestUtils.typecheck

import org.specs2.matcher.TypecheckMatchers._

trait MacroExtraSpec { self: MacroSpec =>
  import MacroTest._
  import MacroExtraTest._

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

  "Opaque type aliases" should {
    "be supported for Double" in {
      val writer = Macros.valueWriter[Logarithm]

      writer.writeTry(Logarithm(1.2D)) must beSuccessfulTry(
        BSONDouble(1.2D)
      ) and {
        writer.writeOpt(Logarithm(23.4D)) must beSome(BSONDouble(23.4D))
      }
    }

    "be supported for custom Value class" in {
      given innerWriter: BSONWriter[FooVal] = Macros.valueWriter
      val writer = Macros.valueWriter[OpaqueFoo]

      val one = OpaqueFoo(new FooVal(1))
      val two = OpaqueFoo(new FooVal(2))

      writer.writeTry(one) must beSuccessfulTry(BSONInteger(1)) and {
        writer.writeOpt(two) must beSome(BSONInteger(2))
      }
    }
  }
}

object MacroExtraTest:
  import MacroTest._

  opaque type Logarithm = Double

  object Logarithm {
    def apply(value: Double): Logarithm = value
  }

  opaque type OpaqueFoo = FooVal

  object OpaqueFoo {
    def apply(value: FooVal): OpaqueFoo = value
  }
end MacroExtraTest
