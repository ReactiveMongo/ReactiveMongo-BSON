package reactivemongo.api.bson

import scala.collection.Factory

private[bson] trait LowPriorityBSONHandlersCompat {
  self: LowPriority1BSONHandlers =>

  implicit final def collectionReader[M[_], T](implicit f: Factory[T, M[T]], reader: BSONReader[T]): BSONReader[M[T]] = new BSONArrayCollectionReader(f.newBuilder)

}
