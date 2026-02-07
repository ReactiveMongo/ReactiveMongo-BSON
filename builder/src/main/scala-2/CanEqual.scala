package reactivemongo.api.bson.builder

import reactivemongo.api.bson.¬

/** Evidence that a value of type `A` can equal a value of type `B`. */
@scala.annotation.implicitNotFound(msg =
  "No numeric evidence for types ${A} and ${B}"
)
sealed trait CanEqual[A, B]

object CanEqual extends LowPriorityCanEqual {

  @SuppressWarnings(Array("AsInstanceOf"))
  implicit def numeric[A, B](
      implicit
      /* @unused */
      i0: A ¬ B,
      i1: Numeric[A],
      i2: Numeric[A]
    ): CanEqual[A, B] = unsafe.asInstanceOf[CanEqual[A, B]]

  protected val unsafe: CanEqual[Nothing, Nothing] =
    new CanEqual[Nothing, Nothing] {}
}

private[builder] sealed trait LowPriorityCanEqual { self: CanEqual.type =>

  @SuppressWarnings(Array("AsInstanceOf"))
  implicit def derive[A, B <: A]: CanEqual[A, B] =
    unsafe.asInstanceOf[CanEqual[A, B]]
}
