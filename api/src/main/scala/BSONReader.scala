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
   * Tries to produce an instance of `T` from the `bson` value,
   * returns the `default` value if an error occurred.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONReader, BSONValue }
   *
   * def fromBSON[T](bson: BSONValue, v: T)(implicit r: BSONReader[T]): T =
   *   r.readOrElse(bson, v)
   * }}}
   */
  def readOrElse(bson: BSONValue, default: => T): T =
    readTry(bson).getOrElse(default)

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
  def beforeRead(f: PartialFunction[BSONValue, BSONValue]): BSONReader[T] =
    new BSONReader[T] {
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
  def widen[U >: T]: BSONReader[U] = new BSONReader[U] {
    def readTry(bson: BSONValue): Try[U] =
      self.readTry(bson).map(identity[U])
  }
}

/** [[BSONReader]] factories */
object BSONReader extends BSONReaderCompat {
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
    new DefaultReader[T](read)

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

  /**
   * '''EXPERIMENTAL:''' (API may change without notice)
   *
   * Creates a [[BSONReader]] accepting only [[BSONArray]],
   * and applying the given safe `read` function to each element value.
   *
   * {{{
   * import reactivemongo.api.bson.BSONReader
   *
   * def foo(elmReader: BSONReader[(String, Int)]): BSONReader[Seq[(String, Int)]] = BSONReader.sequence(elmReader.readTry _)
   * }}}
   */
  def sequence[T](read: BSONValue => Try[T]): BSONReader[Seq[T]] =
    iterable[T, Seq](read)

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentReader]] that reads
   * the [[BSONArray]] elements.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONArray, BSONReader }
   *
   * val reader = BSONReader.tuple2[String, Int]
   *
   * val arr = BSONArray("Foo", 20)
   *
   * reader.readTry(arr) // => Success(("Foo", 20))
   * }}}
   */
  def tuple2[A: BSONReader, B: BSONReader]: BSONReader[(A, B)] =
    from[(A, B)] {
      case BSONArray(v1 +: v2 +: _) => for {
        _1 <- v1.asTry[A]
        _2 <- v2.asTry[B]
      } yield Tuple2(_1, _2)

      case bson =>
        Failure(exceptions.ValueDoesNotMatchException(BSONValue pretty bson))
    }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentReader]] that reads
   * the [[BSONArray]] elements.
   *
   * @see [[tuple2]]
   */
  def tuple3[A: BSONReader, B: BSONReader, C: BSONReader]: BSONReader[(A, B, C)] = from[(A, B, C)] {
    case BSONArray(v1 +: v2 +: v3 +: _) => for {
      _1 <- v1.asTry[A]
      _2 <- v2.asTry[B]
      _3 <- v3.asTry[C]
    } yield Tuple3(_1, _2, _3)

    case bson =>
      Failure(exceptions.ValueDoesNotMatchException(BSONValue pretty bson))
  }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentReader]] that reads
   * the [[BSONArray]] elements.
   *
   * @see [[tuple2]]
   */
  def tuple4[A: BSONReader, B: BSONReader, C: BSONReader, D: BSONReader]: BSONReader[(A, B, C, D)] = from[(A, B, C, D)] {
    case BSONArray(v1 +: v2 +: v3 +: v4 +: _) => for {
      _1 <- v1.asTry[A]
      _2 <- v2.asTry[B]
      _3 <- v3.asTry[C]
      _4 <- v4.asTry[D]
    } yield Tuple4(_1, _2, _3, _4)

    case bson =>
      Failure(exceptions.ValueDoesNotMatchException(BSONValue pretty bson))
  }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentReader]] that reads
   * the [[BSONArray]] elements.
   *
   * @see [[tuple2]]
   */
  def tuple5[A: BSONReader, B: BSONReader, C: BSONReader, D: BSONReader, E: BSONReader]: BSONReader[(A, B, C, D, E)] = from[(A, B, C, D, E)] {
    case BSONArray(v1 +: v2 +: v3 +: v4 +: v5 +: _) => for {
      _1 <- v1.asTry[A]
      _2 <- v2.asTry[B]
      _3 <- v3.asTry[C]
      _4 <- v4.asTry[D]
      _5 <- v5.asTry[E]
    } yield Tuple5(_1, _2, _3, _4, _5)

    case bson =>
      Failure(exceptions.ValueDoesNotMatchException(BSONValue pretty bson))
  }

  // ---

  private[bson] class DefaultReader[T](
    read: BSONValue => Try[T]) extends BSONReader[T] {
    def readTry(bson: BSONValue): Try[T] = read(bson)
  }

  private[bson] class OptionalReader[T](
    read: BSONValue => Option[T]) extends BSONReader[T] {
    def readTry(bson: BSONValue): Try[T] = Try(read(bson)).flatMap {
      case Some(result) => Success(result)

      case _ =>
        Failure(exceptions.ValueDoesNotMatchException(BSONValue pretty bson))
    }

    override def readOpt(bson: BSONValue): Option[T] = read(bson)

    override def readOrElse(bson: BSONValue, default: => T): T =
      read(bson).getOrElse(default)
  }

  private[bson] class FunctionalReader[T](
    read: BSONValue => T) extends BSONReader[T] {
    def readTry(bson: BSONValue): Try[T] = Try(read(bson))

    override def readOpt(bson: BSONValue): Option[T] = try {
      Some(read(bson))
    } catch {
      case NonFatal(_) =>
        None
    }

    override def readOrElse(bson: BSONValue, default: => T): T = try {
      read(bson)
    } catch {
      case NonFatal(_) =>
        default
    }
  }

  private[bson] class MappedReader[T, U](
    parent: BSONReader[T],
    to: T => U) extends BSONReader[U] {
    def readTry(bson: BSONValue): Try[U] = parent.readTry(bson).map(to)
  }
}
