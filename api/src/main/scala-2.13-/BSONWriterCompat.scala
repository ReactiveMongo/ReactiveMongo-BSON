package reactivemongo.api.bson

import scala.language.higherKinds

import scala.util.Try

private[bson] trait BSONWriterCompat { self: BSONWriter.type =>
  /**
   * '''EXPERIMENTAL:''' (API may change without notice)
   *
   * Creates a [[BSONWriter]] accepting only [[scala.collection.Iterable]],
   * and applying the given safe `write` function to each element value.
   */
  def iterable[T, M[_]](
    write: T => Try[BSONValue])(
    implicit
    it: M[T] <:< Iterable[T]): BSONWriter[M[T]] = from[M[T]] { values =>
    trySeq[T, BSONValue, IndexedSeq](it(values))(write).map(BSONArray.apply)
  }
}
