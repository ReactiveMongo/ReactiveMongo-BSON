package reactivemongo.api.bson

import java.util.{ Locale, UUID }

import scala.util.{ Success, Try }

/**
 * Mapping from a BSON string to `T`.
 *
 * {{{
 * final class Foo(val v: String) extends AnyVal
 *
 * val dict = Map[Foo, Int](
 *   (new Foo("key") -> 1),
 *   (new Foo("name") -> 2))
 *
 * import reactivemongo.api.bson.KeyWriter
 *
 * implicit def fooKeyWriter: KeyWriter[Foo] =
 *   KeyWriter[Foo] { foo =>
 *     "foo:" + foo.v
 *   }
 *
 * reactivemongo.api.bson.BSON.writeDocument(dict)
 * // Success = {'foo:key': 1, 'foo:name': 2}
 * }}}
 */
trait KeyWriter[T] {
  def writeTry(key: T): Try[String]
}

/** [[KeyWriter]] factories */
object KeyWriter extends LowPriorityKeyWriter {
  /**
   * Creates a [[KeyWriter]] based on the given `write` function.
   */
  def apply[T](write: T => String): KeyWriter[T] =
    new FunctionalWriter[T](write)

  /** Creates a [[KeyWriter]] based on the given safe `write` function. */
  private[bson] def safe[T](write: T => String): KeyWriter[T] =
    new SafeKeyWriter[T](write)

  /** Creates a [[KeyWriter]] based on the given `writeTry` function. */
  def from[T](writeTry: T => Try[String]): KeyWriter[T] =
    new Default[T](writeTry)

  /**
   * Provides a [[KeyWriter]] instance of any `T` type
   * that can be viewed as a `String`.
   */
  implicit def keyWriter[T](implicit conv: T => String): KeyWriter[T] =
    apply[T](conv)

  /**
   * Supports writing locales as keys,
   * using [[https://tools.ietf.org/html/bcp47 language tag]]
   * as string representation.
   *
   * {{{
   * import reactivemongo.api.bson.KeyWriter
   *
   * implicitly[KeyWriter[java.util.Locale]].writeTry(java.util.Locale.FRANCE)
   * // => Success("fr-FR")
   * }}}
   */
  implicit def localeWriter: KeyWriter[Locale] =
    KeyWriter[Locale](_.toLanguageTag)

  /**
   * Supports writing `UUID` as keys.
   *
   * {{{
   * import reactivemongo.api.bson.KeyWriter
   *
   * implicitly[KeyWriter[java.util.UUID]].writeTry(
   *   java.util.UUID fromString "BDE87A8B-52F6-4345-9BCE-A30F4CB9FCB4")
   * // => Success("BDE87A8B-52F6-4345-9BCE-A30F4CB9FCB4")
   * }}}
   */
  implicit def uuidWriter: KeyWriter[UUID] = KeyWriter[UUID](_.toString)

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
