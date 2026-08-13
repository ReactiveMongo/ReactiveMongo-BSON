package reactivemongo.api.bson

private[bson] trait BSONDocumentHandlerCompat {
  self: BSONDocumentHandler.type =>
  inline def derived[T]: BSONDocumentHandler[T] = Macros.handler[T]
}
