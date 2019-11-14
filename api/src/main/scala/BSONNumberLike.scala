package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

import scala.math.Numeric

/**
 * A BSON value that can be seen as a number.
 *
 * Conversions:
 *   - [[BSONDateTime]]
 *   - [[BSONDecimal]]
 *   - [[BSONDouble]]
 *   - [[BSONInteger]]
 *   - [[BSONLong]]
 *   - [[BSONTimestamp]]
 */
sealed trait BSONNumberLike {
  private[bson] def underlying: BSONValue

  /** Converts this number into an `Int`. */
  def toInt: Try[Int]

  /** Converts this number into a `Long`. */
  def toLong: Try[Long]

  /** Converts this number into a `Float`. */
  def toFloat: Try[Float]

  /** Converts this number into a `Double`. */
  def toDouble: Try[Double]

  override def equals(that: Any): Boolean = that match {
    case other: BSONNumberLike => underlying == other.underlying
    case _ => false
  }

  @inline override def hashCode: Int = underlying.hashCode

  override def toString = s"BSONNumberLike($underlying)"
}

private[bson] sealed trait HasNumeric[A] {
  private[bson] def number: ExtendedNumeric[A]
}

private[bson] sealed trait IsNumeric[A] extends HasNumeric[A] {
  def toInt = number { _ toInt _ }
  def toLong = number { _ toLong _ }
  def toFloat = number { _ toFloat _ }
  def toDouble = number { _ toDouble _ }
}

private[bson] class ExtendedNumeric[A: Numeric](underlying: Try[A]) {
  lazy val numeric = implicitly[Numeric[A]]

  def apply[B](f: (Numeric[A], A) => B): Try[B] =
    underlying.map { f(numeric, _) }
}

private[bson] object ExtendedNumeric {
  @inline def pure[A: Numeric](value: A): ExtendedNumeric[A] =
    new ExtendedNumeric[A](Success(value))
}

object BSONNumberLike {
  implicit class BSONDateTimeNumberLike(
    private[bson] val underlying: BSONDateTime)
    extends BSONNumberLike with IsNumeric[Long] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value)
  }

  implicit final class BSONDecimalNumberLike(
    private[bson] val underlying: BSONDecimal)
    extends BSONNumberLike with IsNumeric[BigDecimal] {
    private[bson] lazy val number = new ExtendedNumeric(
      BSONDecimal.toBigDecimal(underlying))
  }

  implicit class BSONDoubleNumberLike(
    private[bson] val underlying: BSONDouble)
    extends BSONNumberLike with IsNumeric[Double] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value)
  }

  implicit class BSONIntegerNumberLike(
    private[bson] val underlying: BSONInteger)
    extends BSONNumberLike with IsNumeric[Int] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value)
  }

  implicit class BSONLongNumberLike(
    private[bson] val underlying: BSONLong)
    extends BSONNumberLike with IsNumeric[Long] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value)
  }

  implicit class BSONTimestampNumberLike(
    private[bson] val underlying: BSONTimestamp)
    extends BSONNumberLike with IsNumeric[Long] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value)
  }

  // ---

  implicit object BSONNumberLikeHandler extends BSONHandler[BSONNumberLike] {
    def readTry(bson: BSONValue): Try[BSONNumberLike] = bson match {
      case i: BSONInteger => Success(BSONIntegerNumberLike(i))
      case l: BSONLong => Success(BSONLongNumberLike(l))
      case d: BSONDouble => Success(BSONDoubleNumberLike(d))
      case dt: BSONDateTime => Success(BSONDateTimeNumberLike(dt))
      case ts: BSONTimestamp => Success(BSONTimestampNumberLike(ts))
      case dec: BSONDecimal => Success(BSONDecimalNumberLike(dec))

      case _ => Failure(
        exceptions.TypeDoesNotMatchException(
          "<number>", bson.getClass.getSimpleName))
    }

    def writeTry(number: BSONNumberLike): Try[BSONValue] =
      Success(number.underlying)
  }
}
