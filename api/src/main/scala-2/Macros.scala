package reactivemongo.api.bson

import scala.util.Success

/**
 * Macros for generating `BSONReader` and `BSONWriter` at compile time.
 *
 * {{{
 * import reactivemongo.api.bson.Macros
 *
 * case class Person(name: String, surname: String)
 *
 * implicit val personHandler = Macros.handler[Person]
 * }}}
 *
 * @see [[MacroOptions]] for specific options
 * @see [[MacroConfiguration]] for extended configuration
 *
 * @define readerMacro Creates a [[BSONDocumentReader]] for type `A`
 * @define writerMacro Creates a [[BSONDocumentWriter]] for type `A`
 * @define handlerMacro Creates a [[BSONDocumentHandler]] for type `A`
 * @define defaultCfg The default [[MacroConfiguration]] is used (see [[Macros.configured]])
 * @define tparam @tparam A the type of the value represented as BSON
 * @define tparamOpts @tparam Opts the compile-time options
 */
object Macros extends MacroAnnotations {
  import language.experimental.macros

  /**
   * $readerMacro.
   * $defaultCfg.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocumentReader, Macros }
   *
   * case class Foo(bar: String, lorem: Int)
   *
   * val reader: BSONDocumentReader[Foo] = Macros.reader
   * }}}
   *
   * $tparam
   */
  @SuppressWarnings(Array("NullParameter"))
  def reader[A]: BSONDocumentReader[A] = macro MacroImpl.reader[A, MacroOptions.Default]

  /**
   * $readerMacro.
   * $defaultCfg, with given additional options.
   *
   * {{{
   * import reactivemongo.api.bson.{ Macros, MacroOptions }
   *
   * case class Foo(bar: String, lorem: Int)
   *
   * val reader = Macros.readerOpts[Foo, MacroOptions.Verbose]
   * }}}
   *
   * $tparam
   * $tparamOpts
   */
  @SuppressWarnings(Array("NullParameter"))
  def readerOpts[A, Opts <: MacroOptions.Default]: BSONDocumentReader[A] = macro MacroImpl.reader[A, Opts]

  /**
   * Creates a [[BSONReader]] for [[https://docs.scala-lang.org/overviews/core/value-classes.html Value Class]] `A`.
   *
   * The inner value will be directly read from BSON value.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONInteger, BSONReader, Macros }
   *
   * final class FooVal(val v: Int) extends AnyVal // Value Class
   *
   * val vreader: BSONReader[FooVal] = Macros.valueReader[FooVal]
   *
   * vreader.readTry(BSONInteger(1)) // Success(FooVal(1))
   * }}}
   */
  @SuppressWarnings(Array("PointlessTypeBounds", "NullParameter"))
  def valueReader[A <: AnyVal]: BSONReader[A] = macro MacroImpl.valueReader[A, MacroOptions.Default]

  /**
   * $writerMacro.
   * $defaultCfg.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocumentWriter, Macros }
   *
   * case class Foo(bar: String, lorem: Int)
   *
   * val writer: BSONDocumentWriter[Foo] = Macros.writer
   * }}}
   *
   * $tparam
   */
  @SuppressWarnings(Array("NullParameter"))
  def writer[A]: BSONDocumentWriter[A] = macro MacroImpl.writer[A, MacroOptions.Default]

  /**
   * Creates a [[BSONWriter]] for [[https://docs.scala-lang.org/overviews/core/value-classes.html Value Class]] `A`.
   *
   * The inner value will be directly writen from BSON value.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONWriter, Macros }
   *
   * final class FooVal(val v: Int) extends AnyVal // Value Class
   *
   * val vwriter: BSONWriter[FooVal] = Macros.valueWriter[FooVal]
   *
   * vwriter.writeTry(new FooVal(1)) // Success(BSONInteger(1))
   * }}}
   */
  @SuppressWarnings(Array("PointlessTypeBounds", "NullParameter"))
  def valueWriter[A <: AnyVal]: BSONWriter[A] = macro MacroImpl.valueWriter[A, MacroOptions.Default]

  /**
   * $writerMacro.
   * $defaultCfg, with given additional options.
   *
   * {{{
   * import reactivemongo.api.bson.{ Macros, MacroOptions }
   *
   * case class Foo(bar: String, lorem: Int)
   *
   * val writer = Macros.writerOpts[Foo, MacroOptions.DisableWarnings]
   * }}}
   *
   * $tparam
   * $tparamOpts
   */
  @SuppressWarnings(Array("NullParameter"))
  def writerOpts[A, Opts <: MacroOptions.Default]: BSONDocumentWriter[A] = macro MacroImpl.writer[A, Opts]

  /**
   * $handlerMacro.
   * $defaultCfg.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocumentHandler, Macros }
   *
   * case class Foo(bar: String, lorem: Int)
   *
   * val handler: BSONDocumentHandler[Foo] = Macros.handler
   * }}}
   *
   * $tparam
   */
  @SuppressWarnings(Array("NullParameter"))
  def handler[A]: BSONDocumentHandler[A] = macro MacroImpl.handler[A, MacroOptions.Default]

