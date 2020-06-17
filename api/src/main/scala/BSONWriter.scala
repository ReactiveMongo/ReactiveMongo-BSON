package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

/**
 * A writer that produces a subtype of [[BSONValue]] from an instance of `T`.
 */
trait BSONWriter[T] {
  /** Tries to produce a BSON value from an instance of `T`. */
  def writeTry(t: T): Try[BSONValue]

  /**
   * Tries to produce a BSON value from an instance of `T`,
   * returns `None` if an error occurred.
   */
  def writeOpt(t: T): Option[BSONValue] = writeTry(t).toOption

  /**
   * Prepares a BSON writer that returns the result of applying `f`
   * on the BSON value from this writer.
   *
   * If the `f` function is not defined for a [[BSONValue]],
   * it will results in a `Failure`.
   *
   * @param f the partial function to apply
   */
  final def afterWrite(f: PartialFunction[BSONValue, BSONValue]): BSONWriter[T] = BSONWriter.from[T] {
    writeTry(_).flatMap { before =>
      f.lift(before) match {
        case Some(after) =>
          Success(after)

        case _ =>
          Failure(exceptions.ValueDoesNotMatchException(
            BSONValue pretty before))
      }
    }
  }

  /**
   * Prepares a BSON writer that converts the input before calling
   * the current writer.
   *
   * @param f the function apply the `U` input value to convert at `T` value used to the current writer
   */
  def beforeWrite[U](f: U => T): BSONWriter[U] =
    BSONWriter.from[U] { u => writeTry(f(u)) }
}

/**
 * [[BSONWriter]] factories.
 *
 * @define valueDoesNotMatchException A [[exceptions.ValueDoesNotMatchException]] is returned as `Failure` for any value that is not matched by the `write` function
 */
object BSONWriter extends BSONWriterCompat {
  /**
   * Creates a [[BSONWriter]] based on the given `write` function.
   * This function is called within a [[scala.util.Try]].
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONWriter, BSONString }
   *
   * case class Foo(value: String)
   *
   * val foo: BSONWriter[Foo] = BSONWriter { f: Foo => BSONString(f.value) }
   * }}}
   */
  def apply[T](write: T => BSONValue): BSONWriter[T] = {
    @inline def w = write
    new FunctionalWriter[T] {
      val write = w
    }
  }

  private[bson] def safe[T](write: T => BSONValue): BSONWriter[T] with SafeBSONWriter[T] = new BSONWriter[T] with SafeBSONWriter[T] {
    def safeWrite(value: T) = write(value)
  }

  /**
   * Creates a [[BSONWriter]] based on the given `write` function.
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
   * Creates a [[BSONWriter]] based on the given safe `write` function.
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
   * '''EXPERIMENTAL:''' Creates a [[BSONWriter]] based on the given
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
  def collectFrom[T](write: PartialFunction[T, Try[BSONValue]]): BSONWriter[T] = {
    @inline def w = write
    new DefaultWriter[T] {
      val write = { v: T =>
        w.lift(v) getOrElse {
          Failure(exceptions.ValueDoesNotMatchException(s"${v}"))
        }
      }
    }
  }

  /**
   * Creates a [[BSONWriter]] based on the given partial function.
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
    apply[(A, B)] {
      case (a, b) => BSONArray(a, b)
    }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONWriter]] that creates tuple elements
   * as [[BSONArray]] elements.
   */
  def tuple3[A: BSONWriter, B: BSONWriter, C: BSONWriter]: BSONWriter[(A, B, C)] = apply[(A, B, C)] {
    case (a, b, c) => BSONArray(a, b, c)
  }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONWriter]] that creates tuple elements
   * as [[BSONArray]] elements.
   */
  def tuple4[A: BSONWriter, B: BSONWriter, C: BSONWriter, D: BSONWriter]: BSONWriter[(A, B, C, D)] = apply[(A, B, C, D)] {
    case (a, b, c, d) => BSONArray(a, b, c, d)
  }

  /**
   * '''EXPERIMENTAL:''' Creates a [[BSONWriter]] that creates tuple elements
   * as [[BSONArray]] elements.
   */
  def tuple5[A: BSONWriter, B: BSONWriter, C: BSONWriter, D: BSONWriter, E: BSONWriter]: BSONWriter[(A, B, C, D, E)] = apply[(A, B, C, D, E)] {
    case (a, b, c, d, e) => BSONArray(a, b, c, d, e)
  }

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
  def unapply[T](w: BSONWriter[T]): Option[SafeBSONWriter[T]] = w match {
    case s: SafeBSONWriter[T] => Some(s)
    case _ => None
  }
}
