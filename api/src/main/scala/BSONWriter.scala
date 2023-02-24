package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

/**
 * A writer that produces a subtype of [[BSONValue]] from an instance of `T`.
 *
 * @define afterWriteDescription Prepares a BSON writer that returns the result of applying `f` on the BSON value from this writer.
 */
trait BSONWriter[T] {

  /**
   * Tries to produce a BSON value from an instance of `T`.
   *
   * {{{
   * import scala.util.Try
   * import reactivemongo.api.bson.{ BSONWriter, BSONValue }
   *
   * def toBSON[T](value: T)(implicit w: BSONWriter[T]): Try[BSONValue] =
   *   w.writeTry(value)
   * }}}
   */
  def writeTry(t: T): Try[BSONValue]

  /**
   * Tries to produce a BSON value from an instance of `T`,
   * returns `None` if an error occurred.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONWriter, BSONValue }
   *
   * def maybeBSON[T](value: T)(implicit w: BSONWriter[T]): Option[BSONValue] =
   *   w.writeOpt(value)
   * }}}
   */
  def writeOpt(t: T): Option[BSONValue] = writeTry(t).toOption

  /**
   * $afterWriteDescription
   *
   * If the `f` function is not defined for a [[BSONValue]],
   * it will results in a `Failure`.
   *
   * @param f the partial function to apply
   */
  def afterWrite(f: PartialFunction[BSONValue, BSONValue]): BSONWriter[T] =
    BSONWriter.from[T] {
      writeTry(_).flatMap { before =>
        f.lift(before) match {
          case Some(after) =>
            Success(after)

          case _ =>
            Failure(
              exceptions.ValueDoesNotMatchException(BSONValue pretty before)
            )
        }
      }
    }

  /**
   * $afterWriteDescription
   *
   * @param f the safe function to apply
   */
  def afterWriteTry(f: BSONValue => Try[BSONValue]): BSONWriter[T] =
    BSONWriter.from[T] {
      writeTry(_).flatMap(f)
    }

  /**
   * Prepares a BSON writer that converts the input before calling
   * the current writer.
   *
   * @param f the function apply the `U` input value to convert at `T` value used to the current writer
   *
   * {{{
   * import reactivemongo.api.bson.BSONWriter
   *
   * val w: BSONWriter[String] =
   *   implicitly[BSONWriter[Int]].beforeWrite(_.size)
   *
   * w.writeTry("foo") // Success: BSONInteger(3)
   * }}}
   */
  def beforeWrite[U](f: U => T): BSONWriter[U] =
    BSONWriter.from[U] { u => writeTry(f(u)) }

  /**
   * Narrows this writer for a compatible type `U`.
   *
   * {{{
   * import reactivemongo.api.bson.BSONWriter
   *
   * val listWriter: BSONWriter[Seq[String]] =
   *   implicitly[BSONWriter[Seq[String]]]
   *
   * val narrowAsListWriter: BSONWriter[List[String]] =
   *   listWriter.narrow[List[String]]
   *   // as List[String] <: Seq[String]
   * }}}
   *
   * @tparam U must be a sub-type of `T`
   */
  @SuppressWarnings(Array("AsInstanceOf"))
  def narrow[U <: T]: BSONWriter[U] = this.asInstanceOf[BSONWriter[U]]
}

/**
 * [[BSONWriter]] factories.
 *
 * @define createWriterBasedOn Creates a [[BSONWriter]] based on
 * @define valueDoesNotMatchException A [[exceptions.ValueDoesNotMatchException]] is returned as `Failure` for any value that is not matched by the `write` function
 */
object BSONWriter extends BSONWriterCompat with BSONWriterInstances {

  /**
   * $createWriterBasedOn the given `write` function.
   * This function is called within a [[scala.util.Try]].
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONWriter, BSONString }
   *
   * case class Foo(value: String)
   *
   * val foo: BSONWriter[Foo] = BSONWriter { f => BSONString(f.value) }
   * }}}
   */
  def apply[T](write: T => BSONValue): BSONWriter[T] = {
    @inline def w = write
    new FunctionalWriter[T] {
      val write = w
    }
  }

