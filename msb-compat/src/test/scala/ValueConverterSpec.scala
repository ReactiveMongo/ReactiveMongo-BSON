package reactivemongo

import reactivemongo.api.bson.{
  BSONArray,
  BSONBinary,
  BSONBoolean,
  BSONDateTime,
  BSONDecimal,
  BSONDocument,
  BSONDouble,
  BSONInteger,
  BSONJavaScript,
  BSONJavaScriptWS,
  BSONLong,
  BSONMaxKey,
  BSONMinKey,
  BSONNull,
  BSONObjectID,
  BSONRegex,
  BSONString,
  BSONSymbol,
  BSONTimestamp,
  BSONUndefined,
  BSONValue,
  Subtype
}

import org.specs2.specification.core.Fragment

import org.bson.{
  BsonArray,
  BsonBinary,
  BsonBinarySubType,
  BsonBoolean,
  BsonDateTime,
  BsonDecimal128,
  BsonDocument,
  BsonDouble,
  BsonInt32,
  BsonInt64,
  BsonJavaScript,
  BsonJavaScriptWithScope,
  BsonMaxKey,
  BsonMinKey,
  BsonNull,
  BsonObjectId,
  BsonRegularExpression,
  BsonString,
  BsonSymbol,
  BsonTimestamp,
  BsonUndefined,
  BsonValue
}
import org.bson.types.Decimal128

