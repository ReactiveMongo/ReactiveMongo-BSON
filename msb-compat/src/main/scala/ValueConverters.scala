package reactivemongo.api.bson
package msb

import scala.language.implicitConversions

import scala.util.{ Failure, Success }

import org.bson.{
  BsonArray,
  BsonBinary,
  BsonBinarySubType,
  BsonBoolean,
  BsonDateTime,
  BsonDecimal128,
  BsonDocument,
  BsonDouble,
  BsonElement,
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

/**
 * See [[msb$]] and [[ValueConverters]]
 */
object ValueConverters extends ValueConverters

/**
 * Implicit conversions for value types between
 * `org.bson` and `reactivemongo.api.bson`.
 *
 * {{{
 * // Required import
 * import reactivemongo.api.bson.msb.ValueConverters._
 *
 * // From org.bson
 * import reactivemongo.api.bson.{ BSONDouble, BSONString, BSONValue }
 *
 * val newStr: BSONString = new org.bson.BsonString("foo")
 * val newVal: BSONValue = new org.bson.BsonInt32(2)
 *
 * // To org.bson
 * val oldStr: org.bson.BsonString = BSONString("bar")
 * val oldVal: org.bson.BsonValue = BSONDouble(1.2D)
 * }}}
 */
trait ValueConverters extends LowPriorityConverters {
  import JavaConverters._

  implicit final def toArray(bson: BsonArray): BSONArray =
    BSONArray(iterableAsScalaIterable(bson.getValues).map(toValue))

  implicit final def fromArray(array: BSONArray): BsonArray =
    new BsonArray(seqAsJavaList(array.values map fromValue))

  implicit final def toDocument(bson: BsonDocument): BSONDocument =
    BSONDocument(iterableAsScalaIterable(bson.entrySet).map { entry =>
      entry.getKey -> toValue(entry.getValue)
    })

  implicit final def toElement(bson: BsonElement): BSONElement =
    BSONElement(bson.getName, toValue(bson.getValue))

  implicit final def fromDocument(doc: BSONDocument): BsonDocument = {
    val bson = new BsonDocument()

    doc.elements.foreach {
      case BSONElement(k, v) => bson.append(k, fromValue(v))
    }

    bson
  }

  implicit final def fromElement(element: BSONElement): BsonElement =
    new BsonElement(element.name, fromValue(element.value))

  val codeToBinSubtype: Byte => Subtype = {
    val BinaryCode: Byte = BsonBinarySubType.BINARY.getValue
    val FunctionCode: Byte = BsonBinarySubType.FUNCTION.getValue
    val OldBinaryCode: Byte = BsonBinarySubType.OLD_BINARY.getValue
    val UuidLegacyCode: Byte = BsonBinarySubType.UUID_LEGACY.getValue
    val UuidCode: Byte = BsonBinarySubType.UUID_STANDARD.getValue
    val Md5Code: Byte = BsonBinarySubType.MD5.getValue

    (_: Byte) match {
      case BinaryCode =>
        Subtype.GenericBinarySubtype

      case FunctionCode =>
        Subtype.FunctionSubtype

      case OldBinaryCode =>
        Subtype.OldBinarySubtype

      case UuidLegacyCode =>
        Subtype.OldUuidSubtype

      case UuidCode =>
        Subtype.UuidSubtype

      case Md5Code =>
        Subtype.Md5Subtype

      case _ =>
        Subtype.UserDefinedSubtype
    }
  }

  implicit final def toBinary(bson: BsonBinary): BSONBinary =
    BSONBinary(bson.getData, codeToBinSubtype(bson.getType))

  implicit final def fromBinary(binary: BSONBinary): BsonBinary =
    new BsonBinary(fromBinarySubtype(binary.subtype), binary.byteArray)

  implicit final def toBinarySubtype(bson: BsonBinarySubType): Subtype =
    bson match {
      case BsonBinarySubType.BINARY =>
        Subtype.GenericBinarySubtype

      case BsonBinarySubType.FUNCTION =>
        Subtype.FunctionSubtype

      case BsonBinarySubType.OLD_BINARY =>
        Subtype.OldBinarySubtype

      case BsonBinarySubType.UUID_LEGACY =>
        Subtype.OldUuidSubtype

      case BsonBinarySubType.UUID_STANDARD =>
        Subtype.UuidSubtype

      case BsonBinarySubType.MD5 =>
        Subtype.Md5Subtype

      case _ => Subtype.UserDefinedSubtype
    }

  implicit final def fromBinarySubtype(subtype: Subtype): BsonBinarySubType =
    subtype match {
      case _: Subtype.GenericBinarySubtype =>
        BsonBinarySubType.BINARY

      case _: Subtype.FunctionSubtype =>
        BsonBinarySubType.FUNCTION

      case _: Subtype.OldBinarySubtype =>
        BsonBinarySubType.OLD_BINARY

      case _: Subtype.OldUuidSubtype =>
        BsonBinarySubType.UUID_LEGACY

      case _: Subtype.UuidSubtype =>
        BsonBinarySubType.UUID_STANDARD

      case _: Subtype.Md5Subtype =>
        BsonBinarySubType.MD5

      case _ => BsonBinarySubType.USER_DEFINED
    }

  implicit final def toDouble(bson: BsonDouble): BSONDouble =
    BSONDouble(bson.getValue)

  implicit final def fromDouble(double: BSONDouble): BsonDouble =
    new BsonDouble(double.value)

  implicit final def toStr(bson: BsonString): BSONString =
    BSONString(bson.getValue)

  implicit final def fromStr(string: BSONString): BsonString =
    new BsonString(string.value)

  implicit final def toBoolean(bson: BsonBoolean): BSONBoolean =
    BSONBoolean(bson.getValue)

  implicit final def fromBoolean(boolean: BSONBoolean): BsonBoolean =
    if (boolean.value) BsonBoolean.TRUE else BsonBoolean.FALSE

  implicit final def toInteger(bson: BsonInt32): BSONInteger =
    BSONInteger(bson.getValue)

  implicit final def fromInteger(integer: BSONInteger): BsonInt32 =
    new BsonInt32(integer.value)

  implicit final def toLong(bson: BsonInt64): BSONLong =
    BSONLong(bson.getValue)

  implicit final def fromLong(long: BSONLong): BsonInt64 =
    new BsonInt64(long.value)

  implicit final def toJavaScript(bson: BsonJavaScript): BSONJavaScript =
    BSONJavaScript(bson.getCode)

  implicit final def fromJavaScript(javaScript: BSONJavaScript): BsonJavaScript = new BsonJavaScript(javaScript.value)

  implicit final def toJavaScriptWS(bson: BsonJavaScriptWithScope): BSONJavaScriptWS = BSONJavaScriptWS(bson.getCode, bson.getScope)

  implicit final def fromJavaScriptWS(js: BSONJavaScriptWS): BsonJavaScriptWithScope = new BsonJavaScriptWithScope(js.value, fromDocument(js.scope))

  implicit final def toRegex(bson: BsonRegularExpression): BSONRegex =
    BSONRegex(bson.getPattern, bson.getOptions)

  implicit final def fromRegex(regex: BSONRegex): BsonRegularExpression =
    new BsonRegularExpression(regex.value, regex.flags)

  implicit final def toSymbol(bson: BsonSymbol): BSONSymbol =
    BSONSymbol(bson.getSymbol)

  implicit final def fromSymbol(symbol: BSONSymbol): BsonSymbol =
    new BsonSymbol(symbol.value)

  @inline implicit final def toObjectID(boid: BsonObjectId): BSONObjectID =
    toObjectID(boid.getValue)

  final def toObjectID(boid: ObjectId): BSONObjectID =
    BSONObjectID.parse(boid.toByteArray) match {
      case Success(oid) => oid
      case Failure(err) => throw err
    }

  implicit final def fromObjectID(oid: BSONObjectID): BsonObjectId =
    new BsonObjectId(new ObjectId(oid.byteArray))

  implicit final def toDateTime(bson: BsonDateTime): BSONDateTime =
    BSONDateTime(bson.getValue)

  implicit final def fromDateTime(dateTime: BSONDateTime): BsonDateTime =
    new BsonDateTime(dateTime.value)

  implicit final def toTimestamp(bson: BsonTimestamp): BSONTimestamp =
    BSONTimestamp(bson.getValue)

  implicit final def fromTimestamp(timestamp: BSONTimestamp): BsonTimestamp =
    new BsonTimestamp(timestamp.value)

  @inline implicit final def toDecimal(bson: BsonDecimal128): BSONDecimal =
    toDecimal(bson.getValue)

  final def toDecimal(dec: Decimal128): BSONDecimal =
    BSONDecimal(dec.getHigh, dec.getLow)

  implicit final def fromDecimal(decimal: BSONDecimal): BsonDecimal128 =
    new BsonDecimal128(Decimal128.
      fromIEEE754BIDEncoding(decimal.high, decimal.low))

  implicit val toUndefined: BsonUndefined => BSONUndefined =
    _ => BSONUndefined

  implicit val fromUndefined: BSONUndefined => BsonUndefined = {
    val bson = new BsonUndefined
    _ => bson
  }

  implicit val toNull: BsonNull => BSONNull =
    _ => BSONNull

  implicit val fromNull: BSONNull => BsonNull = {
    val bson = new BsonNull
    _ => bson
  }

  implicit val toMinKey: BsonMinKey => BSONMinKey =
    _ => BSONMinKey

  implicit val fromMinKey: BSONMinKey => BsonMinKey = {
    val bson = new BsonMinKey
    _ => bson
  }

  implicit val toMaxKey: BsonMaxKey => BSONMaxKey =
    _ => BSONMaxKey

  implicit val fromMaxKey: BSONMaxKey => BsonMaxKey = {
    val bson = new BsonMaxKey
    _ => bson
  }
}

private[bson] sealed trait LowPriorityConverters { _: ValueConverters =>
  implicit final def toValue(bson: BsonValue): BSONValue = bson match {
    case arr: BsonArray => toArray(arr)
    case dtm: BsonDateTime => toDateTime(dtm)
    case doc: BsonDocument => toDocument(doc)
    case bin: BsonBinary => toBinary(bin)
    case dlb: BsonDouble => toDouble(dlb)
    case str: BsonString => toStr(str)
    case bol: BsonBoolean => toBoolean(bol)
    case int: BsonInt32 => toInteger(int)
    case lng: BsonInt64 => toLong(lng)
    case js: BsonJavaScript => toJavaScript(js)
    case jsW: BsonJavaScriptWithScope => toJavaScriptWS(jsW)
    case reg: BsonRegularExpression => toRegex(reg)
    case sym: BsonSymbol => toSymbol(sym)
    case tsp: BsonTimestamp => toTimestamp(tsp)
    case oid: BsonObjectId => toObjectID(oid)
    case dec: BsonDecimal128 => toDecimal(dec)

    case _: BsonNull => BSONNull
    case _: BsonMaxKey => BSONMaxKey
    case _: BsonMinKey => BSONMinKey

    case _ => BSONUndefined
  }

  implicit final def fromValue(bson: BSONValue): BsonValue = bson match {
    case arr: BSONArray => fromArray(arr)
    case dtm: BSONDateTime => fromDateTime(dtm)
    case doc: BSONDocument => fromDocument(doc)
    case bin: BSONBinary => fromBinary(bin)
    case dlb: BSONDouble => fromDouble(dlb)
    case str: BSONString => fromStr(str)
    case bol: BSONBoolean => fromBoolean(bol)
    case int: BSONInteger => fromInteger(int)
    case lng: BSONLong => fromLong(lng)
    case js: BSONJavaScript => fromJavaScript(js)
    case jsW: BSONJavaScriptWS => fromJavaScriptWS(jsW)
    case reg: BSONRegex => fromRegex(reg)
    case sym: BSONSymbol => fromSymbol(sym)
    case tsp: BSONTimestamp => fromTimestamp(tsp)
    case oid: BSONObjectID => fromObjectID(oid)
    case dec: BSONDecimal => fromDecimal(dec)

    case BSONNull => new BsonNull
    case BSONMaxKey => new BsonMaxKey
    case BSONMinKey => new BsonMinKey

    case _ => new BsonUndefined
  }
}