  private[bson] def safe[T](
      write: T => BSONValue
    ): BSONWriter[T] with SafeBSONWriter[T] =
    new BSONWriter[T] with SafeBSONWriter[T] {
      def safeWrite(value: T) = write(value)
    }

  /**
   * $createWriterBasedOn the given `write` function.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONWriter, BSONInteger }
   *
   * val strCodeToIntWriter = BSONWriter.option[String] {
   *   case "zero" => Some(BSONInteger(0))
   *   case "one" => Some(BSONInteger(1))
   *   case _ => None
   * }
   *
   * strCodeToIntWriter.writeTry("zero") // Success(BSONInteger(0))
   * strCodeToIntWriter.writeTry("one") // Success(BSONInteger(1))
   *
   * strCodeToIntWriter.writeTry("3")
   * // => Failure(ValueDoesNotMatchException(..))
   *
   * strCodeToIntWriter.writeOpt("4") // None (as failed)
   * }}}
   */
  def option[T](write: T => Option[BSONValue]): BSONWriter[T] = {
    @inline def w = write
    new OptionalWriter[T] {
      val write = w
    }
  }

  /**
   * $createWriterBasedOn the given safe `write` function.
   *
   * {{{
   * import scala.util.{ Failure, Success }
   * import reactivemongo.api.bson.{ BSONWriter, BSONInteger }
   *
   * val strCodeToIntWriter = BSONWriter.from[String] {
   *   case "zero" => Success(BSONInteger(0))
   *   case "one" => Success(BSONInteger(1))
   *   case _ => Failure(new IllegalArgumentException())
   * }
   *
   * strCodeToIntWriter.writeTry("zero") // Success(BSONInteger(0))
   * strCodeToIntWriter.writeTry("one") // Success(BSONInteger(1))
   *
   * strCodeToIntWriter.writeTry("3")
   * // => Failure(IllegalArgumentException(..))
   *
   * strCodeToIntWriter.writeOpt("4") // None (as failed)
   * }}}
   *
   * @param write the safe function to write `T` values as BSON
   */
  def from[T](write: T => Try[BSONValue]): BSONWriter[T] = {
    @inline def w = write
    new DefaultWriter[T] {
      val write = w
    }
  }

  /**
   * '''EXPERIMENTAL:''' $createWriterBasedOn the given
   * partially safe `write` function.
   *
   * $valueDoesNotMatchException.
   *
   * {{{
   * import scala.util.Success
   * import reactivemongo.api.bson.{ BSONWriter, BSONInteger }
   *
   * val strCodeToIntWriter = BSONWriter.collectFrom[String] {
   *   case "zero" => Success(BSONInteger(0))
   *   case "one" => Success(BSONInteger(1))
   * }
   *
   * strCodeToIntWriter.writeTry("zero") // Success(BSONInteger(0))
   * strCodeToIntWriter.writeTry("one") // Success(BSONInteger(1))
   *
   * strCodeToIntWriter.writeTry("3")
   * // => Failure(IllegalArgumentException(..))
   *
   * strCodeToIntWriter.writeOpt("4") // None (as failed)
   * }}}
   */
  def collectFrom[T](write: PartialFunction[T, Try[BSONValue]]): BSONWriter[T] =
    from[T] { (v: T) =>
      write.lift(v) getOrElse {
        Failure(exceptions.ValueDoesNotMatchException(s"${v}"))
      }
    }

  /**
   * $createWriterBasedOn the given partial function.
   *
   * $valueDoesNotMatchException.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONWriter, BSONInteger }
   *
   * val strCodeToIntWriter = BSONWriter.collect[String] {
   *   case "zero" => BSONInteger(0)
   *   case "one" => BSONInteger(1)
   * }
   *
   * strCodeToIntWriter.writeTry("zero") // Success(BSONInteger(0))
   * strCodeToIntWriter.writeTry("one") // Success(BSONInteger(1))
   *
   * strCodeToIntWriter.writeTry("3")
   * // => Failure(ValueDoesNotMatchException(..))
   *
   * strCodeToIntWriter.writeOpt("4") // None (as failed)
   * }}}
   */
  def collect[T](write: PartialFunction[T, BSONValue]): BSONWriter[T] =
    apply[T] { v =>
      write.lift(v) getOrElse {
        throw exceptions.ValueDoesNotMatchException(s"${v}")
      }
    }

