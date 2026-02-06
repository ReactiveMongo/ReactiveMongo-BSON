package reactivemongo.api.bson.builder

import scala.util.NotGiven

import scala.annotation.{ implicitNotFound, unused }
import scala.deriving.Mirror

/** Evidence that type `T` has field `K` comparable with type `V`. */
@implicitNotFound(msg = "No field ${K} comparable with type ${V} in ${T}")
trait MongoComparable[T, K, V]

object MongoComparable extends LowPriorityComparable {

  /**
   * Scala 3 specific iterable implicit.
   * Handles the case where field type is an Iterable subtype.
   * This uses match types to find the field type, then checks subtyping.
   */
  inline given iterable[T <: Product, K <: String & Singleton, V](
      using
      m: Mirror.ProductOf[T],
      ev: BsonPath.FindFieldType[
        m.MirroredElemLabels,
        m.MirroredElemTypes,
        K
      ] <:< Iterable[V]
    ): MongoComparable[T, K, V] =
    unsafe.asInstanceOf[MongoComparable[T, K, V]]

  /**
   * Scala 3 specific numeric implicit.
   * Handles the case where both V and field type are numeric.
   * This uses match types to find the field type, then checks that both are numeric.
   */
  inline given numeric[T <: Product, K <: String & Singleton, V](
      using
      m: Mirror.ProductOf[T],
      numV: Numeric[V],
      numField: Numeric[
        BsonPath.FindFieldType[m.MirroredElemLabels, m.MirroredElemTypes, K]
      ]
    ): MongoComparable[T, K, V] =
    unsafe.asInstanceOf[MongoComparable[T, K, V]]

  /**
   * Scala 3 specific wildcard implicit.
   * Provides MongoComparable evidence for any field that exists,
   * where V matches the actual field type.
   * This handles cases where V is unconstrained by binding it to the field type.
   */
  inline given fieldType[T <: Product, K <: String & Singleton](
      using
      m: Mirror.ProductOf[T],
      ev: BsonPath.HasField[m.MirroredElemLabels, K] =:= true
    ): MongoComparable[
    T,
    K,
    BsonPath.FindFieldType[m.MirroredElemLabels, m.MirroredElemTypes, K]
  ] =
    unsafe.asInstanceOf[MongoComparable[
      T,
      K,
      BsonPath.FindFieldType[m.MirroredElemLabels, m.MirroredElemTypes, K]
    ]]

  protected[builder] val unsafe =
    new MongoComparable[Nothing, Nothing, Nothing] {}
}

private[builder] sealed trait LowPriorityComparable {
  self: MongoComparable.type =>

  given strictly[T <: Product, K <: String & Singleton, V](
      using
      @unused i0: BsonPath.Exists[T, K, V]
    ): MongoComparable[T, K, V] =
    unsafe.asInstanceOf[MongoComparable[T, K, V]]
}
