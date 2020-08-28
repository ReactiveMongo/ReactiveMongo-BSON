package reactivemongo.api.bson

import scala.reflect.ClassTag

import _root_.monocle.Optional

/**
 * [[http://julien-truffaut.github.io/Monocle/ Monocle]] utilities
 * for BSON values.
 */
package object monocle extends LowPriorityMonocle {
  /**
   * Returns an optional lens for a field with the given `name`
   *
   * @tparam T the field type
   *
   * {{{
   * import reactivemongo.api.bson.BSONString
   * import reactivemongo.api.bson.monocle._
   *
   * val lens = field[BSONString]("bar")
   * }}}
   */
  def field[T <: BSONValue](name: String)(implicit ct: ClassTag[T]): Optional[BSONDocument, T] =
    Optional[BSONDocument, T](_.get(name).flatMap(ct.unapply)) { newVal: T =>
      { doc: BSONDocument =>
        doc -- (name) ++ (name -> newVal)
      }
    }

  /**
   * Returns an optional lens for a field `f`,
   * nested in a document field.
   *
   * {{{
   * import reactivemongo.api.bson.monocle._
   *
   * // For a top document { "foo": { "bar": 1 } }
   * val lens = nested("foo", field[Int]("bar"))
   * }}}
   */
  def nested[T](documentField: String, f: Optional[BSONDocument, T]): Optional[BSONDocument, T] = field[BSONDocument](documentField) composeOptional f

  /** Field wrapper */
  implicit final class Field(val name: String) extends AnyVal {
    /**
     * {{{
     * import reactivemongo.api.bson.monocle._
     *
     * // For a top document { "foo": { "bar": 1 } }
     * val lens = "foo" \ field[Int]("bar")
     * // equivalent to nested("foo", field[Int]("bar"))
     * }}}
     */
    @inline def \[T](child: Optional[BSONDocument, T]): Optional[BSONDocument, T] = nested(name, child)

    /**
     * {{{
     * import _root_.monocle.Optional
     *
     * import reactivemongo.api.bson.BSONDocument
     * import reactivemongo.api.bson.monocle._
     *
     * val lens: Optional[BSONDocument, BSONDocument] = "foo" \ "bar"
     * }}}
     */
    @inline def \(child: String): Optional[BSONDocument, BSONDocument] =
      field[BSONDocument](name) composeOptional field[BSONDocument](child)
  }

  /** Wrapper for a nested field */
  implicit final class NestedField(
    lens: Optional[BSONDocument, BSONDocument]) {

    /**
     * Returns a lens for a nested document.
     *
     * {{{
     * import reactivemongo.api.bson.monocle._
     *
     * // For a top document { "foo": { "bar": { "lorem": 1 } } }
     * val lens = "foo" \ "bar" \ field[Int]("lorem")
     * }}}
     */
    @inline def \[T](child: Optional[BSONDocument, T]): Optional[BSONDocument, T] = lens composeOptional child
  }
}

private[bson] sealed trait LowPriorityMonocle {
  /**
   * Returns an optional lens for a field with the given `name`.
   *
   * @tparam T the field type
   *
   * {{{
   * import reactivemongo.api.bson.monocle._
   *
   * val lens = field[String]("bar")
   * }}}
   */
  def field[T](name: String)(
    implicit
    w: BSONWriter[T], r: BSONReader[T]): Optional[BSONDocument, T] =
    Optional[BSONDocument, T](_.getAsOpt[T](name)) { newVal: T =>
      { doc: BSONDocument =>
        doc -- (name) ++ (name -> newVal)
      }
    }

}
