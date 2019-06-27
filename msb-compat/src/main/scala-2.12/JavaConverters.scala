package reactivemongo.api.bson.msb

import scala.collection.JavaConverters.{
  iterableAsScalaIterable => toIterable,
  seqAsJavaList => toJavaList
}

private[msb] object JavaConverters {
  @inline def iterableAsScalaIterable[A](i: java.lang.Iterable[A]): Iterable[A] = toIterable(i)

  @inline def seqAsJavaList[A](seq: Seq[A]): java.util.List[A] = toJavaList(seq)
}
