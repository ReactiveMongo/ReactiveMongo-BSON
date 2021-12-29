package reactivemongo.api.bson

import scala.util.Try

/** Utility functions */
object BSON {

  /**
   * Produces a `T` instance of the given BSON value,
   * if there is a corresponding [[BSONReader]] in the implicit scope.
   *
   * Prefer `readDocument` over this one
   * if you want to deserialize `BSONDocuments`.
   */
  def read[T](bson: BSONValue)(implicit reader: BSONReader[T]): Try[T] =
    reader.readTry(bson)

  /**
   * Produces a `BSONValue` instance of the given `T` value,
   * if there is a corresponding [[BSONWriter]] in the implicit scope.
   *
   * Prefer `writeDocument` over this one
   * if you want to serialize `T` instances.
   */
  def write[T](t: T)(implicit writer: BSONWriter[T]): Try[BSONValue] =
    writer.writeTry(t)

  /**
   * Produces a `T` instance of the given `BSONDocument`,
   * if there is a corresponding [[BSONReader]]
   * in the implicit scope.
   */
  def readDocument[T](
      doc: BSONDocument
    )(implicit
      reader: BSONDocumentReader[T]
    ): Try[T] = reader.readTry(doc)

  /**
   * Produces a `BSONDocument` of the given `T` instance,
   * if there is an implicit [[BSONWriter]]` in the implicit scope.
   */
  def writeDocument[T](
      t: T
    )(implicit
      writer: BSONDocumentWriter[T]
    ): Try[BSONDocument] = writer.writeTry(t)

}
