package reactivemongo.api

/**
 * BSON main API
 *
 * {{{
 * import reactivemongo.api.bson._
 *
 * // { "name": "Johny", "surname": "Doe", "age": 28, "months": [1, 2, 3] }
 * document ++ ("name" -> "Johny") ++ ("surname" -> "Doe") ++
 * ("age" -> 28) ++ ("months" -> array(1, 2, 3))
 *
 * // { "_id": generatedId, "name": "Jane", "surname": "Doe", "age": 28,
 * //   "months": [1, 2, 3], "details": { "salary": 12345,
 * //   "inventory": ["foo", 7.8, 0, false] } }
 * document ++ ("_id" -> generateId, "name" -> "Jane", "surname" -> "Doe",
 *   "age" -> 28, "months" -> array(1, 2, 3),
 *   "details" -> document(
 *     "salary" -> 12345L, "inventory" -> array("foo", 7.8, 0L, false)))
 * }}}
 *
 * '''System properties:'''
 *
 * The following properties can be set (e.g. using `-D` option).
 *
 *   - `reactivemongo.api.bson.bufferSizeBytes` (integer; default: `96`): Number of bytes used as initial size when allocating a new buffer.
 *   - `reactivemongo.api.bson.document.strict` (boolean; default: `false`): Flag to enable strict reading of document (filter duplicate fields, see [[BSONDocument]]).
 */
