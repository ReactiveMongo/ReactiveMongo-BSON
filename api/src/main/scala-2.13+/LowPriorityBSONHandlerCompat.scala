package reactivemongo.api.bson

import scala.language.higherKinds

import scala.collection.Factory

private[bson] trait LowPriorityBSONHandlersCompat {
  self: LowPriorityBSONHandlers =>

  implicit final def collectionReader[M[_], T](implicit f: Factory[T, M[T]], reader: BSONReader[T]): BSONReader[M[T]] = new BSONArrayCollectionReader(f.newBuilder)

}
