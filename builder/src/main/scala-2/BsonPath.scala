package reactivemongo.api.bson.builder

import scala.annotation.implicitNotFound

import shapeless._
import shapeless.ops.record.Selector

object BsonPath {

  /** Evidence that type `T` has field `K` with type `V`. */
  @implicitNotFound(msg = "No field ${K} of type ${V} in ${T}")
  final class Exists[T, K, V] {

    /**
     * Materializes an unsafe evidence for the specified alias.
     * Useful when a Scala field must be renamed during BSON serialization
     * (e.g. identity field mapped to `_id`).
     *
     * {{{
     * import reactivemongo.api.bson.builder.BsonPath
     *
     * case class Foo1(number: String)
     *
     * val ev1 = BsonPath.from[Foo1].exists(Symbol("number"))
     * val alias = ev1.unsafeAlias(Symbol("_id"))
     * }}}
     */
    def unsafeAlias(/* @unused */ to: Witness): Exists[T, to.T, V] =
      Exists.unsafe.asInstanceOf[Exists[T, to.T, V]]
  }

  def from[T]: Factory[T] = new Factory[T]

  final class Factory[T] {

    @inline def exists[V](
        field: Witness
      )(implicit
        e: Exists[T, field.T, V]
      ): Exists[T, field.T, V] = Exists.apply[T, V](field)
  }

  object Exists extends LowPriorityExists {

    private[builder] def apply[T, V](
        /* @unused */ field: Witness
      )(implicit
        e: Exists[T, field.T, V]
      ): Exists[T, field.T, V] = e

    implicit def deriveExists[T, H <: HList, K, V](
        implicit
        /* @unused */ i0: LabelledGeneric.Aux[T, H],
        /* @unused */ i1: Selector.Aux[H, K, V]
      ): Exists[T, K, V] = unsafe.asInstanceOf[Exists[T, K, V]]

    private val unsafe = new Exists[Nothing, Nothing, Nothing]
  }

  private[builder] sealed trait LowPriorityExists {

    implicit def deriveOptionExists[T, H <: HList, K, V](
        implicit
        /* @unused */ i0: LabelledGeneric.Aux[T, H],
        /* @unused */ i1: Selector.Aux[H, K, Option[V]]
      ): Exists[T, K, V] = new Exists[T, K, V]
  }

  /** Evidence that type `T` has field `K` with type `V`. */
  @implicitNotFound(msg = "No lookup of field ${K} of type ${V} in ${T}")
  sealed class Lookup[T, K <: HList, V] {
    type Inner
  }

  object Lookup extends LowPriorityLookup {
    type Aux[T, K <: HList, V, U] = Lookup[T, K, V] { type Inner = U }

    implicit def deriveHNilOption[T, K, V](
        implicit
        /* @unused */ head: Exists[T, K, _ <: Option[V]]
      ): Aux[T, K :: HNil, V, V] =
      new Lookup[T, K :: HNil, V] {
        type Inner = V
      }

    implicit def deriveConsOption[T, KH, KT <: HList, V0, V1](
        implicit
        /* @unused */ head: Exists[T, KH, _ <: Option[V0]],
        /* @unused */ tail: Lookup[V0, KT, V1]
      ): Aux[T, KH :: KT, V1, V1] = new Lookup[T, KH :: KT, V1] {
      type Inner = V1
    }
  }

  private[builder] sealed trait LowPriorityLookup {

    implicit def deriveHNil[T, K, V](
        implicit
        /* @unused */ head: Exists[T, K, V],
        /* @unused */ notOption: <:!<[V, Option[_]]
      ): Lookup.Aux[T, K :: HNil, V, V] = new Lookup[T, K :: HNil, V] {
      type Inner = V
    }

    implicit def deriveCons[T, KH, KT <: HList, V0, V1](
        implicit
        /* @unused */ head: Exists[T, KH, V0],
        /* @unused */ tail: Lookup[V0, KT, V1]
      ): Lookup.Aux[T, KH :: KT, V1, V1] = new Lookup[T, KH :: KT, V1] {
      type Inner = V1
    }
  }
}
