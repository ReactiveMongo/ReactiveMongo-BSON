package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

trait BSONDocumentWriter[T] extends BSONWriter[T] { self =>
  override def writeTry(t: T): Try[BSONDocument]

  override def writeOpt(t: T): Option[BSONDocument] = writeTry(t).toOption

  final override def beforeWrite[U](f: U => T): BSONDocumentWriter[U] =
    BSONDocumentWriter.from[U] { u => writeTry(f(u)) }

  final def afterWrite(f: PartialFunction[BSONDocument, BSONDocument]): BSONDocumentWriter[T] = BSONDocumentWriter.from[T] {
    self.writeTry(_).flatMap { before =>
      f.lift(before) match {
        case Some(after) =>
          Success(after)

        case _ =>
          Failure(exceptions.ValueDoesNotMatchException(
            BSONDocument pretty before))
      }
    }
  }
}

/** [[BSONDocumentWriter]] factories */
object BSONDocumentWriter {
  /**
   * Creates a [[BSONDocumentWriter]] based on the given `write` function.
   * This function is called within a [[scala.util.Try]].
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocument, BSONDocumentWriter }
   *
   * case class Foo(value: String)
   *
   * val foo: BSONDocumentWriter[Foo] =
   *   BSONDocumentWriter { f: Foo => BSONDocument("value" -> f.value) }
   * }}}
   */
  def apply[T](write: T => BSONDocument): BSONDocumentWriter[T] = {
    @inline def w = write
    new FunctionalWriter[T] {
      val write = w
    }
  }

  private[bson] def safe[T](write: T => BSONDocument): BSONDocumentWriter[T] with SafeBSONDocumentWriter[T] = new BSONDocumentWriter[T] with SafeBSONDocumentWriter[T] {
    def safeWrite(value: T) = write(value)
  }

  /**
   * Creates a [[BSONWriter]] based on the given `write` function.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocument, BSONDocumentWriter }
   *
   * case class Foo(value: String)
   *
   * val writer = BSONDocumentWriter.option[Foo] { foo =>
   *   Some(BSONDocument("value" -> foo.value))
   * }
   * }}}
   */
  def option[T](write: T => Option[BSONDocument]): BSONDocumentWriter[T] = {
    @inline def w = write
    new OptionalWriter[T] {
      val write = w
    }
  }

  /**
   * Creates a [[BSONDocumentWriter]] based on the given `write` safe function.
   *
   * {{{
   * import scala.util.Success
   * import reactivemongo.api.bson.{ BSONDocument, BSONDocumentWriter }
   *
   * case class Foo(value: String)
   *
   * val writer = BSONDocumentWriter.from[Foo] { foo =>
   *   Success(BSONDocument("value" -> foo.value))
   * }
   * }}}
   *
   * @param write the safe function to write `T` values as BSON
   */
  def from[T](write: T => Try[BSONDocument]): BSONDocumentWriter[T] = {
    @inline def w = write
    new DefaultWriter[T] {
      val write = w
    }
  }

  /**
   * Creates a [[BSONDocumentWriter]] based on the given partial function.
   *
   * A [[exceptions.ValueDoesNotMatchException]] is returned as `Failure`
   * for any value that is not matched by the `write` function.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocument, BSONDocumentWriter }
   *
   * case class Foo(value: String)
   *
   * val writer = BSONDocumentWriter.collect[Foo] {
   *   case Foo(value) if value.nonEmpty =>
   *     BSONDocument("value" -> value)
   * }
   * }}}
   */
  def collect[T](
    write: PartialFunction[T, BSONDocument]): BSONDocumentWriter[T] =
    apply[T] { v =>
      write.lift(v) getOrElse {
        throw exceptions.ValueDoesNotMatchException(s"${v}")
      }
    }

  // ---

  private[bson] trait DefaultWriter[T] extends BSONDocumentWriter[T] {
    protected def write: T => Try[BSONDocument]

    def writeTry(value: T): Try[BSONDocument] = write(value)
  }

  private[bson] trait OptionalWriter[T] extends BSONDocumentWriter[T] {
    protected def write: T => Option[BSONDocument]

    override def writeOpt(value: T): Option[BSONDocument] = write(value)

    def writeTry(v: T): Try[BSONDocument] = write(v) match {
      case Some(bson) =>
        Success(bson)

      case _ =>
        Failure(exceptions.ValueDoesNotMatchException(s"${v}"))
    }
  }

  private[bson] trait FunctionalWriter[T] extends BSONDocumentWriter[T] {
    protected def write: T => BSONDocument

    def writeTry(value: T): Try[BSONDocument] = Try(write(value))
  }
}

/** A writer that is safe, as `writeTry` can always return a `Success`. */
private[bson] trait SafeBSONDocumentWriter[T] { writer: BSONDocumentWriter[T] =>
  def safeWrite(value: T): BSONDocument

  final def writeTry(value: T): Success[BSONDocument] =
    Success(safeWrite(value))
}
