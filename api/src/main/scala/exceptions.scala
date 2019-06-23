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

  def apply(name: String, parent: BSONDocument): BSONValueNotFoundException = new BSONValueNotFoundException(name, s"The key '$name' could not be found in document ${BSONDocument pretty parent}")

  def apply(index: Int, parent: BSONArray): BSONValueNotFoundException = new BSONValueNotFoundException(index.toString, s"The key '#$index' could not be found in array ${BSONArray pretty parent}")

  /** Extract the not found key (either a field name or an index). */
  def unapply(cause: Throwable): Option[String] = cause match {
    case notFound: BSONValueNotFoundException => Option(notFound.key)
    case _ => Option.empty[String]
  }
}

final case class TypeDoesNotMatchException(
  expected: String,
  actual: String) extends Exception with NoStackTrace {
  override val getMessage = s"$actual != $expected"
}

final case class ValueDoesNotMatchException(actual: String) extends Exception with NoStackTrace {
  override val getMessage = s"Value doesn't match: $actual"
}

/** Exception from a BSON reader/writer. */
final class HandlerException private[bson] (
  val expression: String) extends Exception with NoStackTrace {

  override lazy val getMessage: String = getCause match {
    case null =>
      s"Fails to handle ${expression}"

    case cause =>
      s"Fails to handle ${expression}: ${cause.getMessage}"
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
