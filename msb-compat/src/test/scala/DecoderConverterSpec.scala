package reactivemongo

import java.lang.{ Boolean => JBool }

import reactivemongo.api.bson.{ `null`, maxKey, minKey, undefined, BSONArray, BSONBinary, BSONBoolean, BSONDateTime, BSONDecimal, BSONDocument, BSONDouble, BSONHandler, BSONInteger, BSONJavaScript, BSONJavaScriptWS, BSONLong, BSONMaxKey, BSONMinKey, BSONNull, BSONObjectID, BSONReader, BSONRegex, BSONString, BSONSymbol, BSONTimestamp, BSONUndefined, BSONValue }
import reactivemongo.api.bson.msb.HandlerConverters

import org.specs2.specification.core.Fragment

import org.bson.{ BsonArray, BsonBinary, BsonBinarySubType, BsonBoolean, BsonDateTime, BsonDecimal128, BsonDocument, BsonDouble, BsonInt32, BsonInt64, BsonJavaScript, BsonJavaScriptWithScope, BsonMaxKey, BsonMinKey, BsonNull, BsonObjectId, BsonRegularExpression, BsonString, BsonSymbol, BsonTimestamp, BsonUndefined, BsonValue }
import org.bson.codecs._
import org.bson.types.Decimal128

