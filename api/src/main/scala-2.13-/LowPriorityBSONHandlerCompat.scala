package reactivemongo.api.bson

import scala.language.higherKinds

import scala.collection.generic.CanBuildFrom

private[bson] trait LowPriorityBSONHandlersCompat {
  self: LowPriority1BSONHandlers =>

  implicit final def collectionReader[M[_], T](
    implicit
    cbf: CanBuildFrom[M[_], T, M[T]],
    reader: BSONReader[T]): BSONReader[M[T]] = {
    @inline def r = reader
    new BSONArrayCollectionReader[M, T] {
      val reader = r
      def builder() = cbf()
    }
  }
}
