package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

/** Mapping from a BSON string to `T` */
trait KeyReader[T] {
  def readTry(key: String): Try[T]
}

object KeyReader {
  /**
   * Creates a [[KeyReader]] based on the given `read` function.
   * If `read` is safe (no exception), then rather use the [[safe]] factory.
   */
  def apply[T](read: String => T): KeyReader[T] = new FunctionalReader[T](read)

  /** Creates a [[KeyReader]] based on the given safe `read` function. */
  def safe[T](read: String => T): KeyReader[T] = new SafeKeyReader[T](read)

  /** Creates a [[KeyReader]] based on the given `readTry` function. */
  def from[T](readTry: String => Try[T]): KeyReader[T] = new Default[T](readTry)

  /**
   * Provides a [[KeyReader]] instance of any `T` type
   * that can be parsed from a `String`.
   */
  implicit def keyReader[T](implicit conv: String => T): KeyReader[T] =
    apply[T](conv)

  implicit def bigDecimalKeyReader: KeyReader[BigDecimal] =
    apply(BigDecimal.apply)

  implicit def bigIntKeyReader: KeyReader[BigInt] = apply(BigInt.apply)

  implicit def byteKeyReader: KeyReader[Byte] = apply(_.toByte)

  implicit def charKeyReader: KeyReader[Char] = from[Char] { key =>
    if (key.size == 1) {
      Success(key.head)
    } else {
      Failure(exceptions.ValueDoesNotMatchException(
        s"Invalid character: $key"))
    }
  }

  implicit def doubleKeyReader: KeyReader[Double] = apply(_.toDouble)

  implicit def floatKeyReader: KeyReader[Float] = apply(_.toFloat)

  implicit def intKeyReader: KeyReader[Int] = apply(_.toInt)

  implicit def longKeyReader: KeyReader[Long] = apply(_.toLong)

  implicit def shortKeyReader: KeyReader[Short] = apply(_.toShort)

  // ---

  private class Default[T](
    read: String => Try[T]) extends KeyReader[T] {
    def readTry(key: String): Try[T] = read(key)
  }

  private class FunctionalReader[T](
    read: String => T) extends KeyReader[T] {
    def readTry(key: String): Try[T] = Try(read(key))
  }
}

private[bson] final class SafeKeyReader[T](
  read: String => T) extends KeyReader[T] {

  def readTry(key: String): Try[T] = Success(read(key))
}
