package reactivemongo

import java.lang.{ Boolean => JBool }

import reactivemongo.api.bson.{ `null`, maxKey, minKey, undefined, BSONArray, BSONBinary, BSONBoolean, BSONDateTime, BSONDecimal, BSONDocument, BSONDouble, BSONHandler, BSONInteger, BSONJavaScript, BSONJavaScriptWS, BSONLong, BSONMaxKey, BSONMinKey, BSONNull, BSONObjectID, BSONRegex, BSONString, BSONSymbol, BSONTimestamp, BSONUndefined, BSONValue, BSONWriter }
import reactivemongo.api.bson.msb.HandlerConverters

import org.specs2.specification.core.Fragment

import org.bson.{ BsonArray, BsonBinary, BsonBinarySubType, BsonBoolean, BsonDateTime, BsonDecimal128, BsonDocument, BsonDouble, BsonInt32, BsonInt64, BsonJavaScript, BsonJavaScriptWithScope, BsonMaxKey, BsonMinKey, BsonNull, BsonObjectId, BsonRegularExpression, BsonString, BsonSymbol, BsonTimestamp, BsonUndefined, BsonValue }
import org.bson.codecs._
import org.bson.types.{ Binary, Decimal128 }

private[reactivemongo] trait EncoderConverterSpec {
  specs: HandlerConverterSpec =>

  import HandlerConverters._

  "Encoder" should {
    import HandlerConverters.encode

    "write BsonBinary" >> {
      val bytes = "Test".getBytes("UTF-8")
      val enc = new org.bson.codecs.BinaryCodec

      Fragment.foreach(Seq[(BsonBinarySubType, Array[Byte])](
        BsonBinarySubType.UUID_STANDARD -> uuidBytes,
        BsonBinarySubType.BINARY -> bytes)) {
        case (subtpe, data) =>
          s"from $subtpe(sz=${data.size})" in {
            encode(new Binary(subtpe, data), enc).
              aka("encoded") must beSuccessfulTry(new BsonBinary(subtpe, data))
          }

      }
    }

    "write BsonBoolean" in {
      val enc = new org.bson.codecs.BooleanCodec

      encode(JBool.TRUE, enc) must beSuccessfulTry(BsonBoolean.TRUE) and {
        encode(JBool.FALSE, enc) must beSuccessfulTry(BsonBoolean.FALSE)
      }
    }

    "write BsonArray" in {
      val enc = new org.bson.codecs.BsonArrayCodec

      encode(larr, enc) must beSuccessfulTry(larr)
    }

    "write BsonDocument" in {
      val enc = new org.bson.codecs.BsonDocumentCodec

      encode(ldoc, enc) must beSuccessfulTry(ldoc)
    }

    "write opaque BsonValue" should {
      val enc = new org.bson.codecs.BsonValueCodec

      Fragment.foreach(fixtures.map(_._1)) { v =>
        s"from $v" in {
          encode(v, enc) must beSuccessfulTry(v)
        }
      }
    }

    "be converted as BSONWriter" >> {
      "for BsonArray" in {
        // explicit type ascription as Encoder to be sure which conversion
        val codec = new org.bson.codecs.BsonArrayCodec
        val enc: Encoder[BsonArray] = codec

        val writer: BSONWriter[BsonArray] = enc
        val handler: BSONHandler[BsonArray] = codec

        writer.writeTry(larr) must beSuccessfulTry[BSONValue](barr) and {
          handler.writeTry(larr) must beSuccessfulTry[BSONValue](barr)
        }
      }

      "for BsonBinary" in {
        val codec = new org.bson.codecs.BsonBinaryCodec
        val enc: Encoder[BsonBinary] = codec

        val writer: BSONWriter[BsonBinary] = enc
        val handler: BSONHandler[BsonBinary] = codec

        val bin = new BsonBinary(BsonBinarySubType.UUID_STANDARD, uuidBytes)

        writer.writeTry(bin) must beSuccessfulTry(BSONBinary(uuid)) and {
          handler.writeTry(bin) must beSuccessfulTry(BSONBinary(uuid))
        }
      }

      "for BsonBoolean" in {
        val codec = new org.bson.codecs.BsonBooleanCodec
        val enc: Encoder[BsonBoolean] = codec

        val writer: BSONWriter[BsonBoolean] = enc
        val handler: BSONHandler[BsonBoolean] = codec

        writer.writeTry(BsonBoolean.TRUE).
          aka("true1") must beSuccessfulTry(BSONBoolean(true)) and {
            writer.writeTry(BsonBoolean.FALSE).
              aka("false1") must beSuccessfulTry(BSONBoolean(false))
          } and {
            handler.writeTry(BsonBoolean.FALSE).
              aka("false2") must beSuccessfulTry(BSONBoolean(false))
          } and {
            handler.writeTry(BsonBoolean.TRUE).
              aka("true2") must beSuccessfulTry(BSONBoolean(true))
          }
      }

      "for BsonDateTime" in {
        val codec = new org.bson.codecs.BsonDateTimeCodec
        val enc: Encoder[BsonDateTime] = codec

        val writer: BSONWriter[BsonDateTime] = enc
        val handler: BSONHandler[BsonDateTime] = codec

        writer.writeTry(ldt) must beSuccessfulTry[BSONValue](bdt) and {
          handler.writeTry(ldt) must beSuccessfulTry[BSONValue](bdt)
        }
      }

      "for BsonDecimal128" in {
        val codec = new org.bson.codecs.BsonDecimal128Codec
        val enc: Encoder[BsonDecimal128] = codec

        val writer: BSONWriter[BsonDecimal128] = enc
        val handler: BSONHandler[BsonDecimal128] = codec

        val dec = new BsonDecimal128(Decimal128.POSITIVE_INFINITY)

        writer.writeTry(dec) must beSuccessfulTry(BSONDecimal.PositiveInf) and {
          handler.writeTry(dec) must beSuccessfulTry(BSONDecimal.PositiveInf)
        }
      }

      "for BsonDocument" in {
        val codec = new org.bson.codecs.BsonDocumentCodec
        val enc: Encoder[BsonDocument] = codec

        val writer: BSONWriter[BsonDocument] = enc
        val handler: BSONWriter[BsonDocument] = codec

        writer.writeTry(ldoc) must beSuccessfulTry(bdoc) and {
          handler.writeTry(ldoc) must beSuccessfulTry(bdoc)
        }
      }

      "for BsonDouble" in {
        val codec = new org.bson.codecs.BsonDoubleCodec
        val enc: Encoder[BsonDouble] = codec

        val writer: BSONWriter[BsonDouble] = enc
        val handler: BSONWriter[BsonDouble] = codec

        val raw = 3.4D

        writer.writeTry(new BsonDouble(raw)).
          aka("double1") must beSuccessfulTry(BSONDouble(raw)) and {
            handler.writeTry(new BsonDouble(raw)).
              aka("double2") must beSuccessfulTry(BSONDouble(raw))
          }
      }

      "for BsonInt32" in {
        val codec = new org.bson.codecs.BsonInt32Codec
        val enc: Encoder[BsonInt32] = codec

        val writer: BSONWriter[BsonInt32] = enc
        val handler: BSONHandler[BsonInt32] = codec

        val raw = 45

        writer.writeTry(new BsonInt32(raw)).
          aka("BSONValue1") must beSuccessfulTry(BSONInteger(raw)) and {
            handler.writeTry(new BsonInt32(raw)).
              aka("BSONValue2") must beSuccessfulTry(BSONInteger(raw))
          }
      }

      "for BsonInt64" in {
        val codec = new org.bson.codecs.BsonInt64Codec
        val enc: Encoder[BsonInt64] = codec

        val writer: BSONWriter[BsonInt64] = enc
        val handler: BSONHandler[BsonInt64] = codec

        val raw = 678L

        writer.writeTry(new BsonInt64(raw)).
          aka("BSONLong1") must beSuccessfulTry(BSONLong(raw)) and {
            handler.writeTry(new BsonInt64(raw)).
              aka("BSONLong2") must beSuccessfulTry(BSONLong(raw))
          }
      }

      "for BsonJavaScript" in {
        val codec = new org.bson.codecs.BsonJavaScriptCodec
        val enc: Encoder[BsonJavaScript] = codec

        val writer: BSONWriter[BsonJavaScript] = enc
        val handler: BSONHandler[BsonJavaScript] = codec

        val raw = "foo()"

        writer.writeTry(new BsonJavaScript(raw)).
          aka("BSONJavaScript1") must beSuccessfulTry(BSONJavaScript(raw)) and {
            handler.writeTry(new BsonJavaScript(raw)).
              aka("BSONJavaScript2") must beSuccessfulTry(BSONJavaScript(raw))
          }
      }

      "for BsonJavaScriptWithScope" in {
        val codec = new org.bson.codecs.BsonJavaScriptWithScopeCodec(
          new org.bson.codecs.BsonDocumentCodec)

        val enc: Encoder[BsonJavaScriptWithScope] = codec

        val writer: BSONWriter[BsonJavaScriptWithScope] = enc
        val handler: BSONHandler[BsonJavaScriptWithScope] = codec

        val code = "bar(this.lorem)"
        val scope = new BsonDocument().append("lorem", new BsonInt64(2L))

        writer.writeTry(new BsonJavaScriptWithScope(code, scope)).
          aka("BSONJavaScriptWS1") must beSuccessfulTry(
            BSONJavaScriptWS(code, BSONDocument("lorem" -> 2L))) and {
              handler.writeTry(new BsonJavaScriptWithScope(code, scope)).
                aka("BSONJavaScriptWS2") must beSuccessfulTry(
                  BSONJavaScriptWS(code, BSONDocument("lorem" -> 2L)))
            }
      }

      "for BsonMaxKey" in {
        val codec = new org.bson.codecs.BsonMaxKeyCodec
        val enc: Encoder[BsonMaxKey] = codec

        val writer: BSONWriter[BsonMaxKey] = enc
        val handler: BSONHandler[BsonMaxKey] = codec

        writer.writeTry(new BsonMaxKey) must beSuccessfulTry(maxKey) and {
          handler.writeTry(new BsonMaxKey) must beSuccessfulTry(maxKey)
        }
      }

      "for BsonMinKey" in {
        val codec = new org.bson.codecs.BsonMinKeyCodec
        val enc: Encoder[BsonMinKey] = codec

        val writer: BSONWriter[BsonMinKey] = enc
        val handler: BSONHandler[BsonMinKey] = codec

        writer.writeTry(new BsonMinKey) must beSuccessfulTry(minKey) and {
          handler.writeTry(new BsonMinKey) must beSuccessfulTry(minKey)
        }
      }

      "for BsonNull" in {
        val codec = new org.bson.codecs.BsonNullCodec
        val enc: Encoder[BsonNull] = codec

        val writer: BSONWriter[BsonNull] = enc
        val handler: BSONHandler[BsonNull] = codec

        writer.writeTry(new BsonNull) must beSuccessfulTry(`null`) and {
          handler.writeTry(new BsonNull) must beSuccessfulTry(`null`)
        }
      }

      "for BsonObjectId" in {
        val codec = new org.bson.codecs.BsonObjectIdCodec
        val enc: Encoder[BsonObjectId] = codec

        val writer: BSONWriter[BsonObjectId] = enc
        val handler: BSONHandler[BsonObjectId] = codec

        writer.writeTry(loid) must beSuccessfulTry[BSONValue](boid) and {
          handler.writeTry(loid) must beSuccessfulTry[BSONValue](boid)
        }
      }

      "for BsonRegularExpression" in {
        val codec = new org.bson.codecs.BsonRegularExpressionCodec
        val enc: Encoder[BsonRegularExpression] = codec

        val writer: BSONWriter[BsonRegularExpression] = enc
        val handler: BSONHandler[BsonRegularExpression] = codec

        writer.writeTry(lre) must beSuccessfulTry[BSONValue](bre) and {
          handler.writeTry(lre) must beSuccessfulTry[BSONValue](bre)
        }
      }

      "for BsonSymbol" in {
        val codec = new org.bson.codecs.BsonSymbolCodec
        val enc: Encoder[BsonSymbol] = codec

        val writer: BSONWriter[BsonSymbol] = enc
        val handler: BSONHandler[BsonSymbol] = codec

        writer.writeTry(
          new BsonSymbol("sym1")) must beSuccessfulTry[BSONValue](
            BSONSymbol("sym1")) and {
              handler.writeTry(
                new BsonSymbol("sym2")) must beSuccessfulTry[BSONValue](
                  BSONSymbol("sym2"))
            }
      }

      "for BsonString" in {
        val codec = new org.bson.codecs.BsonStringCodec
        val enc: Encoder[BsonString] = codec

        val writer: BSONWriter[BsonString] = enc
        val handler: BSONHandler[BsonString] = codec

        writer.writeTry(
          new BsonString("str1")) must beSuccessfulTry[BSONValue](
            BSONString("str1")) and {
              handler.writeTry(
                new BsonString("str2")) must beSuccessfulTry[BSONValue](
                  BSONString("str2"))
            }
      }

      "for BsonTimestamp" in {
        val codec = new org.bson.codecs.BsonTimestampCodec
        val enc: Encoder[BsonTimestamp] = codec

        val writer: BSONWriter[BsonTimestamp] = enc
        val handler: BSONWriter[BsonTimestamp] = codec

        writer.writeTry(lts) must beSuccessfulTry[BSONValue](bts) and {
          handler.writeTry(lts) must beSuccessfulTry[BSONValue](bts)
        }
      }

      "for BsonUndefined" in {
        val codec = new org.bson.codecs.BsonUndefinedCodec
        val enc: Encoder[BsonUndefined] = codec

        val writer: BSONWriter[BsonUndefined] = enc
        val handler: BSONHandler[BsonUndefined] = codec

        writer.writeTry(new BsonUndefined).
          aka("undefined1") must beSuccessfulTry(undefined) and {
            handler.writeTry(new BsonUndefined).
              aka("undefined2") must beSuccessfulTry(undefined)
          }
      }

      "for opaque BsonValue" >> {
        val codec = new org.bson.codecs.BsonValueCodec
        val enc: Encoder[BsonValue] = codec

        val writer: BSONWriter[BsonValue] = enc
        val handler: BSONHandler[BsonValue] = codec

        Fragment.foreach(fixtures) {
          case (org, bson) => s"for $org" in {
            writer.writeTry(org) must beSuccessfulTry[BSONValue](bson) and {
              handler.writeTry(org) must beSuccessfulTry[BSONValue](bson)
            }
          }
        }
      }
    }

    "be converted from BSONWriter" >> {
      import HandlerConverters.encode

      "for BSONArray" in {
        val writer = implicitly[BSONWriter[BSONArray]]
        val handler = implicitly[BSONHandler[BSONArray]]

        val codec: Codec[BSONArray] = handler
        val enc: Encoder[BSONArray] = writer

        encode(barr, enc) must beSuccessfulTry[BsonValue](larr) and {
          encode(barr, codec) must beSuccessfulTry[BsonValue](larr)
        }
      }

      "for BSONBinary" in {
        val writer = implicitly[BSONWriter[BSONBinary]]
        val handler = implicitly[BSONHandler[BSONBinary]]

        val codec: Codec[BSONBinary] = handler
        val enc: Encoder[BSONBinary] = writer

        val bin = new BsonBinary(BsonBinarySubType.UUID_STANDARD, uuidBytes)

        encode(BSONBinary(uuid), enc) must beSuccessfulTry(bin) and {
          encode(BSONBinary(uuid), codec) must beSuccessfulTry(bin)
        }
      }

      "for BSONBoolean" in {
        val writer = implicitly[BSONWriter[BSONBoolean]]
        val handler = implicitly[BSONHandler[BSONBoolean]]

        val codec: Codec[BSONBoolean] = handler
        val enc: Encoder[BSONBoolean] = writer

        encode(BSONBoolean(true), enc).
          aka("true1") must beSuccessfulTry(BsonBoolean.TRUE) and {
            encode(BSONBoolean(false), enc).
              aka("false1") must beSuccessfulTry(BsonBoolean.FALSE)
          } and {
            encode(BSONBoolean(false), codec).
              aka("false2") must beSuccessfulTry(BsonBoolean.FALSE)
          } and {
            encode(BSONBoolean(true), codec).
              aka("true2") must beSuccessfulTry(BsonBoolean.TRUE)
          }
      }

      "for BSONDateTime" in {
        val writer = implicitly[BSONWriter[BSONDateTime]]
        val handler = implicitly[BSONHandler[BSONDateTime]]

        val codec: Codec[BSONDateTime] = handler
        val enc: Encoder[BSONDateTime] = writer

        encode(bdt, enc) must beSuccessfulTry[BsonValue](ldt) and {
          encode(bdt, codec) must beSuccessfulTry[BsonValue](ldt)
        }
      }

      "for BSONDecimal" in {
        val writer = implicitly[BSONWriter[BSONDecimal]]
        val handler = implicitly[BSONHandler[BSONDecimal]]

        val codec: Codec[BSONDecimal] = handler
        val enc: Encoder[BSONDecimal] = writer

        val dec = new BsonDecimal128(Decimal128.POSITIVE_INFINITY)

        encode(BSONDecimal.PositiveInf, enc) must beSuccessfulTry(dec) and {
          encode(BSONDecimal.PositiveInf, codec) must beSuccessfulTry(dec)
        }
      }

      "for BSONDocument" in {
        val writer = implicitly[BSONWriter[BSONDocument]]
        val handler = implicitly[BSONHandler[BSONDocument]]

        val codec: Codec[BSONDocument] = handler
        val enc: Encoder[BSONDocument] = writer

        encode(bdoc, enc) must beSuccessfulTry(ldoc) and {
          encode(bdoc, codec) must beSuccessfulTry(ldoc)
        }
      }

      "for BSONDouble" in {
        val writer = implicitly[BSONWriter[BSONDouble]]
        val handler = implicitly[BSONHandler[BSONDouble]]

        val codec: Codec[BSONDouble] = handler
        val enc: Encoder[BSONDouble] = writer

        val raw = 3.4D

        encode(BSONDouble(raw), enc).
          aka("double1") must beSuccessfulTry(new BsonDouble(raw)) and {
            encode(BSONDouble(raw), codec).
              aka("double2") must beSuccessfulTry(new BsonDouble(raw))
          }
      }

      "for BSONInteger" in {
        val writer = implicitly[BSONWriter[BSONInteger]]
        val handler = implicitly[BSONHandler[BSONInteger]]

        val codec: Codec[BSONInteger] = handler
        val enc: Encoder[BSONInteger] = writer

        val raw = 45

        encode(BSONInteger(raw), enc).
          aka("BSONValue1") must beSuccessfulTry(new BsonInt32(raw)) and {
            encode(BSONInteger(raw), codec).
              aka("BSONValue2") must beSuccessfulTry(new BsonInt32(raw))
          }
      }

      "for BSONLong" in {
        val writer = implicitly[BSONWriter[BSONLong]]
        val handler = implicitly[BSONHandler[BSONLong]]

        val codec: Codec[BSONLong] = handler
        val enc: Encoder[BSONLong] = writer

        val raw = 678L

        encode(BSONLong(raw), enc).
          aka("BSONLong1") must beSuccessfulTry(new BsonInt64(raw)) and {
            encode(BSONLong(raw), codec).
              aka("BSONLong2") must beSuccessfulTry(new BsonInt64(raw))
          }
      }

      "for BsonJavaScript" in {
        val writer = implicitly[BSONWriter[BSONJavaScript]]
        val handler = implicitly[BSONHandler[BSONJavaScript]]

        val codec: Codec[BSONJavaScript] = handler
        val enc: Encoder[BSONJavaScript] = writer

        val raw = "foo()"

        encode(BSONJavaScript(raw), enc) must beSuccessfulTry(
          new BsonJavaScript(raw)) and {
            encode(BSONJavaScript(raw), codec) must beSuccessfulTry(
              new BsonJavaScript(raw))
          }
      }

      "for BSONJavaScriptWS" in {
        val writer = implicitly[BSONWriter[BSONJavaScriptWS]]
        val handler = implicitly[BSONHandler[BSONJavaScriptWS]]

        val codec: Codec[BSONJavaScriptWS] = handler
        val enc: Encoder[BSONJavaScriptWS] = writer

        val code = "bar(this.lorem)"
        val scope = new BsonDocument().append("lorem", new BsonInt64(2L))

        encode(BSONJavaScriptWS(code, BSONDocument("lorem" -> 2L)), enc).
          aka("BSONJavaScriptWS1") must beSuccessfulTry(
            new BsonJavaScriptWithScope(code, scope)) and {
              encode(BSONJavaScriptWS(
                code, BSONDocument("lorem" -> 2L)), codec) must beSuccessfulTry(
                new BsonJavaScriptWithScope(code, scope))
            }
      }

      "for BSONMaxKey" in {
        val writer = implicitly[BSONWriter[BSONMaxKey]]
        val handler = implicitly[BSONHandler[BSONMaxKey]]

        val codec: Codec[BSONMaxKey] = handler
        val enc: Encoder[BSONMaxKey] = writer

        encode(BSONMaxKey, enc) must beSuccessfulTry(new BsonMaxKey) and {
          encode(BSONMaxKey, codec) must beSuccessfulTry(new BsonMaxKey)
        }
      }

      "for BSONMinKey" in {
        val writer = implicitly[BSONWriter[BSONMinKey]]
        val handler = implicitly[BSONHandler[BSONMinKey]]

        val codec: Codec[BSONMinKey] = handler
        val enc: Encoder[BSONMinKey] = writer

        encode(BSONMinKey, enc) must beSuccessfulTry(new BsonMinKey) and {
          encode(BSONMinKey, codec) must beSuccessfulTry(new BsonMinKey)
        }
      }

      "for BSONNull" in {
        val writer = implicitly[BSONWriter[BSONNull]]
        val handler = implicitly[BSONHandler[BSONNull]]

        val codec: Codec[BSONNull] = handler
        val enc: Encoder[BSONNull] = writer

        encode(BSONNull, enc) must beSuccessfulTry(new BsonNull) and {
          encode(BSONNull, codec) must beSuccessfulTry(new BsonNull)
        }
      }

      "for BSONObjectID" in {
        val writer = implicitly[BSONWriter[BSONObjectID]]
        val handler = implicitly[BSONHandler[BSONObjectID]]

        val codec: Codec[BSONObjectID] = handler
        val enc: Encoder[BSONObjectID] = writer

        encode(boid, enc) must beSuccessfulTry[BsonValue](loid) and {
          encode(boid, codec) must beSuccessfulTry[BsonValue](loid)
        }
      }

      "for BSONRegex" in {
        val writer = implicitly[BSONWriter[BSONRegex]]
        val handler = implicitly[BSONHandler[BSONRegex]]

        val codec: Codec[BSONRegex] = handler
        val enc: Encoder[BSONRegex] = writer

        encode(bre, enc) must beSuccessfulTry[BsonValue](lre) and {
          encode(bre, codec) must beSuccessfulTry[BsonValue](lre)
        }
      }

      "for BSONSymbol" in {
        val writer = implicitly[BSONWriter[BSONSymbol]]
        val handler = implicitly[BSONHandler[BSONSymbol]]

        val codec: Codec[BSONSymbol] = handler
        val enc: Encoder[BSONSymbol] = writer

        encode(BSONSymbol("sym1"), enc) must beSuccessfulTry[BsonValue](
          new BsonSymbol("sym1")) and {
            encode(
              BSONSymbol("sym2"), codec) must beSuccessfulTry[BsonValue](
                new BsonSymbol("sym2"))
          }
      }

      "for BSONString" in {
        val writer = implicitly[BSONWriter[BSONString]]
        val handler = implicitly[BSONHandler[BSONString]]

        val codec: Codec[BSONString] = handler
        val enc: Encoder[BSONString] = writer

        encode(BSONString("str1"), enc) must beSuccessfulTry[BsonValue](
          new BsonString("str1")) and {
            encode(BSONString("str2"), codec) must beSuccessfulTry[BsonValue](
              new BsonString("str2"))
          }
      }

      "for BSONTimestamp" in {
        val writer = implicitly[BSONWriter[BSONTimestamp]]
        val handler = implicitly[BSONHandler[BSONTimestamp]]

        val codec: Codec[BSONTimestamp] = handler
        val enc: Encoder[BSONTimestamp] = writer

        encode(bts, enc) must beSuccessfulTry[BsonValue](lts) and {
          encode(bts, codec) must beSuccessfulTry[BsonValue](lts)
        }
      }

      "for BSONUndefined" in {
        val writer = implicitly[BSONWriter[BSONUndefined]]
        val handler = implicitly[BSONHandler[BSONUndefined]]

        val codec: Codec[BSONUndefined] = handler
        val enc: Encoder[BSONUndefined] = writer

        encode(BSONUndefined, enc) must beSuccessfulTry(new BsonUndefined) and {
          encode(BSONUndefined, codec) must beSuccessfulTry(new BsonUndefined)
        }
      }

      "for opaque BsonValue" >> {
        val writer = implicitly[BSONWriter[BSONValue]]
        val handler = implicitly[BSONHandler[BSONValue]]

        val codec: Codec[BSONValue] = handler
        val enc: Encoder[BSONValue] = writer

        Fragment.foreach(fixtures) {
          case (org, bson) => s"for $org" in {
            encode(bson, enc) must beSuccessfulTry[BsonValue](org) and {
              encode(bson, codec) must beSuccessfulTry[BsonValue](org)
            }
          }
        }
      }
    }
  }
}
