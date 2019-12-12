package reactivemongo.api.bson

import scala.util.Try

/**
 * A BSON handler is able to both read and write `T` values
 * from/to BSON representation.
 *
 * {{{
 * import scala.util.Try
 * import reactivemongo.api.bson.{ BSONHandler, BSONValue }
 *
 * def roundtrip[T](value: T)(implicit handler: BSONHandler[T]): Try[Boolean] =
 *   for {
 *     bson: BSONValue <- handler.writeTry(value)
 *     dser <- handler.readTry(bson)
 *   } yield (dser == value) // true
 * }}}
 */
trait BSONHandler[T] extends BSONReader[T] with BSONWriter[T] {
  final def as[R](to: T => R, from: R => T): BSONHandler[R] =
    new BSONHandler.MappedHandler(this, to, from)
}

object BSONHandler {
  /**
   * Handler factory.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONHandler, BSONString }
   *
   * case class Foo(value: String)
   *
   * val foo: BSONHandler[Foo] = BSONHandler(
   *   { _ => Foo("value") },
   *   { f: Foo => BSONString(f.value) }
   * )
   * }}}
   */
  def apply[T](
    read: BSONValue => T,
    write: T => BSONValue): BSONHandler[T] =
    new FunctionalHandler(read, write)

  /**
   * Returns a BSON handler for a type `T`, provided there are
   * a writer and a reader for it, both using the same kind of `BSONValue`.
   */
  implicit def provided[T](implicit reader: BSONReader[T], writer: BSONWriter[T]): BSONHandler[T] = new DefaultHandler(reader, writer)

  // ---

  private[bson] class DefaultHandler[T](
    reader: BSONReader[T],
    writer: BSONWriter[T]) extends BSONHandler[T] {
    def readTry(bson: BSONValue): Try[T] = reader.readTry(bson)
    def writeTry(v: T): Try[BSONValue] = writer.writeTry(v)
  }

  private[bson] class MappedHandler[T, U](
    parent: BSONHandler[T],
    to: T => U,
    from: U => T) extends BSONHandler[U] {
    def writeTry(u: U) = parent.writeTry(from(u))

    def readTry(bson: BSONValue): Try[U] = parent.readTry(bson).map(to)
  }

  private[bson] class FunctionalHandler[T](
    r: BSONValue => T, w: T => BSONValue)
    extends BSONHandler[T] {
    def readTry(bson: BSONValue): Try[T] = Try(r(bson))
    def writeTry(v: T): Try[BSONValue] = Try(w(v))
  }
}
