package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

import scala.util.control.NonFatal

/**
 * A reader that produces an instance of `T` from a subtype of [[BSONValue]].
 */
trait BSONReader[T] { self =>

  /**
   * Tries to produce an instance of `T` from the `bson` value.
   *
   * {{{
   * import scala.util.Try
   * import reactivemongo.api.bson.{ BSONReader, BSONValue }
   *
   * def fromBSON[T](bson: BSONValue)(implicit r: BSONReader[T]): Try[T] =
   *   r.readTry(bson)
   * }}}
   */
  def readTry(bson: BSONValue): Try[T]
  // TODO: read[M[_]] ?

  /**
   * Tries to produce an instance of `T` from the `bson` value,
   * returns `None` if an error occurred.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONReader, BSONValue }
   *
   * def fromBSON[T](bson: BSONValue)(implicit r: BSONReader[T]): Option[T] =
   *   r.readOpt(bson)
   * }}}
   */
  def readOpt(bson: BSONValue): Option[T] = readTry(bson).toOption

  /**
   * Prepares a [[BSONReader]] that returns the result of applying `f`
   * on the result of this reader.
   *
   * {{{
   * import scala.util.Try
   * import reactivemongo.api.bson.{ BSONReader, BSONValue }
   *
   * // Try to return an integer + 1,
   * // from any T that can be read from BSON
   * // and is a numeric type
   * def fromBSON[T](bson: BSONValue)(
   *   implicit r: BSONReader[T], n: Numeric[T]): Try[Int] = {
   *   val r2: BSONReader[Int] = r.afterRead { v => n.toInt(v) + 1 }
   *  r2.readTry(bson)
   * }
   * }}}
   *
   * @param f the function to apply
   */
  def afterRead[U](f: T => U): BSONReader[U] =
    new BSONReader.MappedReader[T, U](self, f)

  /**
   * Prepares a [[BSONReader]] that transforms the input BSON value,
   * using the given `f` function, before passing the transformed BSON value
   * to the current reader.
   *
   * {{{
   * import reactivemongo.api.bson.{
   *   BSONReader, BSONInteger, BSONNull, BSONString
   * }
   *
   * val normalizingReader: BSONReader[Int] =
   *   implicitly[BSONReader[Int]].beforeRead {
   *     case BSONNull => BSONInteger(-1)
   *     case BSONString(s) => BSONInteger(s.size)
   *     // other values are unchanged
   *   }
   *
   * normalizingReader.readOpt(BSONNull) // Some(-1)
   * normalizingReader.readTry(BSONString("foo")) // Success(3)
   * normalizingReader.readOpt(BSONInteger(4)) // unchanged: Some(4)
   * }}}
   */
  final def beforeRead(f: PartialFunction[BSONValue, BSONValue]): BSONReader[T] = new BSONReader[T] {
    val underlying = BSONReader.collect(f)

    def readTry(bson: BSONValue): Try[T] =
      underlying.readTry(bson).flatMap(self.readTry)
  }

  /**
   * Widen this read for compatible type `U`.
   *
   * {{{
   * import reactivemongo.api.bson.BSONReader
   *
   * val listReader: BSONReader[List[String]] =
   *   implicitly[BSONReader[List[String]]]
   *
   * val widenAsSeqReader: BSONReader[Seq[String]] =
   *   listReader.widen[Seq[String]]
   *   // as Seq[String] >: List[String]
   * }}}
   *
   * @tparam U must be a super-type of `T`
   */
  final def widen[U >: T]: BSONReader[U] = new BSONReader[U] {
    def readTry(bson: BSONValue): Try[U] =
      self.readTry(bson).map(identity[U])
  }
}

/** [[BSONReader]] factories */
object BSONReader {
  /**
   * Creates a [[BSONReader]] based on the given `read` function.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONReader, BSONInteger }
   *
   * val intToStrCodeReader = BSONReader[String] {
   *   case BSONInteger(0) => "zero"
   *   case BSONInteger(1) => "one"
   *   case _ => "unknown"
   * }
   *
   * intToStrCodeReader.readTry(BSONInteger(0)) // Success("zero")
   * intToStrCodeReader.readTry(BSONInteger(1)) // Success("one")
   * intToStrCodeReader.readTry(BSONInteger(2)) // Success("unknown")
   * }}}
   *
   * Any `Exception` thrown by the `read` function will be caught.
   */
  def apply[T](read: BSONValue => T): BSONReader[T] =
    new FunctionalReader[T](read)

