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

private[bson] trait BSONWriterInstances extends BSONWriterInstancesLowPrio:

  type SafeWriter[T] = BSONWriter[T] & SafeBSONWriter[T]

  given intWriter: SafeWriter[Int] = BSONIntegerHandler

  given longWriter: SafeWriter[Long] = BSONLongHandler

  given doubleWriter: SafeWriter[Double] = BSONDoubleHandler

  given decimalWriter: BSONWriter[BigDecimal] = BSONDecimalHandler

  given floatWriter: SafeWriter[Float] = BSONFloatHandler

  given stringWriter: SafeWriter[String] = BSONStringHandler

  given booleanWriter: SafeWriter[Boolean] = BSONBooleanHandler

  given binaryWriter: SafeWriter[Array[Byte]] = BSONBinaryHandler

  given dateTimeWriter: SafeWriter[Instant] = BSONDateTimeHandler

  given localDateWriter: BSONWriter[LocalDate] = bsonLocalDateHandler

  given localTimeWriter: SafeWriter[LocalTime] = BSONLocalTimeHandler

  given localDateTimeWriter: BSONWriter[LocalDateTime] =
    bsonLocalDateTimeHandler

  given offsetDateTimeWriter: BSONWriter[OffsetDateTime] =
    bsonOffsetDateTimeHandler

  given zonedDateTimeWriter: BSONWriter[ZonedDateTime] =
    bsonZonedDateTimeHandler

  given urlWriter: SafeWriter[URL] = BSONURLHandler

  given uriWriter: SafeWriter[URI] = BSONURIHandler

  given uuidWriter: SafeWriter[UUID] = BSONUUIDHandler

  given localeWriter: SafeWriter[Locale] = BSONLocaleHandler

  given mapSafeWriter[V](
      using
      BSONWriter[V] & SafeBSONWriter[V]
    ): BSONDocumentWriter[Map[String, V]] = pkg.mapSafeWriter[V]

  given bsonMapWriter[V <: BSONValue]: BSONDocumentWriter[Map[String, V]] =
    pkg.bsonMapWriter[V]

  given mapWriter[V](
      using
      BSONWriter[V]
    ): BSONDocumentWriter[Map[String, V]] =
    pkg.mapWriter[V]

  given mapKeySafeWriter[K, V](
      using
      KeyWriter[K] & SafeKeyWriter[K],
      BSONWriter[V] & SafeBSONWriter[V]
    ): BSONDocumentWriter[Map[K, V]] =
    pkg.mapKeySafeWriter[K, V]

  given bsonMapKeyWriter[K, V <: BSONValue](
      using
      KeyWriter[K]
    ): BSONDocumentWriter[Map[K, V]] =
    pkg.bsonMapKeyWriter[K, V]

  given mapKeyWriter[K, V](
      using
      KeyWriter[K],
      BSONWriter[V]
    ): BSONDocumentWriter[Map[K, V]] =
    pkg.mapKeyWriter[K, V]

  export pkg.{
    bsonStringWriter,
    bsonSymbolWriter,
    bsonIntegerWriter,
    bsonDecimalWriter,
    bsonArrayWriter,
    bsonDocumentWriter,
    bsonBooleanWriter,
    bsonLongWriter,
    bsonDoubleWriter,
    bsonObjectIDWriter,
    bsonBinaryWriter,
    bsonDateTimeWriter,
    bsonTimestampWriter,
    bsonMaxKeyWriter,
    bsonMinKeyWriter,
    bsonNullWriter,
    bsonUndefinedWriter,
    bsonRegexWriter,
    bsonJavaScriptWriter,
    bsonJavaScriptWSWriter
  }

  given tuple2Writer[A: BSONWriter, B: BSONWriter]: BSONWriter[(A, B)] =
    BSONWriter.tuple2

  given tuple3Writer[A: BSONWriter, B: BSONWriter, C: BSONWriter]: BSONWriter[(A, B, C)] =
    BSONWriter.tuple3

  given tuple4Writer[
      A: BSONWriter,
      B: BSONWriter,
      C: BSONWriter,
      D: BSONWriter
    ]: BSONWriter[(A, B, C, D)] = BSONWriter.tuple4

  given tuple5Writer[
      A: BSONWriter,
      B: BSONWriter,
      C: BSONWriter,
      D: BSONWriter,
      E: BSONWriter
    ]: BSONWriter[(A, B, C, D, E)] = BSONWriter.tuple5
end BSONWriterInstances

private[bson] sealed trait BSONWriterInstancesLowPrio {
  _self: BSONWriterInstances =>

  /*
   * Since Scala 3.6 the order of givens prioritization have changed - compiler can choose the most general implicit instead of the most specific
   * For example when searching for Writer[Map[String, T]]
   *   - Scala 3.5 chooses mapWriter: BSONDocumentWriter[Map[String, V]] instead of collectionWriter[T, Repr <: Iterable[T]]: BSONWriter[Repr] as it's more specyfic
   *   - Scala 3.6 sees both of the candidates as equally good leading to ambigious implicit search result
   * To prevent this issue for Scala 3.6+ users more generalized writers should be put in the low-priority trait to workaround these changes.
   * It would have no effect for Scala 3.5- users since the more specific implicit would choosen anyway
   * See Scala 3 PR that introduced this change for more info: https://github.com/scala/scala3/pull/19300
   */
  export pkg.collectionWriter

  given bsonValueIdentityWriter: BSONWriter[BSONValue] = BSONValueIdentity
}