  /**
   * $handlerMacro.
   * $defaultCfg, with given additional options.
   *
   * {{{
   * import reactivemongo.api.bson.{ Macros, MacroOptions }
   *
   * case class Foo(bar: String, lorem: Int)
   *
   * val handler = Macros.handlerOpts[Foo, MacroOptions.Default]
   * }}}
   *
   * $tparam
   * $tparamOpts
   */
  @SuppressWarnings(Array("NullParameter"))
  def handlerOpts[A, Opts <: MacroOptions.Default]: BSONDocumentHandler[A] = macro MacroImpl.handler[A, Opts]

  /**
   * Creates a [[BSONHandler]] for [[https://docs.scala-lang.org/overviews/core/value-classes.html Value Class]] `A`.
   *
   * The inner value will be directly write from BSON value.
   *
   * {{{
   * import reactivemongo.api.bson.{
   *   BSONInteger, BSONReader, BSONWriter, Macros
   * }
   *
   * final class FooVal(val v: Int) extends AnyVal // Value Class
   *
   * val vreader: BSONReader[FooVal] = Macros.valueReader[FooVal]
   * val vwriter: BSONWriter[FooVal] = Macros.valueWriter[FooVal]
   *
   * vreader.readTry(BSONInteger(1)) // Success(FooVal(1))
   *
   * vwriter.writeTry(new FooVal(1)) // Success(BSONInteger(1))
   * }}}
   */
  @SuppressWarnings(Array("PointlessTypeBounds", "NullParameter"))
  def valueHandler[A <: AnyVal]: BSONHandler[A] = macro MacroImpl.valueHandler[A, MacroOptions.Default]

  // ---

  /**
   * Returns macros using the current BSON configuration.
   *
   * $tparamOpts
   *
   * {{{
   * import reactivemongo.api.bson.{
   *   BSONDocumentReader, MacroConfiguration, Macros
   * }
   *
   * case class Foo(name: String)
   *
   * // Materializes a `BSONDocumentReader[Foo]`,
   * // with the configuration resolved at compile time
   * val r1: BSONDocumentReader[Foo] = Macros.configured.reader[Foo]
   *
   * val r2: BSONDocumentReader[Foo] = Macros.configured(
   *   MacroConfiguration.simpleTypeName).reader[Foo]
   *
   * }}}
   */
  def configured[Opts <: MacroOptions](implicit config: MacroConfiguration.Aux[Opts]) = new WithOptions[Opts](config)

  /**
   * Returns an inference context to call the BSON macros,
   * using explicit compile-time options.
   *
   * $tparamOpts
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocumentWriter, Macros, MacroOptions }
   *
   * case class Bar(score: Float)
   *
   * val w: BSONDocumentWriter[Bar] =
   *   Macros.using[MacroOptions.Default].writer[Bar]
   * }}}
   */
  def using[Opts <: MacroOptions] =
    new WithOptions[Opts](MacroConfiguration[Opts]())

  // ---

  /**
   * Macros for generating `BSONReader` and `BSONWriter` at compile time,
   * with given options.
   *
   * @define readerMacro Creates a [[BSONDocumentReader]] for type `A`
   * @define writerMacro Creates a [[BSONDocumentWriter]] for type `A`
   * @define handlerMacro Creates a [[BSONDocumentHandler]] for type `A`
   * @define tparam @tparam A the type of the value represented as BSON
   *
   * @tparam Opts the compile-time options
   */
  final class WithOptions[Opts <: MacroOptions](
    val config: MacroConfiguration.Aux[Opts]) {

    def this() = this(MacroConfiguration.default)

    // ---

    /**
     * $readerMacro.
     *
     * $tparam
     */
    @SuppressWarnings(Array("NullParameter"))
    def reader[A]: BSONDocumentReader[A] = macro MacroImpl.configuredReader[A, Opts]

    /**
     * $writerMacro.
     *
     * $tparam
     */
    @SuppressWarnings(Array("NullParameter"))
    def writer[A]: BSONDocumentWriter[A] = macro MacroImpl.configuredWriter[A, Opts]

    /**
     * $handlerMacro.
     *
     * $tparam
     */
    @SuppressWarnings(Array("NullParameter"))
    def handler[A]: BSONDocumentHandler[A] = macro MacroImpl.configuredHandler[A, Opts]
  }

  // --- Internals

  /** Only for internal purposes */
  final class Placeholder private () {}

  /** Only for internal purposes */
  object Placeholder {
    private val instance = new Placeholder()

    implicit object Handler extends BSONDocumentHandler[Placeholder] {
      def readDocument(bson: BSONDocument) = Success(instance)

      @com.github.ghik.silencer.silent
      def writeTry(pl: Placeholder) = Success(BSONDocument.empty)
    }
  }

  /** Only for internal purposes */
  final class LocalVar[@specialized T] {
    private var underlying: T = _

    def take(value: T): LocalVar[T] = {
      underlying = value
      this
    }

    def value(): T = underlying
  }
}