final class ValueConverterSpec
    extends org.specs2.mutable.Specification
    with ConverterFixtures {

  "Value converters" title

  import reactivemongo.api.bson.msb._

  "Scalar value converters" should {
    "support binary subtype" >> {
      Fragment.foreach(
        Seq[(BsonBinarySubType, Subtype)](
          BsonBinarySubType.BINARY -> Subtype.GenericBinarySubtype,
          BsonBinarySubType.FUNCTION -> Subtype.FunctionSubtype,
          BsonBinarySubType.OLD_BINARY -> Subtype.OldBinarySubtype,
          BsonBinarySubType.UUID_LEGACY -> Subtype.OldUuidSubtype,
          BsonBinarySubType.UUID_STANDARD -> Subtype.UuidSubtype,
          BsonBinarySubType.MD5 -> Subtype.Md5Subtype,
          BsonBinarySubType.USER_DEFINED -> Subtype.UserDefinedSubtype
        )
      ) {
        case (l, n) =>
          s"from org.bson $l" in {
            implicitly[Subtype](l) must_=== n
          }

          s"to org.bson $n" in {
            implicitly[BsonBinarySubType](n) must_=== l
          }
      }
    }

    "support binary" >> {
      val bytes = "Test".getBytes("UTF-8")

      Fragment.foreach(
        Seq[(BsonBinary, BSONBinary)](
          new BsonBinary(
            BsonBinarySubType.UUID_STANDARD,
            uuidBytes
          ) -> BSONBinary(uuid),
          new BsonBinary(BsonBinarySubType.BINARY, bytes) -> BSONBinary(
            bytes,
            Subtype.GenericBinarySubtype
          )
        )
      ) {
        case (l, n) =>
          s"from org.bson $l" in {
            implicitly[BSONBinary](l) must_=== n
          }

          s"to org.bson $n" in {
            implicitly[BsonBinary](n) must_=== l
          }
      }
    }

    "support boolean" >> {
      "from org.bson" in {
        implicitly[BSONBoolean](BsonBoolean.FALSE) must_=== BSONBoolean(false)
      }

      "to org.bson" in {
        implicitly[BsonBoolean](BSONBoolean(true)) must_=== BsonBoolean.TRUE
      }
    }

    "support date/time" >> {
      "from org.bson" in {
        implicitly[BSONDateTime](ldt) must_=== bdt
      }

      "to org.bson" in {
        implicitly[BsonDateTime](bdt) must_=== ldt
      }
    }

    "support decimal" >> {
      "from org.bson" in {
        implicitly[BSONDecimal](
          new BsonDecimal128(Decimal128.POSITIVE_INFINITY)
        ) must_=== BSONDecimal.PositiveInf
      }

      "to org.bson" in {
        implicitly[BsonDecimal128](
          BSONDecimal.PositiveInf
        ) must_=== (new BsonDecimal128(Decimal128.POSITIVE_INFINITY))
      }
    }

    "support double" >> {
      val raw = 1.23D

      "from org.bson" in {
        implicitly[BSONDouble](new BsonDouble(raw)) must_=== BSONDouble(raw)
      }

      "to org.bson" in {
        implicitly[BsonDouble](BSONDouble(raw)) must_=== new BsonDouble(raw)
      }
    }

    "support integer" >> {
      "from org.bson" in {
        implicitly[BSONInteger](new BsonInt32(1)) must_=== BSONInteger(1)
      }

      "to org.bson" in {
        implicitly[BsonInt32](BSONInteger(2)) must_=== new BsonInt32(2)
      }
    }

    "support JavaScript" >> {
      val raw = "foo()"

      "from org.bson" in {
        implicitly[BSONJavaScript](
          new BsonJavaScript(raw)
        ) must_=== BSONJavaScript(raw)
      }

      "to org.bson" in {
        implicitly[BsonJavaScript](
          BSONJavaScript(raw)
        ) must_=== new BsonJavaScript(raw)
      }
    }

    "support JavaScript/WS" >> {
      val raw = "bar(lorem)"
      val scope: BsonDocument = {
        val doc = new BsonDocument()
        doc.append("lorem", new BsonString("ipsum"))
      }

      "from org.bson" in {
        implicitly[BSONJavaScriptWS](
          new BsonJavaScriptWithScope(raw, scope)
        ) must_=== BSONJavaScriptWS(raw, BSONDocument("lorem" -> "ipsum"))
      }

      "to org.bson" in {
        implicitly[BsonJavaScriptWithScope](
          BSONJavaScriptWS(raw, BSONDocument("lorem" -> "ipsum"))
        ) must_=== new BsonJavaScriptWithScope(raw, scope)
      }
    }

    "support long" >> {
      "from org.bson" in {
        implicitly[BSONLong](new BsonInt64(1L)) must_=== BSONLong(1L)
      }

      "to org.bson" in {
        implicitly[BsonInt64](BSONLong(2L)) must_=== new BsonInt64(2L)
      }
    }

    "support null" >> {
      "from org.bson" in {
        implicitly[BSONNull](new BsonNull) must_=== BSONNull
      }

      "to org.bson" in {
        implicitly[BsonNull](BSONNull) must_=== new BsonNull
      }
    }

    "support maxKey" >> {
      "from org.bson" in {
        implicitly[BSONMaxKey](new BsonMaxKey) must_=== BSONMaxKey
      }

      "to org.bson" in {
        implicitly[BsonMaxKey](BSONMaxKey) must_=== new BsonMaxKey
      }
    }

    "support minKey" >> {
      "from org.bson" in {
        implicitly[BSONMinKey](new BsonMinKey) must_=== BSONMinKey
      }

      "to org.bson" in {
        implicitly[BsonMinKey](BSONMinKey) must_=== new BsonMinKey
      }
    }

    "support object ID" >> {
      "from org.bson" in {
        implicitly[BSONObjectID](loid) must_=== boid
      }

      "to org.bson" in {
        implicitly[BsonObjectId](boid) must_=== loid
      }
    }

    "support string" >> {
      val raw = "Foo"

      "from org.bson" in {
        implicitly[BSONString](new BsonString(raw)) must_=== BSONString(raw)
      }

      "to org.bson" in {
        implicitly[BsonString](BSONString(raw)) must_=== new BsonString(raw)
      }
    }

    "support symbol" >> {
      val raw = "Foo"

      "from org.bson" in {
        implicitly[BSONSymbol](new BsonSymbol(raw)) must_=== BSONSymbol(raw)
      }

      "to org.bson" in {
        implicitly[BsonSymbol](BSONSymbol(raw)) must_=== new BsonSymbol(raw)
      }
    }

    "support timestamp" >> {
      "from org.bson" in {
        implicitly[BSONTimestamp](lts) must_=== bts
      }

      "to org.bson" in {
        implicitly[BsonTimestamp](bts) must_=== lts
      }
    }

    "support regexp" >> {
      "from org.bson" in {
        implicitly[BSONRegex](lre) must_=== bre
      }

      "to org.bson" in {
        implicitly[BsonRegularExpression](bre) must_=== lre
      }
    }

    "support undefined" >> {
      "from org.bson" in {
        implicitly[BSONUndefined](new BsonUndefined) must_=== BSONUndefined
      }

      "to org.bson" in {
        implicitly[BsonUndefined](BSONUndefined) must_=== new BsonUndefined
      }
    }
  }

  "Non-scalar value converters" should {
    "support array" >> {
      "from org.bson" in {
        implicitly[BSONArray](larr) must_=== barr
      }

      "to org.bson" in {
        implicitly[BsonArray](barr) must_=== larr
      }
    }

    "support document" >> {
      "from org.bson" in {
        implicitly[BSONDocument](ldoc) must_=== bdoc
      }

      "to org.bson" in {
        implicitly[BsonDocument](bdoc) must_=== ldoc
      }
    }
  }

  "Opaque values" should {
    Fragment.foreach(fixtures) {
      case (org, bson) =>
        s"from org.bson $org" in {
          implicitly[BSONValue](org) must_=== bson
        }

        s"$bson to org.bson" in {
          implicitly[BsonValue](bson) must_=== org
        }
    }
  }
}