  /**
   * '''EXPERIMENTAL:''' (API may change without notice)
   *
   * Creates a [[BSONWriter]] accepting only [[scala.collection.Iterable]],
   * and applying the given safe `write` function to each element value.
   *
   * {{{
   * import reactivemongo.api.bson.BSONWriter
   *
   * case class Element(str: String, v: Int)
   *
   * def elementWriter: BSONWriter[Element] = ???
   *
   * val seqWriter: BSONWriter[Seq[Element]] =
   *   BSONWriter.sequence[Element](elementWriter writeTry _)
   * }}}
   */
  def sequence[T](write: T => Try[BSONValue]): BSONWriter[Seq[T]] =
    iterable[T, Seq](write)

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONWriter]] that creates tuple elements
   * as [[BSONArray]] elements.
   *
   * {{{
   * import reactivemongo.api.bson.BSONWriter
   *
   * val writer = BSONWriter.tuple2[String, Int]
   *
   * writer.writeTry("Foo" -> 20)
   * // => Success: ['Foo', 20]
   * }}}
   */
  def tuple2[A: BSONWriter, B: BSONWriter]: BSONWriter[(A, B)] =
    apply[(A, B)] { case (a, b) => BSONArray(a, b) }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONWriter]] that creates tuple elements
   * as [[BSONArray]] elements.
   */
  def tuple3[A: BSONWriter, B: BSONWriter, C: BSONWriter]: BSONWriter[(A, B, C)] =
    apply[(A, B, C)] { case (a, b, c) => BSONArray(a, b, c) }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONWriter]] that creates tuple elements
   * as [[BSONArray]] elements.
   */
  def tuple4[
      A: BSONWriter,
      B: BSONWriter,
      C: BSONWriter,
      D: BSONWriter
    ]: BSONWriter[(A, B, C, D)] =
    apply[(A, B, C, D)] { case (a, b, c, d) => BSONArray(a, b, c, d) }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONWriter]] that creates tuple elements
   * as [[BSONArray]] elements.
   */
  def tuple5[
      A: BSONWriter,
      B: BSONWriter,
      C: BSONWriter,
      D: BSONWriter,
      E: BSONWriter
    ]: BSONWriter[(A, B, C, D, E)] =
    apply[(A, B, C, D, E)] { case (a, b, c, d, e) => BSONArray(a, b, c, d, e) }

  // ---

  private[bson] trait DefaultWriter[T] extends BSONWriter[T] {
    protected def write: T => Try[BSONValue]

    def writeTry(value: T): Try[BSONValue] = write(value)
  }

  private[bson] trait OptionalWriter[T] extends BSONWriter[T] {
    protected def write: T => Option[BSONValue]

    override def writeOpt(value: T): Option[BSONValue] = write(value)

    def writeTry(v: T): Try[BSONValue] = write(v) match {
      case Some(bson) =>
        Success(bson)

      case _ =>
        Failure(exceptions.ValueDoesNotMatchException(s"${v}"))
    }
  }

  private[bson] trait FunctionalWriter[T] extends BSONWriter[T] {
    protected def write: T => BSONValue

    def writeTry(value: T): Try[BSONValue] = Try(write(value))
  }
}

/** A writer that is safe, as `writeTry` can always return a `Success`. */
private[reactivemongo] trait SafeBSONWriter[T] { writer: BSONWriter[T] =>
  def safeWrite(value: T): BSONValue

  final def writeTry(value: T): Success[BSONValue] = Success(safeWrite(value))
}

private[reactivemongo] object SafeBSONWriter {

  @com.github.ghik.silencer.silent
  def unapply[T](w: BSONWriter[T]): Option[SafeBSONWriter[T]] =
    implicitly[scala.reflect.ClassTag[SafeBSONWriter[T]]].unapply(w)
}
