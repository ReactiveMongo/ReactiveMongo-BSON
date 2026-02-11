package reactivemongo.api.bson.builder

import java.util.Date

import java.time.temporal.{ Temporal => JTemporal }

/** Evidence that `T` is a temporal (date/time) type */
trait Temporal[T]

object Temporal {
  implicit def date[T <: Date]: Temporal[T] = apply[T]

  implicit def temporal[T <: JTemporal]: Temporal[T] = apply[T]

  private def apply[T] = unsafe.asInstanceOf[Temporal[T]]

  private val unsafe = new Temporal[Nothing] {}
}
