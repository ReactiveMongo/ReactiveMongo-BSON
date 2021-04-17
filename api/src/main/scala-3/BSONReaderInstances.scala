package reactivemongo.api.bson

import java.util.{ Locale, UUID }

import java.net.{ URI, URL }

import java.time.{
  Instant,
  LocalDateTime,
  LocalDate,
  LocalTime,
  OffsetDateTime,
  ZonedDateTime
}

import reactivemongo.api.{ bson => pkg }

private[bson] trait BSONReaderInstances:
  given intReader: BSONReader[Int] = BSONIntegerHandler

  given longReader: BSONReader[Long] = BSONLongHandler

  given doubleReader: BSONReader[Double] = BSONDoubleHandler

  given decimalReader: BSONReader[BigDecimal] = BSONDecimalHandler

  given floatReader: BSONReader[Float] = BSONFloatHandler

  given stringReader: BSONReader[String] = BSONStringHandler

  given booleanReader: BSONReader[Boolean] = BSONBooleanHandler

  given binaryReader: BSONReader[Array[Byte]] = BSONBinaryHandler

  given dateTimeReader: BSONReader[Instant] = BSONDateTimeHandler

  given localDateReader: BSONReader[LocalDate] = bsonLocalDateHandler

  given localTimeReader: BSONReader[LocalTime] = BSONLocalTimeHandler

  given localDateTimeReader: BSONReader[LocalDateTime] =
    bsonLocalDateTimeHandler

  given offsetDateTimeReader: BSONReader[OffsetDateTime] =
    bsonOffsetDateTimeHandler

  given zonedDateTimeReader: BSONReader[ZonedDateTime] =
    bsonZonedDateTimeHandler

  given urlReader: BSONReader[URL] = BSONURLHandler

  given uriReader: BSONReader[URI] = BSONURIHandler

  given uuidReader: BSONReader[UUID] = BSONUUIDHandler

  given localeReader: BSONReader[Locale] = BSONLocaleHandler

  given mapReader[V](using BSONReader[V]): BSONDocumentReader[Map[String, V]] =
    pkg.mapReader[V]

  given mapKeyReader[K, V](
      using KeyReader[K],
      BSONReader[V]
  ): BSONDocumentReader[Map[K, V]] =
    pkg.mapKeyReader[K, V]

  export pkg.{
    bsonStringReader,
    bsonSymbolReader,
    bsonIntegerReader,
    bsonDecimalReader,
    bsonArrayReader,
    bsonDocumentReader,
    bsonBooleanReader,
    bsonLongReader,
    bsonDoubleReader,
    bsonObjectIDReader,
    bsonBinaryReader,
    bsonDateTimeReader,
    bsonTimestampReader,
    bsonMaxKeyReader,
    bsonMinKeyReader,
    bsonNullReader,
    bsonUndefinedReader,
    bsonRegexReader,
    bsonJavaScriptReader,
    bsonJavaScriptWSReader,
    collectionReader
  }
