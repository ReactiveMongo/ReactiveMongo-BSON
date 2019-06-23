package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

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
sealed trait BSONBooleanLike {
  private[bson] def underlying: BSONValue

  /** Returns the boolean equivalent value */
  def toBoolean: Try[Boolean]

  override def equals(that: Any): Boolean = that match {
    case other: BSONBooleanLike => underlying == other.underlying
    case _ => false
  }

  @inline override def hashCode: Int = underlying.hashCode

  override def toString = s"BSONBooleanLike($underlying)"
}

object BSONBooleanLike {
  implicit object BSONBooleanLikeHandler extends BSONHandler[BSONBooleanLike] {
    def readTry(bson: BSONValue): Try[BSONBooleanLike] = bson match {
      case int: BSONInteger => Success(new BSONIntegerBooleanLike(int))
      case double: BSONDouble => Success(new BSONDoubleBooleanLike(double))
      case long: BSONLong => Success(new BSONLongBooleanLike(long))
      case boolean: BSONBoolean => Success(new BSONBooleanBooleanLike(boolean))
      case _: BSONNull => Success(BSONNullBooleanLike)
      case dec: BSONDecimal => Success(new BSONDecimalBooleanLike(dec))
      case _: BSONUndefined => Success(BSONUndefinedBooleanLike)

      case _ => Failure(exceptions.TypeDoesNotMatchException(
        "<boolean>", bson.getClass.getSimpleName))
    }

    def writeTry(number: BSONBooleanLike): Try[BSONValue] =
      Success(number.underlying)

  }

  // ---

  private class BSONBooleanBooleanLike(
    private[bson] val underlying: BSONBoolean) extends BSONBooleanLike {

    val toBoolean = Success(underlying.value)

    override def equals(that: Any): Boolean = that match {
      case other: BSONBooleanBooleanLike => underlying == other.underlying
      case _ => false
    }

    @inline override def hashCode: Int = underlying.hashCode

    override def toString = s"BSONBooleanBooleanLike($underlying)"
  }

  private final class BSONDecimalBooleanLike(
    private[bson] val underlying: BSONDecimal) extends BSONBooleanLike {

    @inline def toBoolean: Try[Boolean] =
      BSONDecimal.toBigDecimal(underlying).map(_.toInt != 0)

    override def equals(that: Any): Boolean = that match {
      case other: BSONDecimalBooleanLike => underlying == other.underlying
      case _ => false
    }

    @inline override def hashCode: Int = underlying.hashCode

    override def toString = s"BSONDecimalBooleanLike($underlying)"
  }

  private class BSONDoubleBooleanLike(
    private[bson] val underlying: BSONDouble) extends BSONBooleanLike {

    @inline def toBoolean = Success(underlying.value > 0D)

    override def equals(that: Any): Boolean = that match {
      case other: BSONDoubleBooleanLike => underlying == other.underlying
      case _ => false
    }

    @inline override def hashCode: Int = underlying.hashCode

    override def toString = s"BSONDoubleBooleanLike($underlying)"
  }

  private object BSONNullBooleanLike extends BSONBooleanLike {
    @inline private[bson] def underlying = BSONNull

    val toBoolean = Success(false)

    override def equals(that: Any): Boolean = that match {
      case _: BSONNull => true
      case _ => false
    }

    @inline override def hashCode: Int = BSONNull.hashCode

    override def toString = "BSONNullBooleanLike"
  }

  private object BSONUndefinedBooleanLike extends BSONBooleanLike {
    @inline private[bson] def underlying = BSONUndefined

    val toBoolean = Success(false)

    override def equals(that: Any): Boolean = that match {
      case _: BSONUndefined => true
      case _ => false
    }

    @inline override def hashCode: Int = BSONUndefined.hashCode

    override def toString = "BSONUndefinedBooleanLike"
  }

  private class BSONIntegerBooleanLike(
    private[bson] val underlying: BSONInteger) extends BSONBooleanLike {

    @inline def toBoolean = Success(underlying.value != 0)

    override def equals(that: Any): Boolean = that match {
      case other: BSONIntegerBooleanLike => underlying == other.underlying
      case _ => false
    }

    @inline override def hashCode: Int = underlying.hashCode

    override def toString = s"BSONIntegerBooleanLike($underlying)"
  }

  private class BSONLongBooleanLike(
    private[bson] val underlying: BSONLong) extends BSONBooleanLike {

    @inline def toBoolean = Success(underlying.value != 0L)

    override def equals(that: Any): Boolean = that match {
      case other: BSONLongBooleanLike => underlying == other.underlying
      case _ => false
    }

    @inline override def hashCode: Int = underlying.hashCode

    override def toString = s"BSONLongBooleanLike($underlying)"
  }
}
