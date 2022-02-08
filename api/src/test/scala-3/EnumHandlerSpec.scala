import scala.util.Success

import reactivemongo.api.bson._

import org.specs2.specification.core.Fragments

enum Dummy:
  case A, B, c

/**
 * (Inspired by [[https://github.com/lloydmeta/enumeratum/blob/master/enumeratum-reactivemongo-bson/src/test/scala/enumeratum/EnumBsonHandlerSpec.scala enumeratum]])
 */
final class EnumHandlerSpec extends org.specs2.mutable.Specification {
  "Enum handler".title

  testScenario(
    descriptor = "Normal operation (no transformations)",
    reader = EnumHandler.reader[Dummy],
    expectedReadSuccesses = Map("A" -> Dummy.A, "c" -> Dummy.c),
    expectedReadFails = Seq("C"),
    writer = EnumHandler.writer[Dummy],
    expectedWrites = Map(Dummy.A -> "A", Dummy.c -> "c"),
    handler = EnumHandler.handler[Dummy]
  )

  testKeyScenario(
    descriptor = "normal operation (no transformations)",
    reader = EnumHandler.keyReader[Dummy],
    expectedReadSuccesses = Map("A" -> Dummy.A, "c" -> Dummy.c),
    expectedReadFails = Seq("C"),
    writer = EnumHandler.keyWriter[Dummy],
    expectedWrites = Map(Dummy.A -> "A", Dummy.c -> "c")
  )

  testScenario(
    descriptor = "case insensitive",
    reader = EnumHandler.reader[Dummy](insensitive = true),
    expectedReadSuccesses = Map("A" -> Dummy.A, "a" -> Dummy.A, "C" -> Dummy.c),
    expectedReadFails = Nil,
    writer = EnumHandler.writer[Dummy],
    expectedWrites = Map(Dummy.A -> "A", Dummy.c -> "c"),
    handler = EnumHandler.handler[Dummy](insensitive = true)
  )

  testKeyScenario(
    descriptor = "case insensitive",
    reader = EnumHandler.keyReader[Dummy](insensitive = true),
    expectedReadSuccesses = Map("A" -> Dummy.A, "a" -> Dummy.A, "C" -> Dummy.c),
    expectedReadFails = Nil,
    writer = EnumHandler.keyWriter[Dummy],
    expectedWrites = Map(Dummy.A -> "A", Dummy.c -> "c")
  )

  testScenario(
    descriptor = "lower case transformed",
    reader = EnumHandler.readerLowercaseOnly[Dummy],
    expectedReadSuccesses = Map("a" -> Dummy.A, "b" -> Dummy.B, "c" -> Dummy.c),
    expectedReadFails = Seq("A", "B", "C"),
    writer = EnumHandler.writerLowercase[Dummy],
    expectedWrites = Map(Dummy.A -> "a", Dummy.c -> "c"),
    handler = EnumHandler.handlerLowercaseOnly[Dummy]
  )

  testKeyScenario(
    descriptor = "lower case transformed",
    reader = EnumHandler.keyReaderLowercaseOnly[Dummy],
    expectedReadSuccesses = Map("a" -> Dummy.A, "b" -> Dummy.B, "c" -> Dummy.c),
    expectedReadFails = Seq("A", "B", "C"),
    writer = EnumHandler.keyWriterLowercase[Dummy],
    expectedWrites = Map(Dummy.A -> "a", Dummy.c -> "c")
  )

  testScenario(
    descriptor = "upper case transformed",
    reader = EnumHandler.readerUppercaseOnly[Dummy],
    expectedReadSuccesses = Map("A" -> Dummy.A, "B" -> Dummy.B, "C" -> Dummy.c),
    expectedReadFails = Seq("c"),
    writer = EnumHandler.writerUppercase[Dummy],
    expectedWrites = Map(Dummy.A -> "A", Dummy.c -> "C"),
    handler = EnumHandler.handlerUppercaseOnly[Dummy]
  )

  testKeyScenario(
    descriptor = "upper case transformed",
    reader = EnumHandler.keyReaderUppercaseOnly[Dummy],
    expectedReadSuccesses = Map("A" -> Dummy.A, "B" -> Dummy.B, "C" -> Dummy.c),
    expectedReadFails = Seq("c"),
    writer = EnumHandler.keyWriterUppercase[Dummy],
    expectedWrites = Map(Dummy.A -> "A", Dummy.c -> "C")
  )

  private def testScenario(
      descriptor: String,
      reader: BSONReader[Dummy],
      expectedReadSuccesses: Map[String, Dummy],
      expectedReadFails: Seq[String],
      writer: BSONWriter[Dummy],
      expectedWrites: Map[Dummy, String],
      handler: BSONHandler[Dummy]
    ): Unit = descriptor should {

    val expectedReadErrors = {
      expectedReadFails
        .map(BSONString(_)) ++ Seq(BSONString("D"), BSONInteger(2))
    }

    def readTests(theReader: BSONReader[Dummy]) = {
      "work with valid value" >> {
        Fragments.foreach(expectedReadSuccesses.toSeq) {
          case (k, v) =>
            k in {
              theReader.readOpt(BSONString(k)) must beSome(v)
            }
        }
      }

      "fail with invalid value" >> {
        Fragments.foreach(expectedReadErrors) { v =>
          v.toString in {
            theReader.readOpt(v) must beEmpty
          }
        }
      }
    }

    def writeTests(theWriter: BSONWriter[Dummy]) =
      "write enum value to BSONString" >> {
        Fragments.foreach(expectedWrites.toSeq) {
          case (k, v) =>
            k.toString in {
              theWriter.writeTry(k) must beSuccessfulTry(BSONString(v))
            }
        }
      }

    "BSONReader" >> readTests(reader)

    "BSONWriter" >> writeTests(writer)

    "BSONHandler" >> {
      "reading" >> readTests(handler)

      "writing" >> writeTests(handler)
    }
  }

  private def testKeyScenario(
      descriptor: String,
      reader: KeyReader[Dummy],
      expectedReadSuccesses: Map[String, Dummy],
      expectedReadFails: Seq[String],
      writer: KeyWriter[Dummy],
      expectedWrites: Map[Dummy, String]
    ): Unit = descriptor should {
    def readTests(theReader: KeyReader[Dummy]) = {
      "work with valid key" >> {
        Fragments.foreach(expectedReadSuccesses.toSeq) {
          case (k, v) =>
            k.toString in {
              theReader.readTry(k) must beSuccessfulTry(v)
            }
        }
      }

      "fail with invalid value" >> {
        Fragments.foreach(expectedReadFails) { v =>
          v.toString in {
            theReader.readTry(v) must beFailedTry[Dummy]
          }
        }
      }
    }

    def writeTests(theWriter: KeyWriter[Dummy]) =
      "write enum values to String" >> {
        Fragments.foreach(expectedWrites.toSeq) {
          case (k, v) =>
            k.toString in {
              theWriter.writeTry(k) must beSuccessfulTry(v)
            }
        }
      }

    "KeyReader" >> readTests(reader)

    "KeyWriter" >> writeTests(writer)
  }
}
