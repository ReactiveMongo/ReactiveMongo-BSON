package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

/** [[BSONWriter]] specialized for [[BSONDocument]] */
trait BSONDocumentWriter[T] extends BSONWriter[T] { self =>
  override def writeTry(t: T): Try[BSONDocument]

  override def writeOpt(t: T): Option[BSONDocument] = writeTry(t).toOption

  final override def beforeWrite[U](f: U => T): BSONDocumentWriter[U] =
    BSONDocumentWriter.from[U] { u => writeTry(f(u)) }

  def afterWrite(f: BSONDocument => BSONDocument): BSONDocumentWriter[T] =
    BSONDocumentWriter.from[T] {
      self.writeTry(_).map(f)
    }

  def afterWriteTry(
      f: BSONDocument => Try[BSONDocument]
    ): BSONDocumentWriter[T] = BSONDocumentWriter.from[T] {
    self.writeTry(_).flatMap(f)
  }

  @SuppressWarnings(Array("AsInstanceOf"))
  override def narrow[U <: T]: BSONDocumentWriter[U] =
    this.asInstanceOf[BSONDocumentWriter[U]]
}

/**
 * [[BSONDocumentWriter]] factories.
 *
 * @define createWriterBasedOn Creates a [[BSONDocumentWriter]] based on
 * @define valueDoesNotMatchException A [[exceptions.ValueDoesNotMatchException]] is returned as `Failure` for any value that is not matched by the `write` function
 */
object BSONDocumentWriter {

  /**
   * $createWriterBasedOn the given `write` function.
   * This function is called within a [[scala.util.Try]].
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocument, BSONDocumentWriter }
   *
   * case class Foo(value: String)
   *
   * val foo: BSONDocumentWriter[Foo] =
   *   BSONDocumentWriter { (f: Foo) => BSONDocument("value" -> f.value) }
   * }}}
   */
  def apply[T](write: T => BSONDocument): BSONDocumentWriter[T] = {
    @inline def w = write
    new FunctionalWriter[T] {
      val write = w
    }
  }

  private[bson] def safe[T](
      write: T => BSONDocument
    ): BSONDocumentWriter[T] with SafeBSONDocumentWriter[T] =
    new BSONDocumentWriter[T] with SafeBSONDocumentWriter[T] {
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
   * $createWriterBasedOn the given `write` safe function.
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
   * $createWriterBasedOn the given partial function.
   *
   * $valueDoesNotMatchException.
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
      write: PartialFunction[T, BSONDocument]
    ): BSONDocumentWriter[T] =
    apply[T] { v =>
      write.lift(v) getOrElse {
        throw exceptions.ValueDoesNotMatchException(s"${v}")
      }
    }

  /**
   * '''EXPERIMENTAL:''' $createWriterBasedOn the given
   * partially safe `write` function.
   *
   * $valueDoesNotMatchException.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocument, BSONDocumentWriter }
   *
   * case class Foo(value: String)
   *
   * val writer = BSONDocumentWriter.collectFrom[Foo] {
   *   case Foo(value) if value.nonEmpty =>
   *     scala.util.Success(BSONDocument("value" -> value))
   * }
   * }}}
   */
  def collectFrom[T](
      write: PartialFunction[T, Try[BSONDocument]]
    ): BSONDocumentWriter[T] = from[T] { (v: T) =>
    write.lift(v) getOrElse {
      Failure(exceptions.ValueDoesNotMatchException(s"${v}"))
    }
  }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentWriter]] that writes
   * a single value as a [[BSONDocument]] with a single field.
   *
   * @param name the name of the field to be written
   *
   * {{{
   * import reactivemongo.api.bson.BSONDocumentWriter
   *
   * val writer = BSONDocumentWriter.field[String]("foo")
   *
   * writer.writeTry("bar")
   * // => Success: {'foo': 'bar'}
   * }}}
   */
  def field[T](name: String)(implicit w: BSONWriter[T]): BSONDocumentWriter[T] =
    BSONDocumentWriter[T] { value => BSONDocument(name -> value) }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentWriter]] that writes
   * tuple elements as [[BSONDocument]] fields.
   *
   * {{{
   * import reactivemongo.api.bson.BSONDocumentWriter
   *
   * val writer = BSONDocumentWriter.tuple2[String, Int]("name", "age")
   *
   * writer.writeTry("Foo" -> 20)
   * // => Success: {'name': 'Foo', 'age': 20}
   * }}}
   */
  def tuple2[A: BSONWriter, B: BSONWriter](
      field1: String,
      field2: String
    ): BSONDocumentWriter[(A, B)] =
    apply[(A, B)] { case (a, b) => BSONDocument(field1 -> a, field2 -> b) }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentWriter]] that writes
   * tuple elements as [[BSONDocument]] fields.
   */
  def tuple3[A: BSONWriter, B: BSONWriter, C: BSONWriter](
      field1: String,
      field2: String,
      field3: String
    ): BSONDocumentWriter[(A, B, C)] = apply[(A, B, C)] {
    case (a, b, c) => BSONDocument(field1 -> a, field2 -> b, field3 -> c)
  }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentWriter]] that writes
   * tuple elements as [[BSONDocument]] fields.
   */
  def tuple4[A: BSONWriter, B: BSONWriter, C: BSONWriter, D: BSONWriter](
      field1: String,
      field2: String,
      field3: String,
      field4: String
    ): BSONDocumentWriter[(A, B, C, D)] =
    apply[(A, B, C, D)] {
      case (a, b, c, d) =>
        BSONDocument(field1 -> a, field2 -> b, field3 -> c, field4 -> d)
    }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentWriter]] that writes
   * tuple elements as [[BSONDocument]] fields.
   */
  def tuple5[
      A: BSONWriter,
      B: BSONWriter,
      C: BSONWriter,
      D: BSONWriter,
      E: BSONWriter
    ](field1: String,
      field2: String,
      field3: String,
      field4: String,
      field5: String
    ): BSONDocumentWriter[(A, B, C, D, E)] =
    apply[(A, B, C, D, E)] {
      case (a, b, c, d, e) =>
        BSONDocument(
          field1 -> a,
          field2 -> b,
          field3 -> c,
          field4 -> d,
          field5 -> e
        )
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
