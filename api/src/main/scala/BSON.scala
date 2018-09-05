package reactivemongo.api.bson

object BSON {
  /**
   * Produces a `T` instance of the given BSON value, if there is an implicit `BSONReader[T]` in the scope.
   *
   * Prefer `readDocument` over this one if you want to deserialize `BSONDocuments`.
   */
  def read[T](bson: BSONValue)(implicit reader: BSONReader[T]): T =
    reader.read(bson)

  /**
   * Produces a `BSONValue` instance of the given `T` value, if there is an implicit `BSONWriter[T, B]` in the scope.
   *
   * Prefer `writeDocument` over this one if you want to serialize `T` instances.
   */
  def write[T, B <: BSONValue](t: T)(implicit writer: BSONWriter[T, B]): B =
    writer.write(t)

  /** Produces a `T` instance of the given `BSONDocument`, if there is an implicit `BSONDocumentReader[T]` in the scope. */
  def readDocument[T](doc: BSONDocument)(implicit reader: BSONDocumentReader[T]): T = reader.read(doc)

  /** Produces a `BSONDocument` of the given `T` instance, if there is an implicit `BSONWriter[T, BSONDocument]` in the scope. */
  def writeDocument[T](t: T)(implicit writer: BSONWriter[T, BSONDocument]): BSONDocument = writer.write(t)

}