  /**
   * Creates a [[BSONReader]] based on the given `read` function.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONReader, BSONInteger }
   *
   * val intToStrCodeReader = BSONReader.option[String] {
   *   case BSONInteger(0) => Some("zero")
   *   case BSONInteger(1) => Some("one")
   *   case _ => None
   * }
   *
   * intToStrCodeReader.readTry(BSONInteger(0)) // Success("zero")
   * intToStrCodeReader.readTry(BSONInteger(1)) // Success("one")
   *
   * intToStrCodeReader.readTry(BSONInteger(2))
   * // => Failure(ValueDoesNotMatchException(..))
   *
   * intToStrCodeReader.readOpt(BSONInteger(3)) // None (as failed)
   * }}}
   *
   * @param read the safe function to read BSON values as `T`
   */
  def option[T](read: BSONValue => Option[T]): BSONReader[T] =
    new OptionalReader[T](read)

  /**
   * Creates a [[BSONReader]] based on the given `read` function.
   *
   * {{{
   * import scala.util.{ Failure, Success }
   * import reactivemongo.api.bson.{ BSONReader, BSONInteger }
   *
   * val intToStrCodeReader = BSONReader.from[String] {
   *   case BSONInteger(0) => Success("zero")
   *   case BSONInteger(1) => Success("one")
   *   case _ => Failure(new IllegalArgumentException())
   * }
   *
   * intToStrCodeReader.readTry(BSONInteger(0)) // Success("zero")
   * intToStrCodeReader.readTry(BSONInteger(1)) // Success("one")
   *
   * intToStrCodeReader.readTry(BSONInteger(2))
   * // => Failure(IllegalArgumentException(..))
   *
   * intToStrCodeReader.readOpt(BSONInteger(3)) // None (as failed)
   * }}}
   *
   * @param read the safe function to read BSON values as `T`
   */
  def from[T](read: BSONValue => Try[T]): BSONReader[T] =
    new Default[T](read)

  /**
   * Creates a [[BSONReader]] based on the given partial function.
   *
   * A [[exceptions.ValueDoesNotMatchException]] is returned as `Failure`
   * for any BSON value that is not matched by the `read` function.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONReader, BSONInteger }
   *
   * val intToStrCodeReader = BSONReader.collect[String] {
   *   case BSONInteger(0) => "zero"
   *   case BSONInteger(1) => "one"
   * }
   *
   * intToStrCodeReader.readTry(BSONInteger(0)) // Success("zero")
   * intToStrCodeReader.readTry(BSONInteger(1)) // Success("one")
   *
   * intToStrCodeReader.readTry(BSONInteger(2))
   * // => Failure(ValueDoesNotMatchException(..))
   *
   * intToStrCodeReader.readOpt(BSONInteger(3)) // None (as failed)
   * }}}
   */
  def collect[T](read: PartialFunction[BSONValue, T]): BSONReader[T] =
    new FunctionalReader[T]({ bson =>
      read.lift(bson) getOrElse {
        throw exceptions.ValueDoesNotMatchException(BSONValue pretty bson)
      }
    })

  // ---

  private class Default[T](
    read: BSONValue => Try[T]) extends BSONReader[T] {
    def readTry(bson: BSONValue): Try[T] = read(bson)
  }

  private class OptionalReader[T](
    read: BSONValue => Option[T]) extends BSONReader[T] {
    def readTry(bson: BSONValue): Try[T] = Try(read(bson)).flatMap {
      case Some(result) => Success(result)

      case _ =>
        Failure(exceptions.ValueDoesNotMatchException(BSONValue pretty bson))
    }

    override def readOpt(bson: BSONValue): Option[T] = read(bson)
  }

  private class FunctionalReader[T](
    read: BSONValue => T) extends BSONReader[T] {
    def readTry(bson: BSONValue): Try[T] = Try(read(bson))

    override def readOpt(bson: BSONValue): Option[T] = try {
      Some(read(bson))
    } catch {
      case NonFatal(_) =>
        None
    }
  }

  private[bson] class MappedReader[T, U](
    parent: BSONReader[T],
    to: T => U) extends BSONReader[U] {
    def readTry(bson: BSONValue): Try[U] = parent.readTry(bson).map(to)
  }
}
