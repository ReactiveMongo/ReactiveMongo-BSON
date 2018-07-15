package reactivemongo.api.bson

import scala.collection.generic.CanBuildFrom
import scala.util.Try

trait DefaultBSONHandlers {
  import scala.language.higherKinds

  implicit object BSONIntegerHandler extends BSONHandler[BSONInteger, Int] {
    def read(bson: BSONValue) = bson match {
      case BSONInteger(i) => i
      case _              => sys.error("TODO")
    }

    def write(int: Int) = BSONInteger(int)
  }

  implicit object BSONLongHandler extends BSONHandler[BSONLong, Long] {
    def read(bson: BSONValue) = bson match {
      case BSONLong(l) => l
      case _           => sys.error("TODO")
    }

    def write(long: Long) = BSONLong(long)
  }

  implicit object BSONDoubleHandler extends BSONHandler[BSONDouble, Double] {
    def read(bson: BSONValue) = bson match {
      case BSONDouble(d) => d
      case _             => sys.error("TODO")
    }

    def write(double: Double) = BSONDouble(double)
  }

  implicit object BSONDecimalHandler extends BSONHandler[BSONDecimal, BigDecimal] {
    def read(bson: BSONValue): BigDecimal = bson match {
      case d: BSONDecimal => BSONDecimal.toBigDecimal(d).get
      case _              => sys.error("TODO")
    }

    def write(value: BigDecimal) = BSONDecimal.fromBigDecimal(value).get
  }

  implicit object BSONStringHandler extends BSONHandler[BSONString, String] {
    def read(bson: BSONValue) = bson match {
      case BSONString(s) => s
      case _             => sys.error("TODO")
    }

    def write(string: String) = BSONString(string)
  }
  implicit object BSONBooleanHandler extends BSONHandler[BSONBoolean, Boolean] {
    def read(bson: BSONValue) = bson match {
      case BSONBoolean(b) => b
      case _              => sys.error("TODO")
    }

    def write(boolean: Boolean) = BSONBoolean(boolean)
  }

  implicit object BSONBinaryHandler extends BSONHandler[BSONBinary, Array[Byte]] {
    def read(bson: BSONValue) = bson match {
      case bin: BSONBinary => bin.value.duplicate().readArray(bin.value.size)
      case _               => sys.error("TODO")
    }

    def write(xs: Array[Byte]) = BSONBinary(xs, Subtype.GenericBinarySubtype)
  }

