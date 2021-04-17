package reactivemongo.api

import scala.math.Ordering

// TODO: relocate handler

/**
 * BSON main API
 *
 * {{{
 * import reactivemongo.api.bson._
 *
 * // { "name": "Johny", "surname": "Doe", "age": 28, "months": [1, 2, 3] }
 * BSONDocument.empty ++ ("name" -> "Johny") ++ ("surname" -> "Doe") ++
 * ("age" -> 28) ++ ("months" -> array(1, 2, 3))
 *
 * // { "_id": generatedId, "name": "Jane", "surname": "Doe", "age": 28,
 * //   "months": [1, 2, 3], "details": { "salary": 12345,
 * //   "inventory": ["foo", 7.8, 0, false] } }
 * document.++("_id" -> generateId, "name" -> "Jane", "surname" -> "Doe",
 *   "age" -> 28, "months" -> array(1, 2, 3),
 *   "details" -> document(
 *     "salary" -> 12345L, "inventory" -> array("foo", 7.8, 0L, false)))
 * }}}
 *
 * '''System properties:'''
 *
 * The following properties can be set (e.g. using JVM `-D` options).
 *
 *   - `reactivemongo.api.bson.bufferSizeBytes` (integer; default: `96`): Number of bytes used as initial size when allocating a new buffer.
 *   - `reactivemongo.api.bson.document.strict` (boolean; default: `false`): Flag to enable strict reading of document (filter duplicate fields, see [[BSONDocument.asStrict]]).
 */
package object bson
    extends DefaultBSONHandlers
    with Aliases
    with Utils
    with PackageCompat {
  // DSL helpers:

  /**
   * Returns an empty document.
   *
   * {{{
   * import reactivemongo.api.bson._
   *
   * val doc = document ++ ("foo" -> 1)
   * // { 'foo': 1 }
   * }}}
   */
  def document = BSONDocument.empty

  /**
   * Returns a document with given elements.
   *
   * {{{
   * import reactivemongo.api.bson._
   *
   * val doc = document("foo" -> 1)
   * // { 'foo': 1 }
   * }}}
   */
  def document(elements: ElementProducer*) = BSONDocument(elements: _*)

  /**
   * Returns an empty array.
   *
   * {{{
   * import reactivemongo.api.bson._
   *
   * val arr1 = BSONString("bar") +: array // [ 'bar' ]
   * val arr2 = BSONInteger(1) +: arr1 // [ 1, 'bar' ]
   * }}}
   */
  def array = BSONArray.empty

  /**
   * Returns an array with given values.
   *
   * {{{
   * import reactivemongo.api.bson._
   *
   * val arr = array("bar", 1L) // [ 'bar', NumberLong(1) ]
   * }}}
   */
  def array(values: Producer[BSONValue]*) = BSONArray(values: _*)

  /** Returns a BSON MinKey value */
  def minKey: BSONMinKey = BSONMinKey

  /** Returns a BSON MaxKey value */
  def maxKey: BSONMaxKey = BSONMaxKey

  /** Returns a BSON Null value */
  def `null`: BSONNull = BSONNull

  /** Returns a BSON Undefined value */
  def undefined: BSONUndefined = BSONUndefined

  /** Returns a newly generated object ID. */
  def generateId = BSONObjectID.generate()

  /**
   * Key/value ordering
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONString, nameValueOrdering }
   *
   * Seq("foo" -> BSONString("1"), "bar" -> BSONString("1")).
   *   sorted // == [ "bar" -> .., "foo" -> .. ]
   * }}}
   */
  implicit def nameValueOrdering[T <: BSONValue]: Ordering[(String, T)] =
    new Ordering[(String, T)] {

      def compare(x: (String, T), y: (String, T)): Int =
        x._1 compare y._1
    }
}
