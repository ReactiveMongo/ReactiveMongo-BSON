import reactivemongo.api.bson.{
  BSONDocument,
  BSONDocumentHandler,
  BSONDocumentReader,
  BSONDocumentWriter,
  BSONDouble,
  BSONInteger,
  BSONReader,
  BSONWriter,
  BigFat,
  Macros
}
import reactivemongo.api.bson.TestUtils.typecheck

import org.specs2.matcher.TypecheckMatchers._

final class CustomNoProductOf(val name: String, val age: Int)

object CustomNoProductOf {

  given Conversion[CustomNoProductOf, Tuple2[String, Int]] =
    (v: CustomNoProductOf) => v.name -> v.age
}

trait MacroExtraSpec { self: MacroSpec =>
  import MacroTest._
  import MacroExtraTest._

  "Case class" should {
    "not be handled without custom ProductOf" in {
      typecheck("Macros.writer[CustomNoProductOf]") must failWith(
        ".*ProductOf\\[CustomNoProductOf\\]"
      )
    }

    "be handled with more than 22 fields" in {
      val handler = Macros.handler[BigFat]

      handler.writeTry(BigFat.example).foreach { x =>
        println(BSONDocument pretty x)
      }

      handler.writeTry(BigFat.example) must beSuccessfulTry(
        BSONDocument(
          "a" -> 1,
          "b" -> 2D,
          "c" -> 3F,
          "d" -> "d",
          "e" -> Seq(1, 2, 3),
          "f" -> 6,
          "g" -> 7D,
          "h" -> 8F,
          "i" -> "i",
          "j" -> Seq(4, 5),
          "k" -> 10,
          "l" -> 11D,
          "m" -> 12F,
          "n" -> "n",
          "o" -> Seq(6, 7),
          "p" -> 13,
          "q" -> 14D,
          "r" -> 15F,
          "s" -> "s",
          "t" -> Seq(8),
          "u" -> 16F,
          "v" -> "v",
          "w" -> Seq(9, 10, 11),
          "x" -> 12,
          "y" -> Seq(13, 14),
          "z" -> 15D
        )
      )
    }
  }

  "Union types" should {
    "be supported" >> {
      val person = Person(firstName = "Foo", lastName = "Bar")
      val personDoc = BSONDocument(
        "className" -> "MacroTest.Person",
        "firstName" -> "Foo",
        "lastName" -> "Bar"
      )

      val bar = Bar("foo", None)
      val barDoc = BSONDocument("className" -> "MacroTest.Bar", "name" -> "foo")

      given personHandler: BSONDocumentHandler[Person] = Macros.handler[Person]
      given barHandler: BSONDocumentHandler[Bar] = Macros.handler[Bar]

      "for alias" in {
        type Alias1 = Person | Bar

        val writer: BSONDocumentWriter[Alias1] = Macros.writer[Alias1]
        val reader: BSONDocumentReader[Alias1] = Macros.reader[Alias1]
        val handler: BSONDocumentHandler[Alias1] = Macros.handler[Alias1]

        writer.writeTry(person) must beSuccessfulTry(personDoc) and {
          reader.readTry(personDoc) must beSuccessfulTry(person)
        } and {
          handler.writeTry(person) must beSuccessfulTry(personDoc)
        } and {
          handler.readTry(personDoc) must beSuccessfulTry(person)
        } and {
          writer.writeTry(bar) must beSuccessfulTry(barDoc)
        } and {
          reader.readTry(barDoc) must beSuccessfulTry(bar)
        } and {
          handler.writeTry(bar) must beSuccessfulTry(barDoc)
        } and {
          handler.readTry(barDoc) must beSuccessfulTry(bar)
        }
      }

      "directly" in {
        val writer = Macros.writer[Person | Bar]
        val reader = Macros.reader[Person | Bar]
        val handler = Macros.handler[Person | Bar]

        writer.writeTry(person) must beSuccessfulTry(personDoc) and {
          reader.readTry(personDoc) must beSuccessfulTry(person)
        } and {
          handler.writeTry(person) must beSuccessfulTry(personDoc)
        } and {
          handler.readTry(personDoc) must beSuccessfulTry(person)
        } and {
          writer.writeTry(bar) must beSuccessfulTry(barDoc)
        } and {
          reader.readTry(barDoc) must beSuccessfulTry(bar)
        } and {
          handler.writeTry(bar) must beSuccessfulTry(barDoc)
        } and {
          handler.readTry(barDoc) must beSuccessfulTry(bar)
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
      val reader = Macros.valueReader[Logarithm]
      val handler = Macros.valueHandler[Logarithm]

      reader.readTry(BSONDouble(0.12D)) must beSuccessfulTry(
        Logarithm(0.12D)
      ) and {
        reader.readOpt(BSONDouble(4.5D)) must beSome(Logarithm(4.5D))
      } and {
        handler.readTry(BSONDouble(0.12D)) must beSuccessfulTry(
          Logarithm(0.12D)
        )
      } and {
        handler.readOpt(BSONDouble(4.5D)) must beSome(Logarithm(4.5D))
      } and {
        writer.writeTry(Logarithm(1.2D)) must beSuccessfulTry(
          BSONDouble(1.2D)
        )
      } and {
        writer.writeOpt(Logarithm(23.4D)) must beSome(BSONDouble(23.4D))
      } and {
        handler.writeTry(Logarithm(1.2D)) must beSuccessfulTry(
          BSONDouble(1.2D)
        )
      } and {
        handler.writeOpt(Logarithm(23.4D)) must beSome(BSONDouble(23.4D))
      }
    }

    "be supported for custom Value class" in {
      given innerWriter: BSONWriter[FooVal] = Macros.valueWriter
      val writer = Macros.valueWriter[OpaqueFoo]

      given innerReader: BSONReader[FooVal] = Macros.valueReader
      val reader = Macros.valueReader[OpaqueFoo]

      val handler = Macros.valueHandler[OpaqueFoo]

      val one = OpaqueFoo(new FooVal(1))
      val two = OpaqueFoo(new FooVal(2))

      reader.readTry(BSONInteger(1)) must beSuccessfulTry(one) and {
        reader.readOpt(BSONInteger(2)) must beSome(two)
      } and {
        handler.readTry(BSONInteger(1)) must beSuccessfulTry(one)
      } and {
        handler.readOpt(BSONInteger(2)) must beSome(two)
      } and {
        writer.writeTry(one) must beSuccessfulTry(BSONInteger(1))
      } and {
        writer.writeOpt(two) must beSome(BSONInteger(2))
      } and {
        handler.writeTry(one) must beSuccessfulTry(BSONInteger(1))
      } and {
        handler.writeOpt(two) must beSome(BSONInteger(2))
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
