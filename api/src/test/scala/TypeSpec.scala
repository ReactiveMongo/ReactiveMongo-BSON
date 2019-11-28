import reactivemongo.api.bson._

import scala.util.{ Failure, Success }

import reactivemongo.BSONValueFixtures

final class TypeSpec extends org.specs2.mutable.Specification {
  "BSON types" title

  implicit def bsonValue[T](value: T)(implicit writer: BSONWriter[T]): BSONValue = writer.writeTry(value) match {
    case Success(bson) => bson
    case Failure(cause) => throw cause
  }

  "BSON document" should {
    "be empty" in {
      BSONDocument().elements must beEmpty and (
        BSONDocument.empty.elements must beEmpty) and (
          document.elements must beEmpty) and (
            document().elements must beEmpty) and (
              BSONDocument.empty.contains("foo") must beFalse)
    }

    "be created with a new element " in {
      val doc = BSONDocument.empty ++ ("foo" -> 1)

      doc must_=== BSONDocument("foo" -> 1) and (
        doc.contains("foo") must beTrue)
    }

    "remove specified elements" in {
      val doc = BSONDocument("Foo" -> 1, "Bar" -> 2, "Lorem" -> 3)

      (doc -- ("Bar", "Lorem") must_=== BSONDocument("Foo" -> 1)) and
        (doc -- ("Foo", "Bar") must_=== BSONDocument("Lorem" -> 3)) and
        (doc -- ("Bar") contains ("Foo") must beTrue)
    }
  }

  "BSON array" should {
    "be empty" in {
      BSONArray().values must beEmpty and (
        BSONArray.empty.values must beEmpty) and (
          array.values must beEmpty) and (
            array().values must beEmpty)

    }

    "be created with a new element " in {
      BSONArray.empty.++("foo", "bar") must_=== BSONArray("foo", "bar")
    }

    "be returned with a added element" in {
      BSONArray("foo").++(BSONString("bar")) must_=== BSONArray("foo", "bar")
    }

    "support optional values" in {
      BSONArray(
        Option.empty[String], // should be skipped
        BSONBoolean(true),
        BSONString("foo"),
        None // should be skipped
      ).values must contain(
          exactly(BSONBoolean(true), BSONString("foo")).inOrder)
    }

    "be pretty-printed" in {
      BSONValueFixtures.bsonArrayFixtures.headOption.
        map(BSONArray.pretty(_)).mkString must_=== """[
  0.0,
  -2.0,
  12.34
]"""
    }
  }

  "BSON binary/blob" should {
    "be read as byte array" in {
      val bytes = Array[Byte](1, 2, 3)
      val bson = BSONBinary(bytes, Subtype.GenericBinarySubtype)

      bson.byteArray aka "read #1" must_=== bytes and (
        bson.byteArray aka "read #2" must_=== bytes)
    }

    "be created from UUID" in {
      val uuid = java.util.UUID.fromString(
        "b32e4733-0679-4dd3-9978-230e70b55dce")

      val expectedBytes = Array[Byte](
        -77, 46, 71, 51, 6, 121, 77, -45, -103, 120, 35, 14, 112, -75, 93, -50)

      BSONBinary(uuid) must_=== BSONBinary(expectedBytes, Subtype.UuidSubtype)
    }

    "be pretty-printed" in {
      BSONBinary.pretty(BSONBinary(
        Array(4, 5, 6), Subtype.GenericBinarySubtype)).
        aka("pretty") must_=== "BinData(0, 'BAUG')"
    }
  }

  "BSON object ID" should {
    "be pretty-printed" in {
      val oid = BSONObjectID.generate()

      BSONObjectID.pretty(oid) must_=== s"ObjectId('${oid.stringify}')"
    }
  }

  "BSON string" should {
    "be pretty-printed" in {
      BSONString.pretty(BSONString(
        "foo 'bar'")) must_=== "'foo \\'bar\\''"
    }
  }

  /*
  "BSON timestamp" should {
    val timeMs = 1574884443000L
    val timeSec = 366
    val ordinal = -1368554632

    "extract time and ordinal values" in {
      val ts = BSONTimestamp(timeMs)

      ts.value aka "raw value" must_=== timeMs and {
        ts.time aka "time" must_=== timeSec
      } and {
        ts.ordinal aka "ordinal" must_=== ordinal
      }
    }

    "be created from the time and ordinal values" in {
      BSONTimestamp(timeSec, ordinal) must_=== BSONTimestamp(timeMs)
    }
  }
   */
}
