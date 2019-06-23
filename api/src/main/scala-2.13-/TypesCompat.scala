package reactivemongo.api.bson

private[bson] trait Aliases {
  type StringOps = scala.collection.immutable.StringOps
}

private[bson] trait Utils {
  @inline private[bson] def lazyZip[A, B](a: Iterable[A], b: Iterable[B]) = (a -> b).zipped

  @inline private[bson] def toLazy[T](it: Traversable[T]) = it.toStream
}
