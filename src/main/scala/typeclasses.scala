package reactivemongo.api.bson

import scala.util.{ Success, Try }

import scala.math.Numeric

sealed trait BSONNumberLikeClass[B <: BSONValue] extends BSONNumberLike

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
sealed trait BSONNumberLike { self: BSONNumberLikeClass[_] =>
  private[bson] def underlying: BSONValue

  /** Converts this number into an `Int`. */
  def toInt: Try[Int]

  /** Converts this number into a `Long`. */
  def toLong: Try[Long]

  /** Converts this number into a `Float`. */
  def toFloat: Try[Float]

  /** Converts this number into a `Double`. */
  def toDouble: Try[Double]
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

private[bson] sealed trait IsBooleanLike[A] extends HasNumeric[A] {
  def toBoolean: Try[Boolean] = number { (numeric, v) =>
    numeric.compare(v, numeric.zero) != 0
  }
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
    extends BSONNumberLikeClass[BSONDateTime] with IsNumeric[Long] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value)
  }

  implicit final class BSONDecimalNumberLike(
      private[bson] val underlying: BSONDecimal)
    extends BSONNumberLikeClass[BSONDecimal] with IsNumeric[BigDecimal] {
    private[bson] lazy val number = new ExtendedNumeric(
      BSONDecimal.toBigDecimal(underlying))
  }

  implicit class BSONDoubleNumberLike(
      private[bson] val underlying: BSONDouble)
    extends BSONNumberLikeClass[BSONDouble] with IsNumeric[Double] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value)
  }

  implicit class BSONIntegerNumberLike(
      private[bson] val underlying: BSONInteger)
    extends BSONNumberLikeClass[BSONInteger] with IsNumeric[Int] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value)
  }

  implicit class BSONLongNumberLike(
      private[bson] val underlying: BSONLong)
    extends BSONNumberLikeClass[BSONLong] with IsNumeric[Long] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value)
  }

  implicit class BSONTimestampNumberLike(
      private[bson] val underlying: BSONTimestamp)
    extends BSONNumberLikeClass[BSONTimestamp] with IsNumeric[Long] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value * 1000L)
  }
}

/**
 * A BSON value that can be seen as a boolean.
 *
 * Conversions:
 *   - `number = 0 ~> false`
 *   - `number != 0 ~> true`
 *   - `boolean`
 *   - `undefined ~> false`
 *   - `null ~> false`
 */
sealed trait BSONBooleanLike { _: BSONBooleanLikeClass[_] =>
  private[bson] def underlying: BSONValue

  /** Returns the boolean equivalent value */
  def toBoolean: Try[Boolean]
}

sealed trait BSONBooleanLikeClass[B <: BSONValue] extends BSONBooleanLike

object BSONBooleanLike {
  implicit class BSONBooleanBooleanLike(
      private[bson] val underlying: BSONBoolean)
    extends BSONBooleanLikeClass[BSONBoolean] {
    def toBoolean = Success(underlying.value)
    override def toString = s"BSONBooleanBooleanLike($underlying)"
  }

  implicit final class BSONDecimalBooleanLike(
      private[bson] val underlying: BSONDecimal)
    extends BSONBooleanLikeClass[BSONDecimal]
    with IsBooleanLike[BigDecimal] {
    private[bson] lazy val number = new ExtendedNumeric(
      BSONDecimal.toBigDecimal(underlying))
    override def toString = s"BSONDecimalBooleanLike($underlying)"
  }

  implicit class BSONDoubleBooleanLike(
      private[bson] val underlying: BSONDouble)
    extends BSONBooleanLikeClass[BSONDouble] with IsBooleanLike[Double] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value)
    override def toString = s"BSONDoubleBooleanLike($underlying)"
  }

  implicit class BSONNullBooleanLike(
      private[bson] val underlying: BSONNull.type)
    extends BSONBooleanLikeClass[BSONNull.type] {
    val toBoolean = Success(false)
    override def toString = "BSONNullBooleanLike"
  }

  implicit class BSONUndefinedBooleanLike(
      private[bson] val underlying: BSONUndefined.type)
    extends BSONBooleanLikeClass[BSONUndefined.type] {
    val toBoolean = Success(false)
    override def toString = "BSONUndefinedBooleanLike"
  }

  implicit class BSONIntegerBooleanLike(
      private[bson] val underlying: BSONInteger)
    extends BSONBooleanLikeClass[BSONInteger] with IsBooleanLike[Int] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value)
    override def toString = s"BSONIntegerBooleanLike($underlying)"
  }

  implicit class BSONLongBooleanLike(
      private[bson] val underlying: BSONLong)
    extends BSONBooleanLikeClass[BSONDouble] with IsBooleanLike[Long] {
    private[bson] lazy val number = ExtendedNumeric.pure(underlying.value)
    override def toString = s"BSONLongBooleanLike($underlying)"
  }
}
