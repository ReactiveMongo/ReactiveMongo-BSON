package reactivemongo.api.bson.builder

import scala.annotation.implicitNotFound

/** Evidence that type `T` is a numeric one. */
@implicitNotFound(msg = "No numeric evidence for type ${T}")
sealed trait IsNumeric[T]

object IsNumeric {

  @SuppressWarnings(Array("AsInstanceOf"))
  implicit def derive[T](
      implicit
      /* @unused */ i0: Numeric[T]
    ): IsNumeric[T] =
    unsafe.asInstanceOf[IsNumeric[T]]

  @SuppressWarnings(Array("AsInstanceOf"))
  implicit def expr[T, V](
      implicit
      /* @unused */ i0: Numeric[V]
    ): IsNumeric[Expr[T, V]] =
    unsafe.asInstanceOf[IsNumeric[Expr[T, V]]]

  private val unsafe: IsNumeric[Nothing] = new IsNumeric[Nothing] {}
}
