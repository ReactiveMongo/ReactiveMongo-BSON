package reactivemongo.api.bson

import java.net.{ URI, URL }

import java.time.{
  Instant,
  LocalDate,
  LocalDateTime,
  OffsetDateTime,
  ZonedDateTime,
  ZoneId
}

import scala.collection.mutable.Builder
import scala.collection.immutable.IndexedSeq

import scala.util.{ Failure, Success, Try }

import exceptions.TypeDoesNotMatchException

private[bson] trait DefaultBSONHandlers
  extends LowPriority1BSONHandlers with BSONIdentityHandlers {

  implicit object BSONIntegerHandler
    extends BSONHandler[Int] with SafeBSONWriter[Int] {

    @inline def readTry(bson: BSONValue): Try[Int] = bson.asInt

    @inline def safeWrite(int: Int) = BSONInteger(int)
  }

  implicit object BSONLongHandler
    extends BSONHandler[Long] with SafeBSONWriter[Long] {

    override def readOpt(bson: BSONValue): Option[Long] = BSONLong.unapply(bson)

    @inline def readTry(bson: BSONValue): Try[Long] = bson.asLong

    @inline def safeWrite(long: Long) = BSONLong(long)
  }

  implicit object BSONDoubleHandler
    extends BSONHandler[Double] with SafeBSONWriter[Double] {

    @inline def readTry(bson: BSONValue): Try[Double] = bson.toDouble

    @inline def safeWrite(double: Double) = BSONDouble(double)
  }

  implicit object BSONDecimalHandler extends BSONHandler[BigDecimal] {
    @inline def readTry(bson: BSONValue): Try[BigDecimal] = bson.asDecimal

    @inline def writeTry(value: BigDecimal): Try[BSONDecimal] =
      BSONDecimal.fromBigDecimal(value)
  }

  implicit object BSONFloatHandler
    extends BSONHandler[Float] with SafeBSONWriter[Float] {

    @inline def readTry(bson: BSONValue): Try[Float] = bson.toFloat

    @inline def safeWrite(float: Float) = BSONDouble(float.toDouble)
  }

  implicit object BSONStringHandler
    extends BSONHandler[String] with SafeBSONWriter[String] {

    @inline def readTry(bson: BSONValue): Try[String] = bson.asString

    @inline def safeWrite(string: String) = BSONString(string)
  }

  implicit object BSONBooleanHandler
    extends BSONHandler[Boolean] with SafeBSONWriter[Boolean] {

    @inline def readTry(bson: BSONValue): Try[Boolean] = bson.asBoolean

    @inline def safeWrite(boolean: Boolean) = BSONBoolean(boolean)
  }

  implicit object BSONBinaryHandler
    extends BSONHandler[Array[Byte]] with SafeBSONWriter[Array[Byte]] {

    def readTry(bson: BSONValue): Try[Array[Byte]] = bson match {
      case bin: BSONBinary =>
        Try(bin.byteArray)

      case _ => Failure(TypeDoesNotMatchException(
        "BSONBinary", bson.getClass.getSimpleName))
    }

    override def readOpt(bson: BSONValue): Option[Array[Byte]] = bson match {
      case bin: BSONBinary => Some(bin.byteArray)
      case _ => None
    }

    def safeWrite(xs: Array[Byte]): BSONBinary =
      BSONBinary(xs, Subtype.GenericBinarySubtype)
  }

  implicit object BSONDateTimeHandler
    extends BSONHandler[Instant] with SafeBSONWriter[Instant] {

    @inline def readTry(bson: BSONValue): Try[Instant] = bson.asDateTime

    @inline def safeWrite(date: Instant) = BSONDateTime(date.toEpochMilli)
  }

  private final class BSONLocalDateTimeHandler(zone: ZoneId)
    extends BSONHandler[LocalDateTime] with SafeBSONWriter[LocalDateTime] {

    @inline def readTry(bson: BSONValue): Try[LocalDateTime] =
      bson.asDateTime.map(LocalDateTime.ofInstant(_, zone))

    @inline def safeWrite(date: LocalDateTime) = {
      val offset = zone.getRules.getOffset(date)

      BSONDateTime(
        (date.toEpochSecond(offset) * 1000) + (date.getNano / 1000000))
    }
  }

  /**
   * Returns a BSON handler for `java.time.LocalDateTime`,
   * considering the specified time `zone`.
   */
  @inline def bsonLocalDateTimeHandler(zone: ZoneId): BSONHandler[LocalDateTime] = new BSONLocalDateTimeHandler(zone)

  implicit val bsonLocalDateTimeHandler: BSONHandler[LocalDateTime] =
    new BSONLocalDateTimeHandler(ZoneId.systemDefault)

  private final class BSONLocalDateHandler(zone: ZoneId)
    extends BSONHandler[LocalDate] with SafeBSONWriter[LocalDate] {

    @inline def readTry(bson: BSONValue): Try[LocalDate] =
      bson.asDateTime.map(LocalDateTime.ofInstant(_, zone).toLocalDate)

    @inline def safeWrite(date: LocalDate) = {
      val time = date.atStartOfDay
      val offset = zone.getRules.getOffset(time)

      BSONDateTime(time.toEpochSecond(offset) * 1000)
    }
  }

  /**
   * Returns a BSON handler for `java.time.LocalDate`,
   * considering the specified time `zone`.
   */
  @inline def bsonLocalDateHandler(zone: ZoneId): BSONHandler[LocalDate] = new BSONLocalDateHandler(zone)

  implicit val bsonLocalDateHandler: BSONHandler[LocalDate] =
    new BSONLocalDateHandler(ZoneId.systemDefault)

  implicit object BSONURLHandler
    extends BSONHandler[URL] with SafeBSONWriter[URL] {

    def readTry(bson: BSONValue): Try[URL] = bson match {
      case BSONString(repr) => Try(new URL(repr))

      case _ => Failure(TypeDoesNotMatchException(
        "BSONString", bson.getClass.getSimpleName))
    }

    override def readOpt(bson: BSONValue): Option[URL] = bson match {
      case BSONString(repr) => Some(new URL(repr))
      case _ => None
    }

    def safeWrite(url: URL) = BSONString(url.toString)
  }

  private final class BSONOffsetDateTimeHandler(zone: ZoneId)
    extends BSONHandler[OffsetDateTime] with SafeBSONWriter[OffsetDateTime] {

    @inline def readTry(bson: BSONValue): Try[OffsetDateTime] =
      bson.asDateTime.map(OffsetDateTime.ofInstant(_, zone))

    @inline def safeWrite(date: OffsetDateTime) =
      BSONDateTime((date.toEpochSecond * 1000) + (date.getNano / 1000000))
  }

  /**
   * Returns a BSON handler for `java.time.OffsetDateTime`,
   * considering the specified time `zone`.
   */
  @inline def bsonOffsetDateTimeHandler(zone: ZoneId): BSONHandler[OffsetDateTime] = new BSONOffsetDateTimeHandler(zone)

  implicit val bsonOffsetDateTimeHandler: BSONHandler[OffsetDateTime] =
    new BSONOffsetDateTimeHandler(ZoneId.systemDefault)

  private final class BSONZonedDateTimeHandler(zone: ZoneId)
    extends BSONHandler[ZonedDateTime] with SafeBSONWriter[ZonedDateTime] {

    @inline def readTry(bson: BSONValue): Try[ZonedDateTime] =
      bson.asDateTime.map(ZonedDateTime.ofInstant(_, zone))

    @inline def safeWrite(date: ZonedDateTime) =
      BSONDateTime((date.toEpochSecond * 1000) + (date.getNano / 1000000))
  }

  /**
   * Returns a BSON handler for `java.time.ZonedDateTime`,
   * considering the specified time `zone`.
   */
  @inline def bsonZonedDateTimeHandler(zone: ZoneId): BSONHandler[ZonedDateTime] = new BSONZonedDateTimeHandler(zone)

  implicit val bsonZonedDateTimeHandler: BSONHandler[ZonedDateTime] =
    new BSONZonedDateTimeHandler(ZoneId.systemDefault)

  implicit object BSONURIHandler
    extends BSONHandler[URI] with SafeBSONWriter[URI] {

    def readTry(bson: BSONValue): Try[URI] = bson match {
      case BSONString(repr) => Try(new URI(repr))

      case _ => Failure(TypeDoesNotMatchException(
        "BSONString", bson.getClass.getSimpleName))
    }

    override def readOpt(bson: BSONValue): Option[URI] = bson match {
      case BSONString(repr) => Some(new URI(repr))
      case _ => None
    }

    def safeWrite(url: URI) = BSONString(url.toString)
  }
}

