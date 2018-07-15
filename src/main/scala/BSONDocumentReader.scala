package reactivemongo.api.bson

trait BSONDocumentReader[T] extends BSONReader[T] {
  final def read(bson: BSONValue): T = bson match {
    case doc @ BSONDocument(_) => readDocument(doc)
    case _                     => sys.error("TODO")
  }

  def readDocument(doc: BSONDocument): T
}

object BSONDocumentReader {
  private class Default[T](
      _read: BSONDocument => T) extends BSONDocumentReader[T] {

    def readDocument(doc: BSONDocument): T = _read(doc)
  }

  def apply[T](read: BSONDocument => T): BSONDocumentReader[T] =
    new Default[T](read)
}
