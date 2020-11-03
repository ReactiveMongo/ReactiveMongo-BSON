package reactivemongo

import reactivemongo.api.bson.BSONHandler
import reactivemongo.api.bson.msb.HandlerConverters

import org.specs2.specification.core.Fragment

import org.bson.{ BsonArray, BsonBinary, BsonBoolean, BsonDateTime, BsonDecimal128, BsonDocument, BsonDouble, BsonInt32, BsonInt64, BsonJavaScript, BsonJavaScriptWithScope, BsonMaxKey, BsonMinKey, BsonNull, BsonObjectId, BsonRegularExpression, BsonString, BsonSymbol, BsonTimestamp, BsonUndefined }
import org.bson.codecs._
import org.bson.codecs.configuration.CodecRegistry
import org.bson.json.JsonReader

final class HandlerConverterSpec
  extends org.specs2.mutable.Specification
  with EncoderConverterSpec with DecoderConverterSpec
  with ConverterFixtures {

  "Handler converters" title

  "Default codec registry" should {
    import HandlerConverters.DefaultCodecRegistry
    val codecReg = implicitly[CodecRegistry]

    Fragment.foreach(Seq[(Class[_], Codec[_])](
      (classOf[BsonArray] -> new BsonArrayCodec),
      (classOf[BsonBinary] -> new BsonBinaryCodec),
      (classOf[BsonBoolean] -> new BsonBooleanCodec),
      (classOf[BsonDateTime] -> new BsonDateTimeCodec),
      (classOf[BsonDocument] -> new BsonDocumentCodec),
      (classOf[BsonDecimal128] -> new BsonDecimal128Codec),
      (classOf[BsonDouble] -> new BsonDoubleCodec),
      (classOf[BsonInt32] -> new BsonInt32Codec),
      (classOf[BsonInt64] -> new BsonInt64Codec),
      (classOf[BsonJavaScript] -> new BsonJavaScriptCodec),
      (classOf[BsonJavaScriptWithScope] -> new BsonJavaScriptWithScopeCodec(new BsonDocumentCodec)),
      (classOf[BsonMaxKey] -> new BsonMaxKeyCodec),
      (classOf[BsonMinKey] -> new BsonMinKeyCodec),
      (classOf[BsonNull] -> new BsonNullCodec),
      (classOf[BsonObjectId] -> new BsonObjectIdCodec),
      (classOf[BsonRegularExpression] -> new BsonRegularExpressionCodec),
      (classOf[BsonString] -> new BsonStringCodec),
      (classOf[BsonSymbol] -> new BsonSymbolCodec),
      (classOf[BsonTimestamp] -> new BsonTimestampCodec),
      (classOf[BsonUndefined] -> new BsonUndefinedCodec))) {
      case (cls, codec) => s"be resolved for ${cls.getSimpleName}" in {
        codecReg.get(cls).getClass must_== codec.getClass
      }
    }
  }

  "Codec from Handler" should {
    "not fail for simple types" in {
      import HandlerConverters.DefaultCodecRegistry

      val handler = implicitly[BSONHandler[Long]]
      val codec = HandlerConverters.fromHandler(handler)

      // https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Int64
      val reader = new JsonReader("""{ "$numberLong": "42" }""")

      val longValue = codec.decode(reader, DecoderContext.builder().build())

      longValue === 42L
    }
  }
}
