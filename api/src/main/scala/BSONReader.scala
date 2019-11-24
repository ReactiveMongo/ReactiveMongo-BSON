package reactivemongo.api.bson

import scala.util.Try

import scala.util.control.NonFatal

/**
 * A reader that produces an instance of `T` from a subtype of [[BSONValue]].
 */
trait BSONReader[T] { self =>

  /** Tries to produce an instance of `T` from the `bson` value. */
  def readTry(bson: BSONValue): Try[T]
  // TODO: read[M[_]] ?

  /**
   * Tries to produce an instance of `T` from the `bson` value,
   * returns `None` if an error occurred.
   */
  def readOpt(bson: BSONValue): Option[T] = readTry(bson).toOption

  /**
   * Returns a [[BSONReader]] that returns the result of applying `f`
   * on the result of this reader.
   *
   * @param f the function to apply
   */
  def afterRead[U](f: T => U): BSONReader[U] =
    new BSONReader.MappedReader[T, U](self, f)

  final def beforeRead(f: PartialFunction[BSONValue, BSONValue]): BSONReader[T] = new BSONReader[T] {
    val underlying = BSONReader.collect(f)

    def readTry(bson: BSONValue): Try[T] =
      underlying.readTry(bson).flatMap(self.readTry)
  }

  /** Widen this read for compatible type `U`. */
  final def widen[U >: T]: BSONReader[U] = new BSONReader[U] {
    def readTry(bson: BSONValue): Try[U] =
      self.readTry(bson).map(identity[U])
  }
}

object BSONReader {
  /** Creates a [[BSONReader]] based on the given `read` function. */
  def apply[T](read: BSONValue => T): BSONReader[T] =
    new FunctionalReader[T](read)

  /** Creates a [[BSONReader]] based on the given `read` function. */
  def from[T](read: BSONValue => Try[T]): BSONReader[T] =
    new Default[T](read)

  /** Creates a [[BSONReader]] based on the given partial function. */
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
