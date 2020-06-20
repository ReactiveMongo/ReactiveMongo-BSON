package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

trait BSONDocumentReader[T] extends BSONReader[T] { self =>
  final def readTry(bson: BSONValue): Try[T] = bson match {
    case doc: BSONDocument => readDocument(doc)

    case _ => Failure(exceptions.TypeDoesNotMatchException(
      "BSONDocument", bson.getClass.getSimpleName))
  }

  def readDocument(doc: BSONDocument): Try[T]

  final override def afterRead[U](f: T => U): BSONDocumentReader[U] =
    new BSONDocumentReader.MappedReader[T, U](self, f)

  def beforeRead(f: PartialFunction[BSONDocument, BSONDocument]): BSONDocumentReader[T] = new BSONDocumentReader[T] {
    val underlying = BSONDocumentReader.collect(f)

    def readDocument(doc: BSONDocument): Try[T] =
      underlying.readDocument(doc).flatMap(self.readDocument)
  }

  final override def widen[U >: T]: BSONDocumentReader[U] =
    new BSONDocumentReader[U] {
      def readDocument(doc: BSONDocument): Try[U] =
        self.readDocument(doc).map(identity[U])
    }
}

/** [[BSONDocumentReader]] factories */
object BSONDocumentReader {
  /**
   * Creates a [[BSONDocumentReader]] based on the given `read` function.
   *
   * {{{
   * import reactivemongo.api.bson.BSONDocumentReader
   *
   * case class Foo(name: String, age: Int)
   *
   * val fooReader: BSONDocumentReader[Foo] = BSONDocumentReader[Foo] { doc =>
   *   (for {
   *     nme <- doc.string("name")
   *     age <- doc.int("age")
   *   } yield Foo(nme, age)).getOrElse(Foo("unknown", -1))
   * }
   * }}}
   */
  def apply[T](read: BSONDocument => T): BSONDocumentReader[T] =
    new FunctionalReader[T](read)

  /**
   * Creates a [[BSONDocumentReader]] based on the given `read` function.
   *
   * {{{
   * import reactivemongo.api.bson.BSONDocumentReader
   *
   * case class Foo(name: String, age: Int)
   *
   * val fooReader: BSONDocumentReader[Foo] =
   *   BSONDocumentReader.option[Foo] { doc =>
   *     for {
   *       nme <- doc.string("name")
   *       age <- doc.int("age")
   *     } yield Foo(nme, age)
   *   }
   * }}}
   */
  def option[T](read: BSONDocument => Option[T]): BSONDocumentReader[T] =
    new OptionalReader[T](read)

  /**
   * Creates a [[BSONDocumentReader]] based on the given safe `read` function.
   *
   * {{{
   * import reactivemongo.api.bson.BSONDocumentReader
   *
   * case class Foo(name: String, age: Int)
   *
   * val fooReader: BSONDocumentReader[Foo] =
   *   BSONDocumentReader.from[Foo] { doc =>
   *     for {
   *       nme <- doc.getAsTry[String]("name")
   *       age <- doc.getAsTry[Int]("age")
   *     } yield Foo(nme, age)
   *   }
   * }}}
   */
  def from[T](read: BSONDocument => Try[T]): BSONDocumentReader[T] =
    new DefaultReader[T](read)

