package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

/**
 * A writer that produces a subtype of [[BSONValue]] from an instance of `T`.
 */
trait BSONWriter[T] {
  /** Tries to produce a BSON value from an instance of `T`. */
  def writeTry(t: T): Try[BSONValue]

  /**
   * Tries to produce a BSON value from an instance of `T`,
   * returns `None` if an error occurred.
   */
  def writeOpt(t: T): Option[BSONValue] = writeTry(t).toOption

  /**
   * Prepares a BSON writer that returns the result of applying `f`
   * on the BSON value from this writer.
   *
   * If the `f` function is not defined for a [[BSONValue]],
   * it will results in a `Failure`.
   *
   * @param f the partial function to apply
   */
  final def afterWrite(f: PartialFunction[BSONValue, BSONValue]): BSONWriter[T] = BSONWriter.from[T] {
    writeTry(_).flatMap { before =>
      f.lift(before) match {
        case Some(after) =>
          Success(after)

        case _ =>
          Failure(exceptions.ValueDoesNotMatchException(
            BSONValue pretty before))
      }
    }
  }

  /**
   * Prepares a BSON writer that converts the input before calling
   * the current writer.
   *
   * @param f the function apply the `U` input value to convert at `T` value used to the current writer
   */
  def beforeWrite[U](f: U => T): BSONWriter[U] =
    BSONWriter.from[U] { u => writeTry(f(u)) }
}

object BSONWriter {
  /** Creates a [[BSONWriter]] based on the given `write` function. */
  def apply[T](write: T => BSONValue): BSONWriter[T] =
    new FunctionalWriter[T](write)

  /** Creates a [[BSONWriter]] based on the given `write` function. */
  def from[T](write: T => Try[BSONValue]): BSONWriter[T] =
    new DefaultWriter[T](write)

  // ---

  private class DefaultWriter[T](
    write: T => Try[BSONValue]) extends BSONWriter[T] {
    def writeTry(value: T): Try[BSONValue] = write(value)
  }

  private class FunctionalWriter[T](
    write: T => BSONValue) extends BSONWriter[T] {
    def writeTry(value: T): Try[BSONValue] = Try(write(value))
  }
}

/** A write that is safe, as `writeTry` can always return a `Success`. */
private[bson] trait SafeBSONWriter[T] { writer: BSONWriter[T] =>
  def safeWrite(value: T): BSONValue

  final def writeTry(value: T): Success[BSONValue] = Success(safeWrite(value))
}

private[bson] object SafeBSONWriter {
  @com.github.ghik.silencer.silent
  def unapply[T](w: BSONWriter[T]): Option[SafeBSONWriter[T]] = w match {
    case s: SafeBSONWriter[T] => Some(s)
    case _ => None
  }
}
