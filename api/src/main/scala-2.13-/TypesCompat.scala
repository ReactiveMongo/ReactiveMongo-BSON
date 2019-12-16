package reactivemongo.api.bson

private[bson] trait Aliases {
  type StringOps = scala.collection.immutable.StringOps
  type BaseColl[T] = Traversable[T]
}

private[bson] trait Utils {
  @inline private[bson] def lazyZip[A, B](a: Iterable[A], b: Iterable[B]) = (a -> b).zipped

  @inline private[bson] def toLazy[T](it: Traversable[T]) = it.toStream

  @inline private[bson] def mapValues[K, V, U](m: Map[K, V])(f: V => U): Map[K, U] = m.mapValues(f)

  @inline private[bson] def filterKeys[K, V](m: Map[K, V])(f: K => Boolean): Map[K, V] = m.filterKeys(f)
}