  /** '''EXPERIMENTAL:''' Creates a [[BSONDocumentReader]] based on the given partial function. */
  def collect[T](read: PartialFunction[BSONDocument, T]): BSONDocumentReader[T] = new FunctionalReader[T]({ doc =>
    read.lift(doc) getOrElse {
      throw exceptions.ValueDoesNotMatchException(BSONDocument pretty doc)
    }
  })

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentReader]] that reads
   * the specified document fields as tuple elements.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocument, BSONDocumentReader }
   *
   * val reader = BSONDocumentReader.tuple2[String, Int]("name", "age")
   *
   * val doc = BSONDocument("name" -> "Foo", "age" -> 20)
   *
   * reader.readTry(doc) // => Success(("Foo", 20))
   * }}}
   */
  def tuple2[A: BSONReader, B: BSONReader](
    field1: String,
    field2: String): BSONDocumentReader[(A, B)] = from[(A, B)] { doc =>
    for {
      _1 <- doc.getAsTry[A](field1)
      _2 <- doc.getAsTry[B](field2)
    } yield Tuple2(_1, _2)
  }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentReader]] that reads
   * the specified document fields as tuple elements.
   *
   * @see [[tuple2]]
   */
  def tuple3[A: BSONReader, B: BSONReader, C: BSONReader](
    field1: String,
    field2: String,
    field3: String): BSONDocumentReader[(A, B, C)] = from[(A, B, C)] { doc =>
    for {
      _1 <- doc.getAsTry[A](field1)
      _2 <- doc.getAsTry[B](field2)
      _3 <- doc.getAsTry[C](field3)
    } yield Tuple3(_1, _2, _3)
  }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentReader]] that reads
   * the specified document fields as tuple elements.
   *
   * @see [[tuple2]]
   */
  def tuple4[A: BSONReader, B: BSONReader, C: BSONReader, D: BSONReader](
    field1: String,
    field2: String,
    field3: String,
    field4: String): BSONDocumentReader[(A, B, C, D)] =
    from[(A, B, C, D)] { doc =>
      for {
        _1 <- doc.getAsTry[A](field1)
        _2 <- doc.getAsTry[B](field2)
        _3 <- doc.getAsTry[C](field3)
        _4 <- doc.getAsTry[D](field4)
      } yield Tuple4(_1, _2, _3, _4)
    }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONDocumentReader]] that reads
   * the specified document fields as tuple elements.
   *
   * @see [[tuple2]]
   */
  def tuple5[A: BSONReader, B: BSONReader, C: BSONReader, D: BSONReader, E: BSONReader](
    field1: String,
    field2: String,
    field3: String,
    field4: String,
    field5: String): BSONDocumentReader[(A, B, C, D, E)] =
    from[(A, B, C, D, E)] { doc =>
      for {
        _1 <- doc.getAsTry[A](field1)
        _2 <- doc.getAsTry[B](field2)
        _3 <- doc.getAsTry[C](field3)
        _4 <- doc.getAsTry[D](field4)
        _5 <- doc.getAsTry[E](field5)
      } yield Tuple5(_1, _2, _3, _4, _5)
    }

  // ---

  private[bson] class DefaultReader[T](
    read: BSONDocument => Try[T]) extends BSONDocumentReader[T] {

    def readDocument(doc: BSONDocument): Try[T] = read(doc)
  }

  private[bson] class OptionalReader[T](
    read: BSONDocument => Option[T]) extends BSONDocumentReader[T] {

    override def readOpt(bson: BSONValue): Option[T] = bson match {
      case doc: BSONDocument => try {
        read(doc)
      } catch {
        case NonFatal(_) => None
      }

      case _ => None
    }

    override def readOrElse(bson: BSONValue, default: => T): T =
      bson match {
        case doc: BSONDocument => try {
          read(doc).getOrElse(default)
        } catch {
          case NonFatal(_) => default
        }

        case _ => default
      }

    def readDocument(doc: BSONDocument): Try[T] = Try(read(doc)).flatMap {
      case Some(result) => Success(result)

      case _ =>
        Failure(exceptions.ValueDoesNotMatchException(BSONDocument pretty doc))
    }
  }

  private[bson] class FunctionalReader[T](
    read: BSONDocument => T) extends BSONDocumentReader[T] {

    def readDocument(doc: BSONDocument): Try[T] = Try(read(doc))
  }

  private[bson] class MappedReader[T, U](
    parent: BSONDocumentReader[T],
    to: T => U) extends BSONDocumentReader[U] {
    def readDocument(doc: BSONDocument): Try[U] =
      parent.readDocument(doc).map(to)
  }
}