private[reactivemongo] trait DecoderConverterSpec {
  specs: HandlerConverterSpec =>

  import HandlerConverters._

  "Decoder" should {
    import HandlerConverters.decode

    "read BsonBinary" >> {
      val bytes = "Test".getBytes("UTF-8")
      val dec = new org.bson.codecs.BsonBinaryCodec

      Fragment.foreach(Seq[(BsonBinarySubType, Array[Byte])](
        BsonBinarySubType.UUID_STANDARD -> uuidBytes,
        BsonBinarySubType.BINARY -> bytes)) {
        case (subtpe, data) =>
          s"from $subtpe(sz=${data.size})" in {
            val bin = new BsonBinary(subtpe, data)

            decode(bin, dec) must beSuccessfulTry(bin)
          }

      }
    }

    "read BsonBoolean" in {
      val dec = new org.bson.codecs.BooleanCodec

      decode(BsonBoolean.TRUE, dec) must beSuccessfulTry(JBool.TRUE) and {
        decode(BsonBoolean.FALSE, dec) must beSuccessfulTry(JBool.FALSE)
      }
    }

    "read BsonArray" in {
      val dec = new org.bson.codecs.BsonArrayCodec

      decode(larr, dec) must beSuccessfulTry(larr)
    }

    "read BsonDocument" in {
      val dec = new org.bson.codecs.BsonDocumentCodec

      decode(ldoc, dec) must beSuccessfulTry(ldoc)
    }

    "read opaque BsonValue" should {
      val dec = new org.bson.codecs.BsonValueCodec

      Fragment.foreach(fixtures.map(_._1)) { v =>
        s"from $v" in {
          decode(v, dec) must beSuccessfulTry(v)
        }
      }
    }

    "be converted as BSONReader" >> {
      "for BsonArray" in {
        // explicit type ascription as Encoder to be sure which conversion
        val codec = new org.bson.codecs.BsonArrayCodec
        val dec: Decoder[BsonArray] = codec

        val reader: BSONReader[BsonArray] = dec
        val handler: BSONHandler[BsonArray] = codec

        reader.readTry(barr) must beSuccessfulTry[BsonValue](larr) and {
          handler.readTry(barr) must beSuccessfulTry[BsonValue](larr)
        }
      }

      "for BsonBinary" in {
        val codec = new org.bson.codecs.BsonBinaryCodec
        val dec: Decoder[BsonBinary] = codec

        val reader: BSONReader[BsonBinary] = dec
        val handler: BSONHandler[BsonBinary] = codec

        val bin = new BsonBinary(BsonBinarySubType.UUID_STANDARD, uuidBytes)

        reader.readTry(BSONBinary(uuid)) must beSuccessfulTry(bin) and {
          handler.readTry(BSONBinary(uuid)) must beSuccessfulTry(bin)
        }
      }

      "for BsonBoolean" in {
        val codec = new org.bson.codecs.BsonBooleanCodec
        val dec: Decoder[BsonBoolean] = codec

        val reader: BSONReader[BsonBoolean] = dec
        val handler: BSONHandler[BsonBoolean] = codec

        reader.readTry(BSONBoolean(true)).
          aka("true1") must beSuccessfulTry(BsonBoolean.TRUE) and {
            reader.readTry(BSONBoolean(false)).
              aka("false1") must beSuccessfulTry(BsonBoolean.FALSE)
          } and {
            handler.readTry(BSONBoolean(false)).
              aka("false2") must beSuccessfulTry(BsonBoolean.FALSE)
          } and {
            handler.readTry(BSONBoolean(true)).
              aka("true2") must beSuccessfulTry(BsonBoolean.TRUE)
          }
      }

      "for BsonDateTime" in {
        val codec = new org.bson.codecs.BsonDateTimeCodec
        val dec: Decoder[BsonDateTime] = codec

        val reader: BSONReader[BsonDateTime] = dec
        val handler: BSONHandler[BsonDateTime] = codec

        reader.readTry(bdt) must beSuccessfulTry[BsonValue](ldt) and {
          handler.readTry(bdt) must beSuccessfulTry[BsonValue](ldt)
        }
      }

      "for BsonDecimal128" in {
        val codec = new org.bson.codecs.BsonDecimal128Codec
        val dec: Decoder[BsonDecimal128] = codec

        val reader: BSONReader[BsonDecimal128] = dec
        val handler: BSONHandler[BsonDecimal128] = codec

        val n = new BsonDecimal128(Decimal128.POSITIVE_INFINITY)

        reader.readTry(BSONDecimal.PositiveInf) must beSuccessfulTry(n) and {
          handler.readTry(BSONDecimal.PositiveInf) must beSuccessfulTry(n)
        }
      }

      "for BsonDocument" in {
        val codec = new org.bson.codecs.BsonDocumentCodec
        val dec: Decoder[BsonDocument] = codec

        val reader: BSONReader[BsonDocument] = dec
        val handler: BSONReader[BsonDocument] = codec

        reader.readTry(bdoc) must beSuccessfulTry(ldoc) and {
          handler.readTry(bdoc) must beSuccessfulTry(ldoc)
        }
      }

      "for BsonDouble" in {
        val codec = new org.bson.codecs.BsonDoubleCodec
        val dec: Decoder[BsonDouble] = codec

        val reader: BSONReader[BsonDouble] = dec
        val handler: BSONReader[BsonDouble] = codec

        val raw = 3.4D

        reader.readTry(BSONDouble(raw)).
          aka("double1") must beSuccessfulTry(new BsonDouble(raw)) and {
            handler.readTry(BSONDouble(raw)).
              aka("double2") must beSuccessfulTry(new BsonDouble(raw))
          }
      }

      "for BsonInt32" in {
        val codec = new org.bson.codecs.BsonInt32Codec
        val dec: Decoder[BsonInt32] = codec

        val reader: BSONReader[BsonInt32] = dec
        val handler: BSONHandler[BsonInt32] = codec

        val raw = 45

        reader.readTry(BSONInteger(raw)).
          aka("BSONValue1") must beSuccessfulTry(new BsonInt32(raw)) and {
            handler.readTry(BSONInteger(raw)).
              aka("BSONValue2") must beSuccessfulTry(new BsonInt32(raw))
          }
      }

      "for BsonInt64" in {
        val codec = new org.bson.codecs.BsonInt64Codec
        val dec: Decoder[BsonInt64] = codec

        val reader: BSONReader[BsonInt64] = dec
        val handler: BSONHandler[BsonInt64] = codec

        val raw = 678L

        reader.readTry(BSONLong(raw)).
          aka("BSONLong1") must beSuccessfulTry(new BsonInt64(raw)) and {
            handler.readTry(BSONLong(raw)).
              aka("BSONLong2") must beSuccessfulTry(new BsonInt64(raw))
          }
      }

      "for BsonJavaScript" in {
        val codec = new org.bson.codecs.BsonJavaScriptCodec
        val dec: Decoder[BsonJavaScript] = codec

        val reader: BSONReader[BsonJavaScript] = dec
        val handler: BSONHandler[BsonJavaScript] = codec

        val raw = "foo()"

        reader.readTry(BSONJavaScript(raw)) must beSuccessfulTry(
          new BsonJavaScript(raw)) and {
            handler.readTry(BSONJavaScript(raw)) must beSuccessfulTry(
              new BsonJavaScript(raw))
          }
      }

      "for BsonJavaScriptWithScope" in {
        val codec = new org.bson.codecs.BsonJavaScriptWithScopeCodec(
          new org.bson.codecs.BsonDocumentCodec)

        val dec: Decoder[BsonJavaScriptWithScope] = codec

        val reader: BSONReader[BsonJavaScriptWithScope] = dec
        val handler: BSONHandler[BsonJavaScriptWithScope] = codec

        val code = "bar(this.lorem)"
        val scope = new BsonDocument().append("lorem", new BsonInt64(2L))

        reader.readTry(BSONJavaScriptWS(code, BSONDocument("lorem" -> 2L))).
          aka("BSONJavaScriptWS1") must beSuccessfulTry(
            new BsonJavaScriptWithScope(code, scope)) and {
              handler.readTry(
                BSONJavaScriptWS(code, BSONDocument("lorem" -> 2L))).
                aka("BSONJavaScriptWS2") must beSuccessfulTry(
                  new BsonJavaScriptWithScope(code, scope))
            }
      }

      "for BsonMaxKey" in {
        val codec = new org.bson.codecs.BsonMaxKeyCodec
        val dec: Decoder[BsonMaxKey] = codec

        val reader: BSONReader[BsonMaxKey] = dec
        val handler: BSONHandler[BsonMaxKey] = codec

        reader.readTry(maxKey) must beSuccessfulTry(new BsonMaxKey) and {
          handler.readTry(maxKey) must beSuccessfulTry(new BsonMaxKey)
        }
      }

      "for BsonMinKey" in {
        val codec = new org.bson.codecs.BsonMinKeyCodec
        val dec: Decoder[BsonMinKey] = codec

        val reader: BSONReader[BsonMinKey] = dec
        val handler: BSONHandler[BsonMinKey] = codec

        reader.readTry(minKey) must beSuccessfulTry(new BsonMinKey) and {
          handler.readTry(minKey) must beSuccessfulTry(new BsonMinKey)
        }
      }

      "for BsonNull" in {
        val codec = new org.bson.codecs.BsonNullCodec
        val dec: Decoder[BsonNull] = codec

        val reader: BSONReader[BsonNull] = dec
        val handler: BSONHandler[BsonNull] = codec

        reader.readTry(`null`) must beSuccessfulTry(new BsonNull) and {
          handler.readTry(`null`) must beSuccessfulTry(new BsonNull)
        }
      }

      "for BsonObjectId" in {
        val codec = new org.bson.codecs.BsonObjectIdCodec
        val dec: Decoder[BsonObjectId] = codec

        val reader: BSONReader[BsonObjectId] = dec
        val handler: BSONHandler[BsonObjectId] = codec

        reader.readTry(boid) must beSuccessfulTry[BsonValue](loid) and {
          handler.readTry(boid) must beSuccessfulTry[BsonValue](loid)
        }
      }

      "for BsonRegularExpression" in {
        val codec = new org.bson.codecs.BsonRegularExpressionCodec
        val dec: Decoder[BsonRegularExpression] = codec

        val reader: BSONReader[BsonRegularExpression] = dec
        val handler: BSONHandler[BsonRegularExpression] = codec

        reader.readTry(bre) must beSuccessfulTry[BsonValue](lre) and {
          handler.readTry(bre) must beSuccessfulTry[BsonValue](lre)
        }
      }

      "for BsonSymbol" in {
        val codec = new org.bson.codecs.BsonSymbolCodec
        val dec: Decoder[BsonSymbol] = codec

        val reader: BSONReader[BsonSymbol] = dec
        val handler: BSONHandler[BsonSymbol] = codec

        reader.readTry(BSONSymbol("sym1")) must beSuccessfulTry[BsonValue](
          new BsonSymbol("sym1")) and {
            handler.readTry(BSONSymbol("sym2")) must beSuccessfulTry[BsonValue](
              new BsonSymbol("sym2"))
          }
      }

      "for BsonString" in {
        val codec = new org.bson.codecs.BsonStringCodec
        val dec: Decoder[BsonString] = codec

        val reader: BSONReader[BsonString] = dec
        val handler: BSONHandler[BsonString] = codec

        reader.readTry(BSONString("str1")) must beSuccessfulTry[BsonValue](
          new BsonString("str1")) and {
            handler.readTry(
              BSONString("str2")) must beSuccessfulTry[BsonValue](
                new BsonString("str2"))
          }
      }

      "for BsonTimestamp" in {
        val codec = new org.bson.codecs.BsonTimestampCodec
        val dec: Decoder[BsonTimestamp] = codec

        val reader: BSONReader[BsonTimestamp] = dec
        val handler: BSONReader[BsonTimestamp] = codec

        reader.readTry(bts) must beSuccessfulTry[BsonValue](lts) and {
          handler.readTry(bts) must beSuccessfulTry[BsonValue](lts)
        }
      }

      "for BsonUndefined" in {
        val codec = new org.bson.codecs.BsonUndefinedCodec
        val dec: Decoder[BsonUndefined] = codec

        val reader: BSONReader[BsonUndefined] = dec
        val handler: BSONHandler[BsonUndefined] = codec

        reader.readTry(undefined).
          aka("undefined1") must beSuccessfulTry(new BsonUndefined) and {
            handler.readTry(undefined).
              aka("undefined2") must beSuccessfulTry(new BsonUndefined)
          }
      }

      "for opaque BsonValue" >> {
        val codec = new org.bson.codecs.BsonValueCodec
        val dec: Decoder[BsonValue] = codec

        val reader: BSONReader[BsonValue] = dec
        val handler: BSONHandler[BsonValue] = codec

        Fragment.foreach(fixtures) {
          case (org, bson) => s"for $org" in {
            reader.readTry(bson) must beSuccessfulTry[BsonValue](org) and {
              handler.readTry(bson) must beSuccessfulTry[BsonValue](org)
            }
          }
        }
      }
    }

    "be converted from BSONReader" >> {
      import HandlerConverters.decode

      "for BSONArray" in {
        val reader = implicitly[BSONReader[BSONArray]]
        val handler = implicitly[BSONHandler[BSONArray]]

        val codec: Codec[BSONArray] = handler
        val dec: Decoder[BSONArray] = reader

        decode(larr, dec) must beSuccessfulTry[BSONValue](barr) and {
          decode(larr, codec) must beSuccessfulTry[BSONValue](barr)
        }
      }

      "for BSONBinary" in {
        val reader = implicitly[BSONReader[BSONBinary]]
        val handler = implicitly[BSONHandler[BSONBinary]]

        val codec: Codec[BSONBinary] = handler
        val dec: Decoder[BSONBinary] = reader

        val bin = new BsonBinary(BsonBinarySubType.UUID_STANDARD, uuidBytes)

        decode(bin, dec) must beSuccessfulTry(BSONBinary(uuid)) and {
          decode(bin, codec) must beSuccessfulTry(BSONBinary(uuid))
        }
      }

      "for BSONBoolean" in {
        val reader = implicitly[BSONReader[BSONBoolean]]
        val handler = implicitly[BSONHandler[BSONBoolean]]

        val codec: Codec[BSONBoolean] = handler
        val dec: Decoder[BSONBoolean] = reader

        decode(BsonBoolean.TRUE, dec).
          aka("true1") must beSuccessfulTry(BSONBoolean(true)) and {
            decode(BsonBoolean.FALSE, dec).
              aka("false1") must beSuccessfulTry(BSONBoolean(false))
          } and {
            decode(BsonBoolean.FALSE, codec).
              aka("false2") must beSuccessfulTry(BSONBoolean(false))
          } and {
            decode(BsonBoolean.TRUE, codec).
              aka("true2") must beSuccessfulTry(BSONBoolean(true))
          }
      }

      "for BSONDateTime" in {
        val reader = implicitly[BSONReader[BSONDateTime]]
        val handler = implicitly[BSONHandler[BSONDateTime]]

        val codec: Codec[BSONDateTime] = handler
        val dec: Decoder[BSONDateTime] = reader

        decode(ldt, dec) must beSuccessfulTry[BSONValue](bdt) and {
          decode(ldt, codec) must beSuccessfulTry[BSONValue](bdt)
        }
      }

      "for BSONDecimal" in {
        val reader = implicitly[BSONReader[BSONDecimal]]
        val handler = implicitly[BSONHandler[BSONDecimal]]

        val codec: Codec[BSONDecimal] = handler
        val dec: Decoder[BSONDecimal] = reader

        val n = new BsonDecimal128(Decimal128.POSITIVE_INFINITY)

        decode(n, dec) must beSuccessfulTry(BSONDecimal.PositiveInf) and {
          decode(n, codec) must beSuccessfulTry(BSONDecimal.PositiveInf)
        }
      }

      "for BSONDocument" in {
        val reader = implicitly[BSONReader[BSONDocument]]
        val handler = implicitly[BSONHandler[BSONDocument]]

        val codec: Codec[BSONDocument] = handler
        val dec: Decoder[BSONDocument] = reader

        decode(ldoc, dec) must beSuccessfulTry(bdoc) and {
          decode(ldoc, codec) must beSuccessfulTry(bdoc)
        }
      }

      "for BSONDouble" in {
        val reader = implicitly[BSONReader[BSONDouble]]
        val handler = implicitly[BSONHandler[BSONDouble]]

        val codec: Codec[BSONDouble] = handler
        val dec: Decoder[BSONDouble] = reader

        val raw = 3.4D

        decode(new BsonDouble(raw), dec).
          aka("double1") must beSuccessfulTry(BSONDouble(raw)) and {
            decode(new BsonDouble(raw), codec).
              aka("double2") must beSuccessfulTry(BSONDouble(raw))
          }
      }

      "for BSONInteger" in {
        val reader = implicitly[BSONReader[BSONInteger]]
        val handler = implicitly[BSONHandler[BSONInteger]]

        val codec: Codec[BSONInteger] = handler
        val dec: Decoder[BSONInteger] = reader

        val raw = 45

        decode(new BsonInt32(raw), dec).
          aka("BSONValue1") must beSuccessfulTry(BSONInteger(raw)) and {
            decode(new BsonInt32(raw), codec).
              aka("BSONValue2") must beSuccessfulTry(BSONInteger(raw))
          }
      }

      "for BSONLong" in {
        val reader = implicitly[BSONReader[BSONLong]]
        val handler = implicitly[BSONHandler[BSONLong]]

        val codec: Codec[BSONLong] = handler
        val dec: Decoder[BSONLong] = reader

        val raw = 678L

        decode(new BsonInt64(raw), dec).
          aka("BSONLong1") must beSuccessfulTry(BSONLong(raw)) and {
            decode(new BsonInt64(raw), codec).
              aka("BSONLong2") must beSuccessfulTry(BSONLong(raw))
          }
      }

      "for BSONJavaScript" in {
        val reader = implicitly[BSONReader[BSONJavaScript]]
        val handler = implicitly[BSONHandler[BSONJavaScript]]

        val codec: Codec[BSONJavaScript] = handler
        val dec: Decoder[BSONJavaScript] = reader

        val raw = "foo()"

        decode(new BsonJavaScript(raw), dec) must beSuccessfulTry(
          BSONJavaScript(raw)) and {
            decode(new BsonJavaScript(raw), codec) must beSuccessfulTry(
              BSONJavaScript(raw))
          }
      }

      "for BSONJavaScriptWS" in {
        val reader = implicitly[BSONReader[BSONJavaScriptWS]]
        val handler = implicitly[BSONHandler[BSONJavaScriptWS]]

        val codec: Codec[BSONJavaScriptWS] = handler
        val dec: Decoder[BSONJavaScriptWS] = reader

        val code = "bar(this.lorem)"
        val scope = new BsonDocument().append("lorem", new BsonInt64(2L))

        decode(new BsonJavaScriptWithScope(code, scope), dec).
          aka("BSONJavaScriptWS1") must beSuccessfulTry(
            BSONJavaScriptWS(code, BSONDocument("lorem" -> 2L))) and {
              decode(new BsonJavaScriptWithScope(
                code, scope), codec) must beSuccessfulTry(
                BSONJavaScriptWS(code, BSONDocument("lorem" -> 2L)))
            }
      }

      "for BSONMaxKey" in {
        val reader = implicitly[BSONReader[BSONMaxKey]]
        val handler = implicitly[BSONHandler[BSONMaxKey]]

        val codec: Codec[BSONMaxKey] = handler
        val dec: Decoder[BSONMaxKey] = reader

        decode(new BsonMaxKey, dec) must beSuccessfulTry(maxKey) and {
          decode(new BsonMaxKey, codec) must beSuccessfulTry(maxKey)
        }
      }

      "for BSONMinKey" in {
        val reader = implicitly[BSONReader[BSONMinKey]]
        val handler = implicitly[BSONHandler[BSONMinKey]]

        val codec: Codec[BSONMinKey] = handler
        val dec: Decoder[BSONMinKey] = reader

        decode(new BsonMinKey, dec) must beSuccessfulTry(minKey) and {
          decode(new BsonMinKey, codec) must beSuccessfulTry(minKey)
        }
      }

      "for BSONNull" in {
        val reader = implicitly[BSONReader[BSONNull]]
        val handler = implicitly[BSONHandler[BSONNull]]

        val codec: Codec[BSONNull] = handler
        val dec: Decoder[BSONNull] = reader

        decode(new BsonNull, dec) must beSuccessfulTry(`null`) and {
          decode(new BsonNull, codec) must beSuccessfulTry(`null`)
        }
      }

      "for BSONObjectID" in {
        val reader = implicitly[BSONReader[BSONObjectID]]
        val handler = implicitly[BSONHandler[BSONObjectID]]

        val codec: Codec[BSONObjectID] = handler
        val dec: Decoder[BSONObjectID] = reader

        decode(loid, dec) must beSuccessfulTry[BSONValue](boid) and {
          decode(loid, codec) must beSuccessfulTry[BSONValue](boid)
        }
      }

      "for BSONRegex" in {
        val reader = implicitly[BSONReader[BSONRegex]]
        val handler = implicitly[BSONHandler[BSONRegex]]

        val codec: Codec[BSONRegex] = handler
        val dec: Decoder[BSONRegex] = reader

        decode(lre, dec) must beSuccessfulTry[BSONValue](bre) and {
          decode(lre, codec) must beSuccessfulTry[BSONValue](bre)
        }
      }

      "for BSONSymbol" in {
        val reader = implicitly[BSONReader[BSONSymbol]]
        val handler = implicitly[BSONHandler[BSONSymbol]]

        val codec: Codec[BSONSymbol] = handler
        val dec: Decoder[BSONSymbol] = reader

        decode(new BsonSymbol("sym1"), dec) must beSuccessfulTry[BSONValue](
          BSONSymbol("sym1")) and {
            decode(new BsonSymbol("sym2"), codec).
              aka("sym2") must beSuccessfulTry[BSONValue](BSONSymbol("sym2"))
          }
      }

      "for BSONString" in {
        val reader = implicitly[BSONReader[BSONString]]
        val handler = implicitly[BSONHandler[BSONString]]

        val codec: Codec[BSONString] = handler
        val dec: Decoder[BSONString] = reader

        decode(new BsonString("str1"), dec) must beSuccessfulTry[BSONValue](
          BSONString("str1")) and {
            decode(new BsonString("str2"), codec).
              aka("str2") must beSuccessfulTry[BSONValue](BSONString("str2"))
          }
      }

      "for BSONTimestamp" in {
        val reader = implicitly[BSONReader[BSONTimestamp]]
        val handler = implicitly[BSONHandler[BSONTimestamp]]

        val codec: Codec[BSONTimestamp] = handler
        val dec: Decoder[BSONTimestamp] = reader

        decode(lts, dec) must beSuccessfulTry[BSONValue](bts) and {
          decode(lts, codec) must beSuccessfulTry[BSONValue](bts)
        }
      }

      "for BSONUndefined" in {
        val reader = implicitly[BSONReader[BSONUndefined]]
        val handler = implicitly[BSONHandler[BSONUndefined]]

        val codec: Codec[BSONUndefined] = handler
        val dec: Decoder[BSONUndefined] = reader

        decode(new BsonUndefined, dec) must beSuccessfulTry(undefined) and {
          decode(new BsonUndefined, codec) must beSuccessfulTry(undefined)
        }
      }

      "for opaque BSONValue" >> {
        val reader = implicitly[BSONReader[BSONValue]]
        val handler = implicitly[BSONHandler[BSONValue]]

        val codec: Codec[BSONValue] = handler
        val dec: Decoder[BSONValue] = reader

        Fragment.foreach(fixtures) {
          case (org, bson) => s"for $org" in {
            decode(org, dec) must beSuccessfulTry[BSONValue](bson) and {
              decode(org, codec) must beSuccessfulTry[BSONValue](bson)
            }
          }
        }
      }
    }
  }
}
