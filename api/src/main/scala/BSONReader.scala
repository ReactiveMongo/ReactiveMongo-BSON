package reactivemongo.api.bson

import scala.util.Try

sealed trait UnsafeBSONReader[T] {
  def readTry(value: BSONValue): Try[T]
}

/**
 * A reader that produces an instance of `T` from a subtype of [[BSONValue]].
 */
trait BSONReader[T] { self =>

  /**
   * Reads a BSON value and produce an instance of `T`.
   *
   * This method may throw exceptions at runtime.
   * If used outside a reader, one should consider `readTry(bson: B): Try[T]` or `readOpt(bson: B): Option[T]`.
   */
  def read(bson: BSONValue): T // TODO: Keep only readTry

  /** Tries to produce an instance of `T` from the `bson` value, returns `None` if an error occurred. */
  def readOpt(bson: BSONValue): Option[T] = readTry(bson).toOption

  /** Tries to produce an instance of `T` from the `bson` value. */
  def readTry(bson: BSONValue): Try[T] = Try(read(bson))

  /**
   * Returns a BSON reader that returns the result of applying `f`
   * on the result of this reader.
   *
   * @param f the function to apply
   */
  final def afterRead[U](f: T => U): BSONReader[U] =
    BSONReader[U]((read _) andThen f)

  final def beforeRead(f: PartialFunction[BSONValue, BSONValue]): BSONReader[T] =
    BSONReader.collect[T](f andThen (read _))

  /*
  private[reactivemongo] def widenReader[U >: T]: UnsafeBSONReader[U] =
    new UnsafeBSONReader[U] {
      @SuppressWarnings(Array("AsInstanceOf", "TryGet")) // TODO: Review
      def readTry(value: BSONValue): Try[U] =
        Try(value.asInstanceOf[B]) match {
          case Failure(_) => Failure(exceptions.TypeDoesNotMatchException(
            s"Cannot convert $value: ${value.getClass} with ${self.getClass}"))

          case Success(bson) => self.readTry(bson)
        }
    }
   */
}

object BSONReader {
  def apply[T](read: BSONValue => T): BSONReader[T] = new Default[T](read)

  def collect[T](read: PartialFunction[BSONValue, T]): BSONReader[T] =
    new Default[T]({ bson =>
      read.lift(bson).getOrElse {
        sys.error(s"TODO: Unexpected BSON value: $bson")
      }
    })

  // ---

  private class Default[T](_read: BSONValue => T) extends BSONReader[T] {
    def read(bson: BSONValue): T = _read(bson)
  }
}
