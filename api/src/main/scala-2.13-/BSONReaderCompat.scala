package reactivemongo.api.bson

import scala.language.higherKinds

import scala.util.{ Failure, Try }

import scala.collection.generic.CanBuildFrom

private[bson] trait BSONReaderCompat { self: BSONReader.type =>

  /**
   * '''EXPERIMENTAL:''' (API may change without notice)
   *
   * Creates a [[BSONReader]] accepting only [[BSONArray]],
   * and applying the given safe `read` function to each element value.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONReader, Macros }
   *
   * case class Element(str: String, v: Int)
   *
   * val elementHandler = Macros.handler[Element]
   *
   * val setReader: BSONReader[Set[Element]] =
   *   BSONReader.iterable[Element, Set](elementHandler readTry _)
   * }}}
   */
  def iterable[T, M[_]](
      read: BSONValue => Try[T]
    )(implicit
      cbf: CanBuildFrom[M[_], T, M[T]]
    ): BSONReader[M[T]] = {

    from[M[T]] {
      case BSONArray(values) =>
        trySeq[BSONValue, T, M](values)(read)

      case bson =>
        Failure(
          exceptions
            .TypeDoesNotMatchException("BSONArray", bson.getClass.getSimpleName)
        )
    }
  }
}
