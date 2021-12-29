package reactivemongo
package api.bson

import reactivemongo.api.bson.buffer.{ DefaultBufferHandler, WritableBuffer }

import org.specs2.specification.core.Fragments

import DefaultBufferHandler._

final class EqualitySpec extends org.specs2.mutable.Specification {
  "Equality".title

  section("unit")

  "BSONBinary" should {
    def bin() = BSONBinary(Array[Byte](1, 2, 3), Subtype.GenericBinarySubtype)

    "permit equality to work" in {
      bin() must_=== bin()
    }

    "retain equality through serialization/deserialization" in {
      val expected = bin()
      val writeBuffer = WritableBuffer.empty
      writeBinary(expected, writeBuffer)

      expected must_=== readBinary(writeBuffer.toReadableBuffer())
    }
  }

  "BSONObjectID" should {
    def oid() = BSONObjectID.parse("0102030405060708090a0b0c").get
    "permit equality to work" in {
      oid() must_=== oid()
    }

    "retain equality through serialization/deserialization" in {
      val boid1 = oid()
      val writeBuffer = WritableBuffer.empty
      writeObjectID(boid1, writeBuffer)

      boid1 must_=== readObjectID(writeBuffer.toReadableBuffer())
    }
  }

  "BSONArray" should {
    "permit equality to work" in {
      val ba1 = BSONArray(
        Seq(
          BSONInteger(42),
          BSONString("42"),
          BSONDouble(42.0),
          BSONDateTime(0)
        )
      )

      ba1 must_=== ba1.copy()
    }

    "retain equality through serialization/deserialization" in {
      val ba1 = BSONArray(
        Seq(
          BSONInteger(42),
          BSONString("42"),
          BSONDouble(42.0),
          BSONDateTime(0)
        )
      )

      val writeBuffer = WritableBuffer.empty
      writeArray(ba1.values, writeBuffer)

      val input = writeBuffer.toReadableBuffer()

      input.size must_=== writeBuffer.size() and {
        readArray(input) must_=== ba1
      }
    }
  }

  "BSONDocument" should {
    "retain equality through serialization/deserialization" in {
      val b1 = BSONDocument(
        Seq(
          "boolean" -> BSONBoolean(value = true),
          "int" -> BSONInteger(42),
          "long" -> BSONLong(42L),
          "double" -> BSONDouble(42.0),
          "string" -> BSONString("forty-two"),
          "datetime" -> BSONDateTime(System.currentTimeMillis()),
          "timestamp" -> BSONTimestamp(System.currentTimeMillis()),
          "binary" -> BSONBinary(
            Array[Byte](1, 2, 3),
            Subtype.GenericBinarySubtype
          ),
          "objectid" -> BSONObjectID
            .parse(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12))
            .get,
          "array" -> BSONArray(
            Seq(
              BSONInteger(42),
              BSONString("42"),
              BSONDouble(42.0),
              BSONDateTime(0)
            )
          )
        )
      )

      val writeBuffer = WritableBuffer.empty
      writeDocument(b1, writeBuffer)

      readDocument(writeBuffer.toReadableBuffer()) must_=== b1
    }
  }

  "BSONBooleanLike" should {
    Fragments.foreach[BSONValue](
      BSONValueFixtures.bsonIntFixtures ++
        BSONValueFixtures.bsonDoubleFixtures ++
        BSONValueFixtures.bsonLongFixtures ++
        BSONValueFixtures.bsonBoolFixtures ++
        BSONValueFixtures.bsonDecimalFixtures ++
        Seq(BSONNull, BSONUndefined)
    ) { v =>
      s"retain equality through handler for $v" in {
        BSON
          .read[BSONBooleanLike](v) must beSuccessfulTry[BSONBooleanLike].like {
          case r1 => BSON.read[BSONBooleanLike](v) must beSuccessfulTry(r1)
        }
      }
    }
  }

  "BSONNumberLike" should {
    Fragments.foreach[BSONValue](
      BSONValueFixtures.bsonIntFixtures ++
        BSONValueFixtures.bsonDoubleFixtures ++
        BSONValueFixtures.bsonLongFixtures ++
        BSONValueFixtures.bsonDateTimeFixtures ++
        BSONValueFixtures.bsonTsFixtures ++
        BSONValueFixtures.bsonDecimalFixtures
    ) { v =>
      s"retain equality through handler for $v" in {
        BSON.read[BSONNumberLike](v) must beSuccessfulTry[BSONNumberLike].like {
          case r1 => BSON.read[BSONNumberLike](v) must beSuccessfulTry(r1)
        }
      }
    }
  }

  section("unit")
}
