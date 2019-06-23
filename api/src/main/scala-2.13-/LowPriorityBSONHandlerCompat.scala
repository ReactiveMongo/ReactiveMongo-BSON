package reactivemongo.api.bson

import scala.language.higherKinds

import scala.collection.generic.CanBuildFrom

private[bson] trait LowPriorityBSONHandlersCompat {
  self: LowPriorityBSONHandlers =>

  implicit final def collectionReader[M[_], T](implicit cbf: CanBuildFrom[M[_], T, M[T]], reader: BSONReader[T]): BSONReader[M[T]] = new BSONArrayCollectionReader(cbf())

}
