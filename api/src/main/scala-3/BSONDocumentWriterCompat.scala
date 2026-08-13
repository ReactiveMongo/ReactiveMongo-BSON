package reactivemongo.api.bson

private[bson] trait BSONDocumentWriterCompat {
  self: BSONDocumentWriter.type =>
  inline def derived[T]: BSONDocumentWriter[T] = Macros.writer[T]
}
