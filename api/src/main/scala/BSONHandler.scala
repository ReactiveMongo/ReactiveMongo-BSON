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

  final override def beforeRead(f: PartialFunction[BSONValue, BSONValue]): BSONHandler[T] = BSONHandler.provided[T](
    reader = super.beforeRead(f),
    writer = this)

  final override def afterWrite(f: PartialFunction[BSONValue, BSONValue]): BSONHandler[T] = BSONHandler.provided[T](
    reader = this,
    writer = super.afterWrite(f))

  @SuppressWarnings(Array("AsInstanceOf"))
  override def widen[U >: T]: BSONHandler[U] = this.asInstanceOf[BSONHandler[U]]

  @SuppressWarnings(Array("AsInstanceOf"))
  override def narrow[U <: T]: BSONHandler[U] =
    this.asInstanceOf[BSONHandler[U]]
}

/** [[BSONHandler]] factories */
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
   * Returns a BSON handler for a type `T`,
   * provided there are a writer and a reader for it.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONHandler, BSONReader, BSONWriter }
   *
   * def foo[T](
   *   implicit r: BSONReader[T], w: BSONWriter[T]): BSONHandler[T] =
   *   BSONHandler.provided[T]
   * }}}
   */
  implicit def provided[T](implicit reader: BSONReader[T], writer: BSONWriter[T]): BSONHandler[T] = new WrappedHandler(reader, writer)

  /**
   * Creates a [[BSONHandler]] based on the given `read` and `write` functions.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONHandler, BSONInteger }
   *
   * val intToStrCodeHandler = BSONHandler.option[String](
   *   read = {
   *     case BSONInteger(0) => Some("zero")
   *     case BSONInteger(1) => Some("one")
   *     case _ => None
   *   },
   *   write = {
   *     case "zero" => Some(BSONInteger(0))
   *     case "one" => Some(BSONInteger(1))
   *     case _ => None
   *   })
   *
   * intToStrCodeHandler.readTry(BSONInteger(0)) // Success("zero")
   * intToStrCodeHandler.readOpt(BSONInteger(3)) // None (as failed)
   *
   * intToStrCodeHandler.writeTry("one") // Success(BSONInteger(1))
   * intToStrCodeHandler.writeOpt("3") // None (as failed)
   * }}}
   */
  def option[T](
    read: BSONValue => Option[T],
    write: T => Option[BSONValue]): BSONHandler[T] =
    new OptionalHandler(read, write)

  /**
   * Returns a BSON handler for a type `T`,
   * from the given safe `read` and `write` functions.
   *
   * {{{
   * import scala.util.{ Failure, Success }
   * import reactivemongo.api.bson.{ BSONHandler, BSONInteger }
   *
   * case class Foo(age: Int)
   *
   * val handler: BSONHandler[Foo] = BSONHandler.from[Foo](
   *   read = {
   *     case BSONInteger(age) => Success(Foo(age))
   *     case _ => Failure(new IllegalArgumentException())
   *   },
   *   write = { foo => Success(BSONInteger(foo.age)) })
   * }}}
   */
  def from[T](
    read: BSONValue => Try[T],
    write: T => Try[BSONValue]): BSONHandler[T] =
    new DefaultHandler(read, write)

  /**
   * Creates a [[BSONHandler]] based on the given `read` and `write` functions.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONHandler, BSONInteger }
   *
   * val intToStrCodeHandler = BSONHandler.collect[String](
   *   read = {
   *     case BSONInteger(0) => "zero"
   *     case BSONInteger(1) => "one"
   *   },
   *   write = {
   *     case "zero" => BSONInteger(0)
   *     case "one" => BSONInteger(1)
   *   })
   *
   * intToStrCodeHandler.readTry(BSONInteger(0)) // Success("zero")
   * intToStrCodeHandler.readOpt(BSONInteger(3)) // None (as failed)
   *
   * intToStrCodeHandler.writeTry("one") // Success(BSONInteger(1))
   * intToStrCodeHandler.writeOpt("3") // None (as failed)
   * }}}
   */
  def collect[T](
    read: PartialFunction[BSONValue, T],
    write: PartialFunction[T, BSONValue]): BSONHandler[T] =
    new FunctionalHandler(
      { bson =>
        read.lift(bson) getOrElse {
          throw exceptions.ValueDoesNotMatchException(BSONValue pretty bson)
        }
      },
      { v =>
        write.lift(v) getOrElse {
          throw exceptions.ValueDoesNotMatchException(s"${v}")
        }
      })

  // ---

  private[bson] final class OptionalHandler[T](
    read: BSONValue => Option[T],
    val write: T => Option[BSONValue])
    extends BSONReader.OptionalReader[T](read)
    with BSONWriter.OptionalWriter[T] with BSONHandler[T]

  private[bson] final class DefaultHandler[T](
    read: BSONValue => Try[T],
    val write: T => Try[BSONValue])
    extends BSONReader.DefaultReader[T](read)
    with BSONWriter.DefaultWriter[T] with BSONHandler[T]

  private[bson] final class WrappedHandler[T](
    reader: BSONReader[T],
    writer: BSONWriter[T]) extends BSONHandler[T] {
    @inline def readTry(bson: BSONValue): Try[T] = reader.readTry(bson)
    @inline def writeTry(v: T): Try[BSONValue] = writer.writeTry(v)
  }

  private[bson] final class MappedHandler[T, U](
    parent: BSONHandler[T],
    to: T => U,
    from: U => T) extends BSONReader.MappedReader[T, U](parent, to)
    with BSONHandler[U] {
    def writeTry(u: U) = parent.writeTry(from(u))
  }

  private[bson] final class FunctionalHandler[T](
    r: BSONValue => T, val write: T => BSONValue)
    extends BSONReader.FunctionalReader(r)
    with BSONWriter.FunctionalWriter[T] with BSONHandler[T]
}
