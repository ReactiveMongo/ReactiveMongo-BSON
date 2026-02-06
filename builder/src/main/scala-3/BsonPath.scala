package reactivemongo.api.bson.builder

import scala.annotation.{ implicitNotFound, unused }
import scala.compiletime.{ constValue, erasedValue, summonFrom }
import scala.deriving.Mirror

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
     * val ev1 = BsonPath.from[Foo1].exists[String]("number")
     * val alias = ev1.unsafeAlias("_id")
     * }}}
     */
    def unsafeAlias[K2](@unused to: K2): Exists[T, K2, V] =
      Exists.unsafe.asInstanceOf[Exists[T, K2, V]]
  }

  def from[T]: Factory[T] = new Factory[T]

  final class Factory[T] {

    inline def exists[V]: ExistsPartialFactory[T, V] =
      new ExistsPartialFactory[T, V]

    final class ExistsPartialFactory[T, V] {

      inline def apply[K <: Singleton](
          field: K
        )(using
          e: Exists[T, K, V]
        ): Exists[T, K, V] = e
    }
  }

  // Match type to find the type of field K in tuple (Labels, Types)
  type FindFieldType[Labels <: Tuple, Types <: Tuple, K] =
    (Labels, Types) match {
      case (EmptyTuple, _)    => Nothing
      case (_, EmptyTuple)    => Nothing
      case (K *: _, t *: _)   => t
      case (_ *: ls, _ *: ts) => FindFieldType[ls, ts, K]
    }

  // Match type to check if field K exists (returns true if found, false otherwise)
  type HasField[Labels <: Tuple, K] <: Boolean =
    Labels match {
      case EmptyTuple => false
      case K *: _     => true
      case _ *: tail  => HasField[tail, K]
    }

  // Match type to check if field K has exactly type V
  type HasFieldWithType[Labels <: Tuple, Types <: Tuple, K, V] <: Boolean =
    FindFieldType[Labels, Types, K] match {
      case V => true
      case _ => false
    }

  // Match type to check if field K has type Option[V]
  type HasFieldWithOptionType[
      Labels <: Tuple,
      Types <: Tuple,
      K,
      V
    ] <: Boolean =
    FindFieldType[Labels, Types, K] match {
      case Option[V] => true
      case _         => false
    }

  // Match type to check if field K is an Option type
  type IsOptionField[Labels <: Tuple, Types <: Tuple, K] <: Boolean =
    FindFieldType[Labels, Types, K] match {
      case Option[_] => true
      case _         => false
    }

  // Match type to unwrap Option from field type (returns inner type if Option, otherwise the type itself)
  type UnwrapFieldOption[Labels <: Tuple, Types <: Tuple, K] =
    FindFieldType[Labels, Types, K] match {
      case Option[inner] => inner
      case _             => FindFieldType[Labels, Types, K]
    }

  object Exists extends LowPriorityExists {

    private[builder] inline def apply[T, V]: ExistsPartial[T, V] =
      new ExistsPartial[T, V]

    final class ExistsPartial[T, V] {

      inline def apply[K <: Singleton](
          @unused field: K
        )(using
          e: Exists[T, K, V]
        ): Exists[T, K, V] = e
    }

    /**
     * Derive Exists evidence when field K in type T has exactly type V.
     * Uses match types and =:= for soft failure.
     */
    inline given deriveExists[T <: Product, K <: String & Singleton, V](
        using
        m: Mirror.ProductOf[T],
        @unused ev: HasFieldWithType[
          m.MirroredElemLabels,
          m.MirroredElemTypes,
          K,
          V
        ] =:= true
      ): Exists[T, K, V] = unsafe.asInstanceOf[Exists[T, K, V]]

    /**
     * Derive Exists evidence for any type when field K exists.
     * This is used for wildcard patterns like Exists[T, K, _].
     * Lower priority than deriveExists so exact type match is preferred.
     */
    inline given deriveExistsAny[T <: Product, K <: String & Singleton](
        using
        m: Mirror.ProductOf[T],
        @unused ev: HasField[m.MirroredElemLabels, K] =:= true
      ): Exists[T, K, FindFieldType[m.MirroredElemLabels, m.MirroredElemTypes, K]] =
      unsafe.asInstanceOf[Exists[
        T,
        K,
        FindFieldType[m.MirroredElemLabels, m.MirroredElemTypes, K]
      ]]

    private[builder] val unsafe = new Exists[Nothing, Nothing, Nothing]
  }

  private[builder] sealed trait LowPriorityExists {

    /**
     * Derive Exists evidence when field K in type T has type Option[V].
     */
    inline given deriveOptionExists[T <: Product, K <: String & Singleton, V](
        using
        m: Mirror.ProductOf[T],
        @unused ev: HasFieldWithOptionType[
          m.MirroredElemLabels,
          m.MirroredElemTypes,
          K,
          V
        ] =:= true
      ): Exists[T, K, V] = new Exists[T, K, V]
  }

  /** Evidence that type `T` has field `K` with type `V`. */
  @implicitNotFound(msg = "No lookup of field ${K} of type ${V} in ${T}")
  sealed class Lookup[T, K, V] {
    type Inner
  }

  object Lookup extends LowPriorityLookup {
    type Aux[T, K, V, U] = Lookup[T, K, V] { type Inner = U }

    /**
     * Derive Lookup for a single field that is an Option type.
     * Uses match types to unwrap the Option and return the inner type.
     * This is equivalent to Scala 2's `Exists[T, K, _ <: Option[V]]` pattern.
     */
    inline given deriveHNilOption[T <: Product, K <: String & Singleton](
        using
        m: Mirror.ProductOf[T],
        @unused ev: IsOptionField[
          m.MirroredElemLabels,
          m.MirroredElemTypes,
          K
        ] =:= true
      ): Aux[
      T,
      K *: EmptyTuple,
      UnwrapFieldOption[m.MirroredElemLabels, m.MirroredElemTypes, K],
      UnwrapFieldOption[m.MirroredElemLabels, m.MirroredElemTypes, K]
    ] =
      new Lookup[
        T,
        K *: EmptyTuple,
        UnwrapFieldOption[m.MirroredElemLabels, m.MirroredElemTypes, K]
      ] {
        type Inner =
          UnwrapFieldOption[m.MirroredElemLabels, m.MirroredElemTypes, K]
      }

    inline given deriveConsOption[
        T <: Product,
        KH <: String & Singleton,
        KT <: Tuple,
        V1
      ](using
        m: Mirror.ProductOf[T],
        @unused ev: IsOptionField[
          m.MirroredElemLabels,
          m.MirroredElemTypes,
          KH
        ] =:= true,
        tail: Lookup[
          UnwrapFieldOption[m.MirroredElemLabels, m.MirroredElemTypes, KH],
          KT,
          V1
        ]
      ): Aux[T, KH *: KT, V1, V1] = new Lookup[T, KH *: KT, V1] {
      type Inner = V1
    }
  }

  private[builder] sealed trait LowPriorityLookup {

    inline given deriveHNil[T, K <: String & Singleton & Singleton, V](
        using
        head: Exists[T, K, V],
        notOption: V <:!< Option[?]
      ): Lookup.Aux[T, K *: EmptyTuple, V, V] =
      new Lookup[T, K *: EmptyTuple, V] {
        type Inner = V
      }

    inline given deriveCons[
        T,
        KH <: String & Singleton & Singleton,
        KT <: Tuple,
        V0,
        V1
      ](using
        head: Exists[T, KH, V0],
        tail: Lookup[V0, KT, V1]
      ): Lookup.Aux[T, KH *: KT, V1, V1] = new Lookup[T, KH *: KT, V1] {
      type Inner = V1
    }
  }

  // Type-level negation for "not a subtype of"
  trait <:!<[A, B]

  object <:!< {
    given [A, B]: <:!<[A, B] = new <:!<[A, B] {}

    given [A, B](
        using
        ev: A <:< B
      ): <:!<[A, B] = ???
  }

  // Compile-time helpers
  private inline def hasField[
      Labels <: Tuple,
      K <: String & Singleton
    ]: Boolean = {
    inline erasedValue[Labels] match {
      case _: EmptyTuple  => false
      case _: (K *: tail) => true
      case _: (? *: tail) => hasField[tail, K]
    }
  }

  private inline def checkFieldType[
      T,
      Labels <: Tuple,
      Types <: Tuple,
      K <: String & Singleton,
      V
    ]: Boolean = {
    inline erasedValue[Labels] match {
      case _: EmptyTuple => false
      case _: (K *: _)   =>
        inline erasedValue[Types] match {
          case _: (V *: _) => true
          case _           => false
        }
      case _: (_ *: labelTail) =>
        inline erasedValue[Types] match {
          case _: (_ *: typeTail) =>
            checkFieldType[T, labelTail, typeTail, K, V]
          case _ => false
        }
    }
  }

  private inline def checkFieldOptionType[
      T,
      Labels <: Tuple,
      Types <: Tuple,
      K <: String & Singleton,
      V
    ]: Boolean = {
    inline erasedValue[Labels] match {
      case _: EmptyTuple => false
      case _: (K *: _)   =>
        inline erasedValue[Types] match {
          case _: (Option[V] *: _) => true
          case _                   => false
        }
      case _: (_ *: labelTail) =>
        inline erasedValue[Types] match {
          case _: (_ *: typeTail) =>
            checkFieldOptionType[T, labelTail, typeTail, K, V]
          case _ => false
        }
    }
  }
}