@SuppressWarnings(Array("TryGet"))
@com.github.ghik.silencer.silent("Unused import") // higherKinds: 2.13+
private[bson] trait LowPriority1BSONHandlers
  extends LowPriorityBSONHandlersCompat
  with LowPriority2BSONHandlers { _: DefaultBSONHandlers =>

  import scala.language.higherKinds

  // Collections Handlers
  private class BSONArrayCollectionWriter[T, Repr](implicit ev: Repr => Iterable[T], writer: BSONWriter[T]) extends BSONWriter[Repr] {
    def writeTry(repr: Repr): Try[BSONArray] = {
      val builder = IndexedSeq.newBuilder[BSONValue]

      @annotation.tailrec
      def write(input: Iterable[T]): Try[IndexedSeq[BSONValue]] =
        input.headOption match {
          case Some(v) => writer.writeTry(v) match {
            case Success(bson) => {
              builder += bson
              write(input.tail)
            }

            case Failure(cause) => Failure(cause)
          }

          case _ => Success(builder.result())
        }

      write(repr).map { BSONArray(_) }
    }
  }

  @com.github.ghik.silencer.silent
  implicit def collectionWriter[T, Repr <% Iterable[T]](implicit writer: BSONWriter[T], notOption: Repr ¬ Option[T]): BSONWriter[Repr] = new BSONArrayCollectionWriter[T, Repr]

  protected class BSONArrayCollectionReader[M[_], T](
    builder: Builder[T, M[T]])(implicit reader: BSONReader[T]) extends BSONReader[M[T]] {

    def readTry(bson: BSONValue): Try[M[T]] = {
      @annotation.tailrec
      def read(vs: Seq[BSONValue]): Try[M[T]] = vs.headOption match {
        case Some(v) => reader.readTry(v) match {
          case Success(r) => {
            builder += r
            read(vs.tail)
          }

          case Failure(cause) => Failure(cause)
        }

        case _ => Success(builder.result())
      }

      bson match {
        case BSONArray(vs) => read(vs)

        case _ => Failure(TypeDoesNotMatchException(
          "BSONArray", bson.getClass.getSimpleName))
      }
    }
  }

  implicit def mapReader[V](implicit valueReader: BSONReader[V]): BSONDocumentReader[Map[String, V]] =
    new BSONDocumentReader[Map[String, V]] {
      def readDocument(doc: BSONDocument): Try[Map[String, V]] = Try {
        mapValues(doc.fields) { v =>
          valueReader.readTry(v).get
        }
      }
    }

  implicit def mapSafeWriter[V](implicit valueWriter: BSONWriter[V] with SafeBSONWriter[V]): BSONDocumentWriter[Map[String, V]] = new BSONDocumentWriter[Map[String, V]] {
    def writeTry(inputMap: Map[String, V]): Try[BSONDocument] =
      Success(BSONDocument(mapValues(inputMap)(valueWriter.safeWrite)))
  }

  implicit def bsonMapWriter[V <: BSONValue]: BSONDocumentWriter[Map[String, V]] = new BSONDocumentWriter[Map[String, V]] {
    def writeTry(inputMap: Map[String, V]): Try[BSONDocument] =
      Success(BSONDocument(inputMap))
  }
}

