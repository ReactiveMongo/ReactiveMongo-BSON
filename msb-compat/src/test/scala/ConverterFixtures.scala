package reactivemongo

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
  BsonSymbol,
  BsonString,
  BsonTimestamp,
  BsonUndefined,
  BsonValue
}
import org.bson.types.{ Decimal128, ObjectId }

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
  BSONValue
}

trait ConverterFixtures {
  val time = System.currentTimeMillis()

  val boid = BSONObjectID.fromTime(time, true)
  val loid = new BsonObjectId(new ObjectId(boid.stringify))

  val uuid = java.util.UUID.randomUUID

  val uuidBytes: Array[Byte] = {
    val bytes = Array.ofDim[Byte](16)
    val buf = java.nio.ByteBuffer.wrap(bytes)

    buf putLong uuid.getMostSignificantBits
    buf putLong uuid.getLeastSignificantBits

    bytes
  }

  val ldt = new BsonDateTime(time)
  val bdt = BSONDateTime(time)

  val lts = new BsonTimestamp(time)
  val bts = BSONTimestamp(time)

  val lre = new BsonRegularExpression("foo[A-Z]+", "i")
  val bre = BSONRegex("foo[A-Z]+", "i")

  val larr: BsonArray = {
    val arr = new BsonArray()

    arr.add(loid)
    arr.add(new BsonString("foo"))
    arr.add(ldt)
    arr.add(new BsonSymbol("bar"))
    arr.add(lts)
    arr.add(new BsonJavaScript("lorem()"))
    arr.add(lre)

    arr.add {
      val nested = new BsonArray()

      nested.add(new BsonInt32(1))
      nested.add(new BsonInt64(2L))

      nested
    }

    arr.add(new BsonDouble(3.4D))

    arr
  }

  val barr = BSONArray(boid, BSONString("foo"), bdt, BSONSymbol("bar"), bts, BSONJavaScript("lorem()"), bre, BSONArray(BSONInteger(1), BSONLong(2L)), BSONDouble(3.4D))

  val ldoc: BsonDocument = {
    val d = new BsonDocument()

    d.append("oid", loid)
    d.append("str", new BsonString("foo"))
    d.append("dt", ldt)
    d.append("sym", new BsonSymbol("bar"))
    d.append("ts", lts)
    d.append("nested", new BsonDocument().
      append("foo", new BsonString("bar")).
      append("lorem", new BsonInt64(1L)))
    d.append("js", new BsonJavaScript("lorem()"))
    d.append("re", lre)
    d.append("array", larr)
    d.append("double", new BsonDouble(3.4D))
  }

  val bdoc = BSONDocument("oid" -> boid, "str" -> BSONString("foo"), "dt" -> bdt, "sym" -> BSONSymbol("bar"), "ts" -> bts, "nested" -> BSONDocument("foo" -> "bar", "lorem" -> 1L), "js" -> BSONJavaScript("lorem()"), "re" -> bre, "array" -> barr, "double" -> BSONDouble(3.4D))

  val fixtures = Seq[(BsonValue, BSONValue)](
    new BsonBinary(
      BsonBinarySubType.UUID_STANDARD, uuidBytes) -> BSONBinary(uuid),
    new BsonBoolean(true) -> BSONBoolean(true),
    new BsonDouble(1.23D) -> BSONDouble(1.23D),
    new BsonString("Foo") -> BSONString("Foo"),
    new BsonInt32(1) -> BSONInteger(1),
    new BsonInt64(1L) -> BSONLong(1L),
    loid -> boid,
    ldt -> bdt,
    lts -> bts,
    new BsonDecimal128(Decimal128.POSITIVE_ZERO) -> BSONDecimal.PositiveZero,
    lre -> bre,
    new BsonJavaScript("foo()") -> BSONJavaScript("foo()"),
    new BsonJavaScriptWithScope(
      "bar",
      new BsonDocument("bar", new BsonInt32(13))) -> BSONJavaScriptWS(
      "bar", BSONDocument("bar" -> 13)),
    new BsonSymbol("sym") -> BSONSymbol("sym"),
    new BsonUndefined -> BSONUndefined,
    new BsonNull -> BSONNull,
    new BsonMaxKey -> BSONMaxKey,
    new BsonMinKey -> BSONMinKey,
    larr -> barr,
    ldoc -> bdoc)

}
