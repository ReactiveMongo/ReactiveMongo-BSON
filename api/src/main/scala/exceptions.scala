package reactivemongo.api.bson.exceptions

import scala.util.control.NoStackTrace

/** Formerly `DocumentKeyNotFoundException` */
final class BSONValueNotFoundException private[exceptions] (
  k: => String,
  private val message: String) extends Exception with NoStackTrace {

  /** The key which is not found, either a field name or index. */
  @inline def key: String = k

  override def getMessage = message

  override def equals(that: Any): Boolean = that match {
    case other: BSONValueNotFoundException =>
      message == other.message

    case _ =>
      false
  }

  override def hashCode: Int = message.hashCode
}

object BSONValueNotFoundException {
  import reactivemongo.api.bson.{ BSONArray, BSONDocument }

  private[api] def apply(name: String, parent: BSONDocument): BSONValueNotFoundException = new BSONValueNotFoundException(name, s"The key '$name' could not be found in document ${BSONDocument pretty parent}")

  private[api] def apply(index: Int, parent: BSONArray): BSONValueNotFoundException = new BSONValueNotFoundException(index.toString, s"The key '#$index' could not be found in array ${BSONArray pretty parent}")

  /** Extract the not found key (either a field name or an index). */
  private[api] def unapply(cause: Throwable): Option[String] = cause match {
    case notFound: BSONValueNotFoundException => Option(notFound.key)
    case _ => Option.empty[String]
  }
}

/**
 * Indicates that the type of a read value doesn't match the expected one.
 */
final class TypeDoesNotMatchException private[api] (
  val expected: String,
  val actual: String) extends Exception with NoStackTrace {
  override val getMessage = s"$actual != $expected"

  private[api] lazy val tupled = expected -> actual

  override def equals(that: Any): Boolean = that match {
    case other: TypeDoesNotMatchException =>
      this.tupled == other.tupled

    case _ =>
      false
  }

  override def hashCode: Int = tupled.hashCode

  override def toString: String = s"TypeDoesNotMatchException${tupled.toString}"
}

/** [[TypeDoesNotMatchException]] factories */
object TypeDoesNotMatchException {
  /**
   * Defines a type exception.
   *
   * @param expected the name of the expected type
   * @param actual the name of the actual type
   *
   * {{{
   * reactivemongo.api.bson.exceptions.
   *   TypeDoesNotMatchException("LocalDate", "String")
   * }}}
   */
  def apply(expected: String, actual: String): TypeDoesNotMatchException =
    new TypeDoesNotMatchException(expected, actual)

  /**
   * Extracts expected and actual types from the type exception.
   *
   * {{{
   * import reactivemongo.api.bson.exceptions.TypeDoesNotMatchException
   *
   * def foo(cause: Exception) = cause match {
   *   case TypeDoesNotMatchException(expected, actual) =>
   *     println(expected + " -> " + actual)
   *
   *   case _ =>
   * }
   * }}}
   */
  def unapply(exception: Exception): Option[(String, String)] =
    exception match {
      case x: TypeDoesNotMatchException => Some(x.expected -> x.actual)
      case _ => None
    }
}

/** Indicates that a read value doesn't match an expected one */
final class ValueDoesNotMatchException private[api] (
  val actual: String) extends Exception with NoStackTrace {
  override val getMessage = s"Value doesn't match: $actual"

  override def equals(that: Any): Boolean = that match {
    case other: ValueDoesNotMatchException =>
      this.actual == other.actual

    case _ =>
      false
  }

  @inline override def hashCode: Int = actual.hashCode

  override def toString = s"ValueDoesNotMatchException($actual)"
}

/** [[ValueDoesNotMatchException]] factories */
object ValueDoesNotMatchException {
  /**
   * {{{
   * reactivemongo.api.bson.exceptions.
   *   ValueDoesNotMatchException("unsupported")
   * }}}
   */
  def apply(actual: String): ValueDoesNotMatchException =
    new ValueDoesNotMatchException(actual)

  /**
   * Extracts expected and actual types from the type exception.
   *
   * {{{
   * import reactivemongo.api.bson.exceptions.ValueDoesNotMatchException
   *
   * def foo(cause: Exception) = cause match {
   *   case ValueDoesNotMatchException(actual) =>
   *     println(actual)
   *
   *   case _ =>
   * }
   * }}}
   */
  def unapply(exception: Exception): Option[String] = exception match {
    case x: ValueDoesNotMatchException => Some(x.actual)
    case _ => None
  }
}

/** Exception from a BSON reader/writer. */
final class HandlerException private[bson] (
  val expression: String) extends Exception with NoStackTrace {

  override lazy val getMessage: String = getCause match {
    case null =>
      s"Fails to handle '${expression}'"

    case cause =>
      s"Fails to handle '${expression}': ${cause.getMessage}"
  }

  def suppress(other: Iterable[HandlerException]): HandlerException =
    other.foldLeft(this) { (m, o) => m.addSuppressed(o); m }
}

object HandlerException {
  @inline def apply(expression: String): HandlerException =
    new HandlerException(expression)

  @inline def apply(expression: String, cause: Throwable): HandlerException = {
    val ex = new HandlerException(expression)
    ex.initCause(cause)
    ex
  }
}
