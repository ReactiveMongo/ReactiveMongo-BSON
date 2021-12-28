package reactivemongo.api.bson.monocle

import scala.util.{ Failure, Success, Try }

import reactivemongo.api.bson.{
  BSONDocument,
  BSONElement,
  BSONReader,
  BSONValue,
  BSONWriter,
  ElementProducer
}

sealed trait FieldLens[T] {

  /** Returns a BSON elements */
  private[monocle] def element(name: String, value: T): BSONElement

  private[monocle] def getter(name: String): BSONDocument => Option[T]
}

object FieldLens:
  import scala.reflect.ClassTag

  given bsonValueField[T <: BSONValue](using ct: ClassTag[T]): FieldLens[T] =
    new FieldLens[T] {
      def element(name: String, value: T): BSONElement = name -> value

      def getter(name: String) = (_: BSONDocument).get(name).flatMap(ct.unapply)
    }

  given default[T](using w: BSONWriter[T], r: BSONReader[T]): FieldLens[T] =
    new FieldLens[T] {

      def element(name: String, value: T): BSONElement =
        w.writeTry(value) match {
          case Failure(cause) =>
            throw cause

          case Success(bson) =>
            implicitly[BSONElement](name -> bson)
        }

      def getter(name: String) = (_: BSONDocument).getAsOpt[T](name)(r)
    }
end FieldLens
