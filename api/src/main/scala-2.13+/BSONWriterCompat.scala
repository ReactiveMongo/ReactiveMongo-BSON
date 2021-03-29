package reactivemongo.api.bson

import scala.util.Try

private[bson] trait BSONWriterCompat { self: BSONWriter.type =>
  /**
   * '''EXPERIMENTAL:''' (API may change without notice)
   *
   * Creates a [[BSONWriter]] accepting only [[scala.collection.Iterable]],
   * and applying the given safe `write` function to each element value.
   *
   * {{{
   * import reactivemongo.api.bson.BSONWriter
   *
   * case class Element(str: String, v: Int)
   *
   * def elementWriter: BSONWriter[Element] = ???
   *
   * val setWriter: BSONWriter[Set[Element]] =
   *   BSONWriter.iterable[Element, Set](elementWriter writeTry _)
   * }}}
   */
  def iterable[T, M[_]](
    write: T => Try[BSONValue])(
    implicit
    it: M[T] <:< Iterable[T]): BSONWriter[M[T]] = from[M[T]] { values =>
    trySeq[T, BSONValue, IndexedSeq](it(values))(write).map(BSONArray.apply)
  }
}
