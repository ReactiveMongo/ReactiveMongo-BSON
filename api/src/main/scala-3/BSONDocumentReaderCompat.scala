package reactivemongo.api.bson

private[bson] trait BSONDocumentReaderCompat {
  self: BSONDocumentReader.type =>
  inline def derived[T]: BSONDocumentReader[T] = Macros.reader[T]
}