package object bson extends DefaultBSONHandlers with Aliases with Utils {
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

  def element(name: String, value: BSONValue) = BSONElement(name, value)

  /** Convenient type alias for document handlers */
  type BSONDocumentHandler[T] = BSONDocumentReader[T] with BSONDocumentWriter[T] with BSONHandler[T]

  /** [[BSONDocumentHandler]] factories */
  object BSONDocumentHandler {
    import scala.util.Try

    /**
     * Document handler factory.
     *
     * {{{
     * import reactivemongo.api.bson.{ BSONDocument, BSONDocumentHandler }
     *
     * case class Bar(score: Double)
     *
     * val h: BSONDocumentHandler[Bar] = BSONDocumentHandler[Bar](
     *   read = { doc =>
     *     Bar(doc.getOrElse[Double]("score", 0D))
     *   },
     *   write = { bar => BSONDocument("score" -> bar.score) })
     * }}}
     */
    def apply[T](
      read: BSONDocument => T,
      write: T => BSONDocument): BSONDocumentHandler[T] =
      new FunctionalDocumentHandler[T](read, write)

    /**
     * Returns a document handler for a type `T`,
     * provided there are a writer and a reader for it.
     *
     * {{{
     * import reactivemongo.api.bson.{
     *   BSONDocumentHandler, BSONDocumentReader, BSONDocumentWriter
     * }
     *
     * def foo[T](
     *   implicit r: BSONDocumentReader[T],
     *     w: BSONDocumentWriter[T]): BSONDocumentHandler[T] =
     *   BSONDocumentHandler.provided[T]
     * }}}
     */
    def provided[T](implicit r: BSONDocumentReader[T], w: BSONDocumentWriter[T]): BSONDocumentHandler[T] = new WrappedDocumentHandler[T](r, w)

    /**
     * Creates a [[BSONDocumentHandler]]
     * based on the given `read` and `write` functions.
     *
     * {{{
     * import reactivemongo.api.bson.{ BSONDocumentHandler, BSONDocument }
     *
     * val handler = BSONDocumentHandler.option[String](
     *   read = {
     *     case doc: BSONDocument => doc.getAsOpt[Int]("value").collect {
     *       case 0 => "zero"
     *       case 1 => "one"
     *     }
     *     case _ => None
     *   },
     *   write = {
     *     case "zero" => Some(BSONDocument("value" -> 0))
     *     case "one" => Some(BSONDocument("value" -> 1))
     *     case _ => None
     *   })
     *
     * handler.readTry(BSONDocument("value" -> 0)) // Success("zero")
     * handler.readOpt(BSONDocument("value" -> 3)) // None (as failed)
     *
     * handler.writeTry("one") // Success(BSONDocument("value" -> 1))
     * handler.writeOpt("3") // None (as failed)
     * }}}
     */
    def option[T](
      read: BSONValue => Option[T],
      write: T => Option[BSONDocument]): BSONDocumentHandler[T] =
      new OptionalDocumentHandler(read, write) // TODO: Test

    /**
     * Creates a [[BSONDocumentHandler]]
     * based on the given safe `read` and `write` functions.
     *
     * {{{
     * import scala.util.Success
     * import reactivemongo.api.bson.{ BSONDocument, BSONDocumentHandler }
     *
     * case class Bar(score: Double)
     *
     * val h: BSONDocumentHandler[Bar] = BSONDocumentHandler.from[Bar](
     *   read = _.getAsTry[Double]("score").map(Bar(_)),
     *   write = { bar => Success(BSONDocument("score" -> bar.score)) })
     * }}}
     */
    def from[T](
      read: BSONDocument => Try[T],
      write: T => Try[BSONDocument]): BSONDocumentHandler[T] =
      new DefaultDocumentHandler(read, write)

    /**
     * Creates a [[BSONDocumentHandler]]
     * based on the given `read` and `write` functions.
     *
     * {{{
     * import reactivemongo.api.bson.{ BSONDocumentHandler, BSONDocument }
     *
     * val handler = BSONDocumentHandler.collect[String](
     *   read = {
     *     case doc: BSONDocument => doc.getAsOpt[Int]("value").collect {
     *       case 0 => "zero"
     *       case 1 => "one"
     *     } getOrElse ""
     *   },
     *   write = {
     *     case "zero" => BSONDocument("value" -> 0)
     *     case "one" => BSONDocument("value" -> 1)
     *   })
     *
     * handler.readTry(BSONDocument("value" -> 0)) // Success("zero")
     * handler.readOpt(BSONDocument("value" -> 3)) // None (as failed)
     *
     * handler.writeTry("one") // Success(BSONDocument("value" -> 1))
     * handler.writeOpt("3") // None (as failed)
     * }}}
     */
    def collect[T](
      read: PartialFunction[BSONValue, T],
      write: PartialFunction[T, BSONDocument]): BSONDocumentHandler[T] =
      new FunctionalDocumentHandler(
        { bson =>
          read.lift(bson) getOrElse {
            throw exceptions.ValueDoesNotMatchException(
              BSONDocument pretty bson)
          }
        },
        { v =>
          write.lift(v) getOrElse {
            throw exceptions.ValueDoesNotMatchException(s"${v}")
          }
        })
  }

  /**
   * Key/value ordering
   *
   * {{{
   * import reactivemongo.api.bson.BSONString
   *
   * Seq("foo" -> BSONString("1"), "bar" -> BSONString("1")).
   *   sorted // == [ "bar" -> .., "foo" -> .. ]
   * }}}
   */
  implicit def nameValueOrdering[T <: BSONValue] =
    new scala.math.Ordering[(String, T)] {

      def compare(x: (String, T), y: (String, T)): Int =
        x._1 compare y._1
    }

  import com.github.ghik.silencer.silent

  /**
   * Evidence that `T` can be serialized as a BSON document.
   */
  @silent sealed trait DocumentClass[T]

  /** See [[DocumentClass]] */
  object DocumentClass {
    import language.experimental.macros

    private val unsafe = new DocumentClass[Nothing] {}

    /** Un-checked factory */
    @silent
    @SuppressWarnings(Array("AsInstanceOf"))
    def unchecked[T] = unsafe.asInstanceOf[DocumentClass[T]]

    /**
     * Implicit evidence of `DocumentClass` for `T`
     * if `T` is a case class or a sealed trait.
     */
    @SuppressWarnings(Array("NullParameter"))
    implicit def evidence[T]: DocumentClass[T] = macro MacroImpl.documentClass[T]
  }

  // ---

  import language.experimental.macros

  /**
   * Keeps a `A` statement but raise a migration error at compile-time.
   *
   * The compilation error can be disabled by setting the system property
   * `reactivemongo.api.migrationRequired.nonFatal` to `true`.
   */
  @SuppressWarnings(Array("NullParameter", "UnusedMethodParameter"))
  def migrationRequired[A](details: String): A = macro reactivemongo.api.bson.
    MacroImpl.migrationRequired[A]
}
