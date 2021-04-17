package reactivemongo.api.bson

import scala.util.Try

/** Reads and writers `T` values to/from [[BSONDocument]]. */
trait BSONDocumentHandler[T]
    extends BSONDocumentReader[T]
    with BSONDocumentWriter[T]
    with BSONHandler[T] {

  final override def beforeRead(
      f: BSONDocument => BSONDocument
    ): BSONDocumentHandler[T] =
    BSONDocumentHandler.provided[T](reader = super.beforeRead(f), writer = this)

  final override def afterWrite(
      f: BSONDocument => BSONDocument
    ): BSONDocumentHandler[T] =
    BSONDocumentHandler.provided[T](reader = this, writer = super.afterWrite(f))

  final override def afterWriteTry(f: BSONDocument => Try[BSONDocument]): BSONDocumentHandler[T] =
    BSONDocumentHandler.provided[T](
      reader = this,
      writer = super.afterWriteTry(f))

  @SuppressWarnings(Array("AsInstanceOf"))
  final override def widen[U >: T]: BSONDocumentHandler[U] =
    this.asInstanceOf[BSONDocumentHandler[U]]

  @SuppressWarnings(Array("AsInstanceOf"))
  final override def narrow[U <: T]: BSONDocumentHandler[U] =
    this.asInstanceOf[BSONDocumentHandler[U]]
}

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
      write: T => BSONDocument
    ): BSONDocumentHandler[T] =
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
  def provided[T](
      implicit
      reader: BSONDocumentReader[T],
      writer: BSONDocumentWriter[T]
    ): BSONDocumentHandler[T] = new WrappedDocumentHandler[T](reader, writer)

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
      write: T => Option[BSONDocument]
    ): BSONDocumentHandler[T] =
    new OptionalDocumentHandler(read, write)

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
      write: T => Try[BSONDocument]
    ): BSONDocumentHandler[T] =
    new DefaultDocumentHandler(read, write)

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentHandler]]
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
      write: PartialFunction[T, BSONDocument]
    ): BSONDocumentHandler[T] =
    new FunctionalDocumentHandler(
      { bson =>
        read.lift(bson) getOrElse {
          throw exceptions.ValueDoesNotMatchException(BSONDocument pretty bson)
        }
      },
      { v =>
        write.lift(v) getOrElse {
          throw exceptions.ValueDoesNotMatchException(s"${v}")
        }
      }
    )

  // ---

  private final class OptionalDocumentHandler[T](
      read: BSONDocument => Option[T],
      val write: T => Option[BSONDocument])
      extends BSONDocumentReader.OptionalReader[T](read)
      with BSONDocumentWriter.OptionalWriter[T]
      with BSONDocumentHandler[T]

  private final class DefaultDocumentHandler[T](
      read: BSONDocument => Try[T],
      val write: T => Try[BSONDocument])
      extends BSONDocumentReader.DefaultReader[T](read)
      with BSONDocumentWriter.DefaultWriter[T]
      with BSONDocumentHandler[T]

  private final class FunctionalDocumentHandler[T](
      read: BSONDocument => T,
      val write: T => BSONDocument)
      extends BSONDocumentReader.FunctionalReader[T](read)
      with BSONDocumentWriter.FunctionalWriter[T]
      with BSONDocumentHandler[T]

  private final class WrappedDocumentHandler[T](
      reader: BSONDocumentReader[T],
      writer: BSONDocumentWriter[T])
      extends BSONDocumentReader[T]
      with BSONDocumentWriter[T]
      with BSONDocumentHandler[T] {

    @inline def readDocument(doc: BSONDocument): Try[T] = reader.readTry(doc)
    @inline def writeTry(value: T): Try[BSONDocument] = writer.writeTry(value)
  }
}
