package reactivemongo.api.bson

import scala.util.{ Failure, Try }

import scala.collection.Factory

private[bson] trait BSONReaderCompat { self: BSONReader.type =>
  /**
   * '''EXPERIMENTAL:''' (API may change without notice)
   *
   * Creates a [[BSONReader]] accepting only [[BSONArray]],
   * and applying the given safe `read` function to each element value.
   */
  def iterable[T, M[_]](read: BSONValue => Try[T])(
    implicit
    cbf: Factory[T, M[T]]): BSONReader[M[T]] = {

    from[M[T]] {
      case BSONArray(values) =>
        trySeq[BSONValue, T, M](values)(read)

      case bson => Failure(exceptions.TypeDoesNotMatchException(
        "BSONArray", bson.getClass.getSimpleName))
    }
  }
}
