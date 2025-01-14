package reactivemongo.api.bson

import java.util.{ Locale, UUID }

import java.net.{ URI, URL }

import java.time.{
  Instant,
  LocalDate,
  LocalDateTime,
  LocalTime,
  OffsetDateTime,
  ZonedDateTime
}

import reactivemongo.api.bson as pkg

private[bson] trait BSONHandlerInstances:
  given intHandler: BSONHandler[Int] = BSONIntegerHandler

  given shortHandler: BSONHandler[Short] = BSONShortHandler

  given byteHandler: BSONHandler[Byte] = BSONByteHandler

  given charHandler: BSONHandler[Char] = BSONCharHandler

  given longHandler: BSONHandler[Long] = BSONLongHandler

  given doubleHandler: BSONHandler[Double] = BSONDoubleHandler

  given decimalHandler: BSONHandler[BigDecimal] = BSONDecimalHandler

  given floatHandler: BSONHandler[Float] = BSONFloatHandler

  given stringHandler: BSONHandler[String] = BSONStringHandler

  given booleanHandler: BSONHandler[Boolean] = BSONBooleanHandler

  given binaryHandler: BSONHandler[Array[Byte]] = BSONBinaryHandler

  given dateTimeHandler: BSONHandler[Instant] = BSONDateTimeHandler

  given localDateHandler: BSONHandler[LocalDate] = bsonLocalDateHandler

  given localTimeHandler: BSONHandler[LocalTime] = BSONLocalTimeHandler

  given localDateTimeHandler: BSONHandler[LocalDateTime] =
    bsonLocalDateTimeHandler

  given offsetDateTimeHandler: BSONHandler[OffsetDateTime] =
    bsonOffsetDateTimeHandler

  given zonedDateTimeHandler: BSONHandler[ZonedDateTime] =
    bsonZonedDateTimeHandler

  given urlHandler: BSONHandler[URL] = BSONURLHandler

  given uriHandler: BSONHandler[URI] = BSONURIHandler

  given uuidHandler: BSONHandler[UUID] = BSONUUIDHandler

  given localeHandler: BSONHandler[Locale] = BSONLocaleHandler

  export pkg.BSONValueIdentity
end BSONHandlerInstances
