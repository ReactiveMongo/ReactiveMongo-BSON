package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

trait BSONDocumentWriter[T] extends BSONWriter[T] { self =>
  override def writeTry(t: T): Try[BSONDocument]

  override def writeOpt(t: T): Option[BSONDocument] = writeTry(t).toOption

  final override def beforeWrite[U](f: U => T): BSONDocumentWriter[U] =
    BSONDocumentWriter.from[U] { u => writeTry(f(u)) }

  final def afterWrite(f: PartialFunction[BSONDocument, BSONDocument]): BSONDocumentWriter[T] = BSONDocumentWriter.from[T] {
    self.writeTry(_).flatMap { before =>
      f.lift(before) match {
        case Some(after) =>
          Success(after)

        case _ =>
          Failure(exceptions.ValueDoesNotMatchException(
            BSONDocument pretty before))
      }
    }
  }

}

object BSONDocumentWriter {
  def apply[T](write: T => BSONDocument): BSONDocumentWriter[T] =
    new FunctionalWriter[T](write)

  def from[T](write: T => Try[BSONDocument]): BSONDocumentWriter[T] =
    new Default[T](write)

  // ---

  private class Default[T](
    write: T => Try[BSONDocument]) extends BSONDocumentWriter[T] {

    def writeTry(value: T): Try[BSONDocument] = write(value)
  }

  private class FunctionalWriter[T](
    write: T => BSONDocument) extends BSONDocumentWriter[T] {

    def writeTry(value: T): Try[BSONDocument] = Try(write(value))
  }
}
