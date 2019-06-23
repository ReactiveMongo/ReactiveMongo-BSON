package reactivemongo.api.bson

import java.net.{ URI, URL }

import java.time.Instant

import scala.collection.mutable.Builder
import scala.collection.immutable.{ IndexedSeq, HashMap }

import scala.util.{ Failure, Success, Try }

import exceptions.TypeDoesNotMatchException

private[bson] trait DefaultBSONHandlers
  extends LowPriorityBSONHandlers with BSONIdentityHandlers {

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

    @inline def readTry(bson: BSONValue): Try[Double] = bson.asDouble

    @inline def safeWrite(double: Double) = BSONDouble(double)
  }

  implicit object BSONDecimalHandler extends BSONHandler[BigDecimal] {
    @inline def readTry(bson: BSONValue): Try[BigDecimal] = bson.asDecimal

    @inline def writeTry(value: BigDecimal): Try[BSONDecimal] =
      BSONDecimal.fromBigDecimal(value)
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
        Try(bin.value.duplicate().readArray(bin.value.size))

      case _ => Failure(TypeDoesNotMatchException(
        "BSONBinary", bson.getClass.getSimpleName))
    }

    def safeWrite(xs: Array[Byte]): BSONBinary =
      BSONBinary(xs, Subtype.GenericBinarySubtype)
  }

  implicit object BSONDateTimeHandler
    extends BSONHandler[Instant] with SafeBSONWriter[Instant] {

    @inline def readTry(bson: BSONValue): Try[Instant] = bson.asDateTime

    @inline def safeWrite(date: Instant) = BSONDateTime(date.toEpochMilli)
  }

  implicit object BSONURLHandler
    extends BSONHandler[URL] with SafeBSONWriter[URL] {

    def readTry(bson: BSONValue): Try[URL] = bson match {
      case BSONString(repr) => Try(new URL(repr))

      case _ => Failure(TypeDoesNotMatchException(
        "BSONString", bson.getClass.getSimpleName))
    }

    def safeWrite(url: URL) = BSONString(url.toString)
  }

  implicit object BSONURIHandler
    extends BSONHandler[URI] with SafeBSONWriter[URI] {

    def readTry(bson: BSONValue): Try[URI] = bson match {
      case BSONString(repr) => Try(new URI(repr))

      case _ => Failure(TypeDoesNotMatchException(
        "BSONString", bson.getClass.getSimpleName))
    }

    def safeWrite(url: URI) = BSONString(url.toString)
  }
}

private[bson] trait LowPriorityBSONHandlers
  extends LowPriorityBSONHandlersCompat
  with LowerPriorityBSONHandlers { _: DefaultBSONHandlers =>

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

      write(repr).map { seq => new BSONArray(seq) }
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

  implicit def mapReader[K, V](implicit keyReader: BSONReader[K], valueReader: BSONReader[V]): BSONDocumentReader[Map[K, V]] =
    new BSONDocumentReader[Map[K, V]] {
      def readDocument(doc: BSONDocument): Try[Map[K, V]] = {
        val builder = Map.newBuilder[K, V]

        @annotation.tailrec
        def parse(entries: Seq[(String, BSONValue)]): Try[Map[K, V]] =
          entries.headOption match {
            case Some((k, v)) => (for {
              key <- keyReader.readTry(BSONString(k))
              vlu <- valueReader.readTry(v)
            } yield (key -> vlu)) match {
              case Success(entry) => {
                builder += entry
                parse(entries.tail)
              }

              case Failure(cause) => Failure(cause)
            }

            case _ => Success(builder.result())
          }

        parse(doc.fields.toSeq)
      }
    }

  implicit def mapWriter[V](implicit valueWriter: BSONWriter[V]): BSONDocumentWriter[Map[String, V]] = new BSONDocumentWriter[Map[String, V]] {
    def writeTry(inputMap: Map[String, V]): Try[BSONDocument] = {
      val m = HashMap.newBuilder[String, BSONValue]

      @annotation.tailrec
      def write(entries: Map[String, V]): Try[HashMap[String, BSONValue]] =
        entries.headOption match {
          case Some((k, v)) => (valueWriter.writeTry(v).map { vlu =>
            m += BSONStringHandler.safeWrite(k).value -> vlu
            ()
          }) match {
            case Success(_) => write(entries.tail)
            case Failure(cause) => Failure(cause)
          }

          case _ => Success(m.result())
        }

      write(inputMap).map(BSONDocument(_))
    }
  }

}

private[bson] trait LowerPriorityBSONHandlers { _: DefaultBSONHandlers =>
  implicit def mapKeyWriter[K, V](
    implicit
    ev: K => StringOps,
    valueWriter: BSONWriter[V]): BSONDocumentWriter[Map[K, V]] =
    new BSONDocumentWriter[Map[K, V]] {
      def writeTry(inputMap: Map[K, V]): Try[BSONDocument] = {
        val m = HashMap.newBuilder[String, BSONValue]

        @annotation.tailrec
        def write(entries: Map[K, V]): Try[HashMap[String, BSONValue]] =
          entries.headOption match {
            case Some((k, v)) =>
              (valueWriter.writeTry(v).map { vlu =>
                val key = BSONStringHandler.safeWrite(ev(k).mkString)

                m += (key).value -> vlu
                ()
              }) match {
                case Success(_) => write(entries.tail)
                case Failure(cause) => Failure(cause)
              }

            case _ => Success(m.result())
          }

        write(inputMap).map(BSONDocument(_))
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
