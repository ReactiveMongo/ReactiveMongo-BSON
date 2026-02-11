package reactivemongo.api.bson.builder

import scala.annotation.implicitNotFound

/** Evidence that type `T` has field `K` comparable with type `V`. */
@implicitNotFound(msg = "No field ${K} comparable with type ${V} in ${T}")
trait MongoComparable[T, K, V]

object MongoComparable extends LowPriorityComparable {

  implicit def iterable[T, K, V](
      implicit
      /* @unused */ i0: BsonPath.Exists[T, K, _ <: Iterable[V]]
    ): MongoComparable[T, K, V] = apply[T, K, V]

  implicit def numeric[T, K, V, U](
      implicit
      /* @unused */ i0: BsonPath.Exists[T, K, U],
      /* @unused */ i1: Numeric[V],
      /* @unused */ i2: Numeric[U]
    ): MongoComparable[T, K, V] = apply[T, K, V]

  protected[builder] def apply[T, K, V] =
    unsafe.asInstanceOf[MongoComparable[T, K, V]]

  protected[builder] val unsafe =
    new MongoComparable[Nothing, Nothing, Nothing] {}
}

private[builder] sealed trait LowPriorityComparable {
  self: MongoComparable.type =>

  implicit def strictly[T, K, V](
      implicit
      /* @unused */ i0: BsonPath.Exists[T, K, V]
    ): MongoComparable[T, K, V] = apply[T, K, V]

}
