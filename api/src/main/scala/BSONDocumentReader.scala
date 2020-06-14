package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

trait BSONDocumentReader[T] extends BSONReader[T] { self =>
  final def readTry(bson: BSONValue): Try[T] = bson match {
    case doc: BSONDocument => readDocument(doc)

    case _ => Failure(exceptions.TypeDoesNotMatchException(
      "BSONDocument", bson.getClass.getSimpleName))
  }

  def readDocument(doc: BSONDocument): Try[T]

  final override def afterRead[U](f: T => U): BSONDocumentReader[U] =
    new BSONDocumentReader.MappedReader[T, U](self, f)

  final def beforeRead(f: PartialFunction[BSONDocument, BSONDocument]): BSONDocumentReader[T] = new BSONDocumentReader[T] {
    val underlying = BSONDocumentReader.collect(f)

    def readDocument(doc: BSONDocument): Try[T] =
      underlying.readDocument(doc).flatMap(self.readDocument)
  }
}

object BSONDocumentReader {
  /** Creates a [[BSONDocumentReader]] based on the given `read` function. */
  def apply[T](read: BSONDocument => T): BSONDocumentReader[T] =
    new FunctionalReader[T](read)

  /** Creates a [[BSONDocumentReader]] based on the given `read` function. */
  def option[T](read: BSONDocument => Option[T]): BSONDocumentReader[T] =
    new OptionalReader[T](read)

  /** Creates a [[BSONDocumentReader]] based on the given `read` function. */
  def from[T](read: BSONDocument => Try[T]): BSONDocumentReader[T] =
    new DefaultReader[T](read)

  /** Creates a [[BSONDocumentReader]] based on the given partial function. */
  def collect[T](read: PartialFunction[BSONDocument, T]): BSONDocumentReader[T] = new FunctionalReader[T]({ doc =>
    read.lift(doc) getOrElse {
      throw exceptions.ValueDoesNotMatchException(BSONDocument pretty doc)
    }
  })

  // ---

  private class DefaultReader[T](
    read: BSONDocument => Try[T]) extends BSONDocumentReader[T] {

    def readDocument(doc: BSONDocument): Try[T] = read(doc)
  }

  private class OptionalReader[T](
    read: BSONDocument => Option[T]) extends BSONDocumentReader[T] {

    override def readOpt(bson: BSONValue): Option[T] = bson match {
      case doc: BSONDocument => try {
        read(doc)
      } catch {
        case NonFatal(_) => None
      }

      case _ => None
    }

    override def readOrElse(bson: BSONValue, default: => T): T =
      bson match {
        case doc: BSONDocument => try {
          read(doc).getOrElse(default)
        } catch {
          case NonFatal(_) => default
        }

        case _ => default
      }

    def readDocument(doc: BSONDocument): Try[T] = Try(read(doc)).flatMap {
      case Some(result) => Success(result)

      case _ =>
        Failure(exceptions.ValueDoesNotMatchException(BSONDocument pretty doc))
    }
  }

  private class FunctionalReader[T](
    read: BSONDocument => T) extends BSONDocumentReader[T] {

    def readDocument(doc: BSONDocument): Try[T] = Try(read(doc))
  }

  private[bson] class MappedReader[T, U](
    parent: BSONDocumentReader[T],
    to: T => U) extends BSONDocumentReader[U] {
    def readDocument(doc: BSONDocument): Try[U] =
      parent.readDocument(doc).map(to)
  }
}
