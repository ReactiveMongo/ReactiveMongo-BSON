package reactivemongo.api.bson

import scala.util.{ Success, Try }

/** Mapping from a BSON string to `T` */
trait KeyWriter[T] {
  def writeTry(key: T): Try[String]
}

object KeyWriter extends LowPriorityKeyWriter {
  /**
   * Creates a [[KeyWriter]] based on the given `write` function.
   * If `write` is safe (no exception), then rather use the [[safe]] factory.
   */
  def apply[T](write: T => String): KeyWriter[T] =
    new FunctionalWriter[T](write)

  /** Creates a [[KeyWriter]] based on the given safe `write` function. */
  def safe[T](write: T => String): KeyWriter[T] = new SafeKeyWriter[T](write)

  /** Creates a [[KeyWriter]] based on the given `writeTry` function. */
  def from[T](writeTry: T => Try[String]): KeyWriter[T] =
    new Default[T](writeTry)

  /**
   * Provides a [[KeyWriter]] instance of any `T` type
   * that can be viewed as a `String`.
   */
  implicit def keyWriter[T](implicit conv: T => String): KeyWriter[T] =
    apply[T](conv)

  // ---

  private class Default[T](
    write: T => Try[String]) extends KeyWriter[T] {
    def writeTry(key: T): Try[String] = write(key)
  }

  private class FunctionalWriter[T](
    write: T => String) extends KeyWriter[T] {
    def writeTry(key: T): Try[String] = Try(write(key))
  }
}

private[bson] sealed trait LowPriorityKeyWriter { _: KeyWriter.type =>
  implicit def anyValKeyWriter[T <: AnyVal]: KeyWriter[T] =
    safe[T](_.toString)
}

private[bson] final class SafeKeyWriter[T](
  val write: T => String) extends KeyWriter[T] {

  def writeTry(key: T): Try[String] = Success(write(key))
}