@SuppressWarnings(Array("TryGet"))
private[bson] trait LowPriority2BSONHandlers
  extends LowPriority3BSONHandlers { _: DefaultBSONHandlers =>

  implicit def mapKeyReader[K, V](implicit keyReader: KeyReader[K], valueReader: BSONReader[V]): BSONDocumentReader[Map[K, V]] =
    new BSONDocumentReader[Map[K, V]] {
      def readDocument(doc: BSONDocument): Try[Map[K, V]] = Try {
        doc.fields.map {
          case (k, v) =>
            keyReader.readTry(k).get -> valueReader.readTry(v).get
        }
      }
    }

  implicit def mapWriter[V](implicit valueWriter: BSONWriter[V]): BSONDocumentWriter[Map[String, V]] = new BSONDocumentWriter[Map[String, V]] {
    def writeTry(inputMap: Map[String, V]): Try[BSONDocument] = Try {
      val m = Seq.newBuilder[BSONElement]

      inputMap.foreach {
        case (k, v) =>
          m += BSONElement(k, valueWriter.writeTry(v).get)
      }

      BSONDocument(ElementProducer(m.result()))
    }
  }
}

@SuppressWarnings(Array("TryGet"))
private[bson] trait LowPriority3BSONHandlers
  extends LowPriority4BSONHandlers { _: DefaultBSONHandlers =>

  implicit def mapKeySafeWriter[K, V](
    implicit
    keyWriter: KeyWriter[K] with SafeKeyWriter[K],
    valueWriter: BSONWriter[V] with SafeBSONWriter[V]): BSONDocumentWriter[Map[K, V]] =
    new BSONDocumentWriter[Map[K, V]] {
      def writeTry(inputMap: Map[K, V]): Try[BSONDocument] = Success {
        val m = Seq.newBuilder[BSONElement]

        inputMap.foreach {
          case (k, v) =>
            m += BSONElement(keyWriter.write(k), valueWriter.safeWrite(v))
        }

        BSONDocument(ElementProducer(m.result()))
      }
    }

  implicit def bsonMapKeyWriter[K, V <: BSONValue](
    implicit
    keyWriter: KeyWriter[K]): BSONDocumentWriter[Map[K, V]] =
    new BSONDocumentWriter[Map[K, V]] {
      def writeTry(inputMap: Map[K, V]): Try[BSONDocument] = Try {
        val m = Seq.newBuilder[BSONElement]

        inputMap.foreach {
          case (k, v) =>
            m += BSONElement(keyWriter.writeTry(k).get, v)
        }

        BSONDocument(ElementProducer(m.result()))
      }
    }
}

