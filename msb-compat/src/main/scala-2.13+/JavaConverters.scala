package reactivemongo.api.bson.msb

import scala.jdk.CollectionConverters.{ IterableHasAsScala, SeqHasAsJava }

private[msb] object JavaConverters {

  @inline def iterableAsScalaIterable[A](
      i: java.lang.Iterable[A]
    ): Iterable[A] = IterableHasAsScala(i).asScala

  @inline def seqAsJavaList[A](seq: Seq[A]): java.util.List[A] =
    SeqHasAsJava(seq).asJava
}