  import java.util.Date

  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, Date] {
    def read(bson: BSONValue) = bson match {
      case BSONDateTime(value) => new java.util.Date(value)
      case _                   => sys.error("TODO")
    }

    def write(date: Date) = BSONDateTime(date.getTime)
  }

  // Typeclasses Handlers
  import BSONNumberLike._
  import BSONBooleanLike._

  class BSONNumberLikeReader extends BSONReader[BSONNumberLike] {
    def read(bson: BSONValue): BSONNumberLike = bson match {
      case i: BSONInteger    => BSONIntegerNumberLike(i)
      case l: BSONLong       => BSONLongNumberLike(l)
      case d: BSONDouble     => BSONDoubleNumberLike(d)
      case dt: BSONDateTime  => BSONDateTimeNumberLike(dt)
      case ts: BSONTimestamp => BSONTimestampNumberLike(ts)
      case dec: BSONDecimal  => BSONDecimalNumberLike(dec)
      case _                 => throw new UnsupportedOperationException()
    }
  }

  implicit object BSONNumberLikeWriter extends BSONWriter[BSONNumberLike, BSONValue] {
    def write(number: BSONNumberLike) = number.underlying
  }

  implicit def bsonNumberLikeReader = new BSONNumberLikeReader

  class BSONBooleanLikeReader extends BSONReader[BSONBooleanLike] {
    def read(bson: BSONValue): BSONBooleanLike = bson match {
      case int: BSONInteger      => BSONIntegerBooleanLike(int)
      case double: BSONDouble    => BSONDoubleBooleanLike(double)
      case long: BSONLong        => BSONLongBooleanLike(long)
      case boolean: BSONBoolean  => BSONBooleanBooleanLike(boolean)
      case _: BSONNull.type      => BSONNullBooleanLike(BSONNull)
      case _: BSONUndefined.type => BSONUndefinedBooleanLike(BSONUndefined)
      case dec: BSONDecimal      => BSONDecimalBooleanLike(dec)
      case _                     => throw new UnsupportedOperationException()
    }
  }

  implicit object BSONBooleanLikeWriter extends BSONWriter[BSONBooleanLike, BSONValue] {
    def write(number: BSONBooleanLike) = number.underlying
  }

  implicit def bsonBooleanLikeReader = new BSONBooleanLikeReader

  // Collections Handlers
  class BSONArrayCollectionReader[M[_], T](implicit cbf: CanBuildFrom[M[_], T, M[T]], reader: BSONReader[T]) extends BSONReader[M[T]] {
    @SuppressWarnings(Array("AsInstanceOf", "TryGet")) // TODO: Review
    def read(bson: BSONValue) = bson match {
      case array @ BSONArray(_) => array.elements.map {
        case BSONElement(_, v) => reader.read(v)
      }.to[M]

      case _ => sys.error("TODO")
    }
  }

  class BSONArrayCollectionWriter[T, Repr <% Traversable[T]](implicit writer: BSONWriter[T, _ <: BSONValue]) extends BSONWriter[Repr, BSONArray] {
    def write(repr: Repr) = {
      new BSONArray(repr.map(s => Try(writer.write(s))).to[Stream])
    }
  }

  implicit def collectionToBSONArrayCollectionWriter[T, Repr <% Traversable[T]](implicit writer: BSONWriter[T, _ <: BSONValue]): BSONWriter[Repr, BSONArray] = new BSONArrayCollectionWriter[T, Repr]

  implicit def bsonArrayToCollectionReader[M[_], T](implicit cbf: CanBuildFrom[M[_], T, M[T]], reader: BSONReader[T]): BSONReader[M[T]] = new BSONArrayCollectionReader

  abstract class IdentityBSONConverter[T <: BSONValue](
      implicit
      m: Manifest[T]) extends BSONReader[T] with BSONWriter[T, T] {

    def write(t: T): T = t

    //override def writeOpt(t: T): Option[T] = if (m.runtimeClass.isInstance(t)) Some(t.asInstanceOf[T]) else None

    @SuppressWarnings(Array("AsInstanceOf")) // TODO: Review
    def read(bson: BSONValue) = m.runtimeClass.cast(bson).asInstanceOf[T]

    //override def readOpt(bson: T): Option[T] = if (m.runtimeClass.isInstance(bson)) Some(bson.asInstanceOf[T]) else None
  }

  implicit object BSONStringIdentity extends IdentityBSONConverter[BSONString]

  implicit object BSONIntegerIdentity extends IdentityBSONConverter[BSONInteger]

  implicit object BSONDecimalIdentity
    extends IdentityBSONConverter[BSONDecimal]

  implicit object BSONArrayIdentity extends IdentityBSONConverter[BSONArray]

  implicit object BSONDocumentIdentity
    extends IdentityBSONConverter[BSONDocument]
    //with BSONDocumentReader[BSONDocument]
    with BSONDocumentWriter[BSONDocument]

  implicit object BSONBooleanIdentity extends IdentityBSONConverter[BSONBoolean]

  implicit object BSONLongIdentity extends IdentityBSONConverter[BSONLong]

  implicit object BSONDoubleIdentity extends IdentityBSONConverter[BSONDouble]

  implicit object BSONValueIdentity extends IdentityBSONConverter[BSONValue]

  implicit object BSONObjectIDIdentity extends IdentityBSONConverter[BSONObjectID]

  implicit object BSONBinaryIdentity extends IdentityBSONConverter[BSONBinary]

  implicit object BSONDateTimeIdentity extends IdentityBSONConverter[BSONDateTime]

  implicit object BSONNullIdentity extends IdentityBSONConverter[BSONNull.type]

  implicit object BSONUndefinedIdentity extends IdentityBSONConverter[BSONUndefined.type]

  implicit object BSONRegexIdentity extends IdentityBSONConverter[BSONRegex]

  implicit object BSONJavaScriptIdentity
    extends BSONReader[BSONJavaScript]
    with BSONWriter[BSONJavaScript, BSONJavaScript] {

    def read(b: BSONValue) = b match {
      case v @ BSONJavaScript(_) => v
      case _                     => sys.error("TODO")
    }

    def write(b: BSONJavaScript) = b
  }

  /*
  implicit def findWriter[T](implicit writer: BSONWriter[T, _ <: BSONValue]): BSONWriter[T, _ <: BSONValue] =
    new VariantBSONWriterWrapper(writer)

  implicit def findReader[T](implicit reader: BSONReader[_ <: BSONValue, T]): BSONReader[_ <: BSONValue, T] =
    new VariantBSONReaderWrapper(reader)
   */

  implicit def mapReader[K, V](implicit keyReader: BSONReader[K], valueReader: BSONReader[V]): BSONDocumentReader[Map[K, V]] =
    new BSONDocumentReader[Map[K, V]] {
      def readDocument(bson: BSONDocument): Map[K, V] = {
        @SuppressWarnings(Array("TryGet"))
        def elements = bson.elements.map { element =>
          keyReader.read(BSONString(element.name)) -> element.value.
            seeAsTry[V].get
        }

        elements.toMap
      }
    }

  implicit def mapWriter[K, V](implicit keyWriter: BSONWriter[K, BSONString], valueWriter: BSONWriter[V, _ <: BSONValue]): BSONDocumentWriter[Map[K, V]] =
    new BSONDocumentWriter[Map[K, V]] {
      def write(inputMap: Map[K, V]): BSONDocument = {
        val elements = inputMap.map { tuple =>
          BSONElement(keyWriter.write(tuple._1).value, valueWriter.write(tuple._2))
        }
        BSONDocument(elements)
      }
    }
}

private[bson] final class BSONDocumentHandlerImpl[T](
    r: BSONDocument => T,
    w: T => BSONDocument) extends BSONDocumentReader[T]
  with BSONDocumentWriter[T] with BSONHandler[BSONDocument, T] {

  def readDocument(doc: BSONDocument): T = r(doc)
  def write(value: T): BSONDocument = w(value)
}

object DefaultBSONHandlers extends DefaultBSONHandlers
