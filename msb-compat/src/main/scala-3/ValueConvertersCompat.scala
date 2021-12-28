package reactivemongo.api.bson.msb

import reactivemongo.api.bson.{
  BSONMaxKey,
  BSONMinKey,
  BSONNull,
  BSONUndefined
}

import org.bson.{ BsonMaxKey, BsonMinKey, BsonNull, BsonUndefined }

private[msb] trait ValueConvertersCompat { _self: ValueConverters =>
  implicit def toUndefined(_v: BsonUndefined): BSONUndefined = BSONUndefined

  private val bsonUndefined = new BsonUndefined
  implicit def fromUndefined(_v: BSONUndefined): BsonUndefined = bsonUndefined

  implicit def toNull(_v: BsonNull): BSONNull = BSONNull

  private val bsonNull = new BsonNull
  implicit def fromNull(_v: BSONNull): BsonNull = bsonNull

  implicit def toMinKey(_v: BsonMinKey): BSONMinKey = BSONMinKey

  private val bsonMinKey = new BsonMinKey
  implicit def fromMinKey(_v: BSONMinKey): BsonMinKey = bsonMinKey

  implicit def toMaxKey(_v: BsonMaxKey): BSONMaxKey = BSONMaxKey

  private val bsonMaxKey = new BsonMaxKey
  implicit def fromMaxKey(_v: BSONMaxKey): BsonMaxKey = bsonMaxKey
}