@SuppressWarnings(Array("TryGet"))
private[bson] trait LowPriority4BSONHandlers { _: DefaultBSONHandlers =>
  implicit def mapKeyWriter[K, V](
    implicit
    ev: K => StringOps,
    valueWriter: BSONWriter[V]): BSONDocumentWriter[Map[K, V]] =
    new BSONDocumentWriter[Map[K, V]] {
      def writeTry(inputMap: Map[K, V]): Try[BSONDocument] = Try {
        val m = Seq.newBuilder[BSONElement]

        inputMap.foreach {
          case (k, v) =>
            m += BSONElement(ev(k).mkString, valueWriter.writeTry(v).get)
        }

        BSONDocument(ElementProducer(m.result()))
      }
    }
}

private[bson] final class FunctionalDocumentHandler[T](
  read: BSONDocument => T,
  w: T => BSONDocument) extends BSONDocumentReader[T]
  with BSONDocumentWriter[T] with BSONHandler[T] {

  def readDocument(doc: BSONDocument): Try[T] = Try(read(doc))
  def writeTry(value: T): Try[BSONDocument] = Try(w(value))
}

private[bson] final class DefaultDocumentHandler[T](
  reader: BSONDocumentReader[T],
  writer: BSONDocumentWriter[T]) extends BSONDocumentReader[T]
  with BSONDocumentWriter[T] with BSONHandler[T] {

  def readDocument(doc: BSONDocument): Try[T] = reader.readTry(doc)
  def writeTry(value: T): Try[BSONDocument] = writer.writeTry(value)
}
