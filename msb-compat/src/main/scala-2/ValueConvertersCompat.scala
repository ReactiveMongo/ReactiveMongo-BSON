package reactivemongo.api.bson.msb

import reactivemongo.api.bson.{
  BSONMaxKey,
  BSONMinKey,
  BSONNull,
  BSONUndefined
}

import org.bson.{ BsonMaxKey, BsonMinKey, BsonNull, BsonUndefined }

private[msb] trait ValueConvertersCompat { _: ValueConverters =>

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
