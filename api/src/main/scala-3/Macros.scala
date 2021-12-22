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
object Macros extends MacroAnnotations:

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
  inline def reader[A]: BSONDocumentReader[A] = ${
    MacroImpl.reader[A, MacroOptions.Default]
  }

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
  inline def readerOpts[
      A,
      Opts <: MacroOptions.Default
    ]: BSONDocumentReader[A] = ${ MacroImpl.reader[A, Opts] }

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
  inline def valueReader[A <: AnyVal]: BSONReader[A] =
    ${ MacroImpl.anyValReader[A, MacroOptions.Default] }

  /**
   * Creates a [[BSONReader]] for an [[https://dotty.epfl.ch/docs/reference/other-new-features/opaques.html opaque type alias]] `A`, that itself aliases a [[https://docs.scala-lang.org/overviews/core/value-classes.html Value Class]].
   *
   * The inner value will be directly written as BSON value.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONReader, Macros }
   *
   * opaque type Logarithm = Double
   *
   * object Logarithm {
   *   def apply(value: Double): Logarithm = value
   * }
   *
   * val vreader: BSONReader[Logarithm] = Macros.valueReader[Logarithm]
   *
   * vreader.readTry(BSONDouble(1.2)) // Success(Logarithm(1.2D))
   * }}}
   */
  inline def valueReader[A: OpaqueAlias]: BSONReader[A] =
    ${ MacroImpl.opaqueAliasReader[A, MacroOptions.Default] }

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
  inline def writer[A]: BSONDocumentWriter[A] =
    ${ MacroImpl.writer[A, MacroOptions.Default] }

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
  inline def valueWriter[A <: AnyVal]: BSONWriter[A] =
    ${ MacroImpl.anyValWriter[A, MacroOptions.Default] }

  /**
   * Creates a [[BSONWriter]] for an [[https://dotty.epfl.ch/docs/reference/other-new-features/opaques.html opaque type alias]] `A`, that itself aliases a [[https://docs.scala-lang.org/overviews/core/value-classes.html Value Class]].
   *
   * The inner value will be directly writen from BSON value.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONWriter, Macros }
   *
   * opaque type Logarithm = Double
   *
   * object Logarithm {
   *   def apply(value: Double): Logarithm = value
   * }
   *
   * val vwriter: BSONWriter[Logarithm] = Macros.valueWriter[Logarithm]
   *
   * vwriter.writeTry(Logarithm(1.2D)) // Success(BSONDouble(1.2))
   * }}}
   */
  inline def valueWriter[A: OpaqueAlias]: BSONWriter[A] =
    ${ MacroImpl.opaqueAliasWriter[A, MacroOptions.Default] }

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
  inline def writerOpts[
      A,
      Opts <: MacroOptions.Default
    ]: BSONDocumentWriter[A] =
    ${ MacroImpl.writer[A, Opts] }

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
  inline def valueHandler[A <: AnyVal]: BSONHandler[A] =
    ${ MacroImpl.anyValHandler[A, MacroOptions.Default] }

  /**
   * Creates a [[BSONHandler]] for an [[https://dotty.epfl.ch/docs/reference/other-new-features/opaques.html opaque type alias]] `A`, that itself aliases a [[https://docs.scala-lang.org/overviews/core/value-classes.html Value Class]].
   *
   * The inner value will be directly written as BSON value.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONHandler, Macros }
   *
   * opaque type Logarithm = Double
   *
   * object Logarithm {
   *   def apply(value: Double): Logarithm = value
   * }
   *
   * val vhandler: BSONHandler[Logarithm] = Macros.valueHandler[Logarithm]
   *
   * vhandler.readTry(BSONDouble(1.2)) // Success(Logarithm(1.2D))
   * vhandler.writeTry(Logarithm(2.34D)) // Success(BSONDouble(2.34D))
   * }}}
   */
  inline def valueHandler[A: OpaqueAlias]: BSONHandler[A] =
    ${ MacroImpl.opaqueAliasHandler[A, MacroOptions.Default] }

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
  def configured[Opts <: MacroOptions](
      using
      config: MacroConfiguration.Aux[Opts]
    ) = new WithOptions[Opts](config)

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
    inline def reader[A]: BSONDocumentReader[A] =
      ${ MacroImpl.readerWithConfig[A, Opts]('config) }

    /**
     * $writerMacro.
     *
     * $tparam
     */
    inline def writer[A]: BSONDocumentWriter[A] =
      ${ MacroImpl.writerWithConfig[A, Opts]('config) }

    /**
     * TODO
     * $handlerMacro.
     *
     * $tparam
     *    inline def handler[A]: BSONDocumentHandler[A] =
     *      ${ MacroImpl.handlerWithConfig[A, Opts]('config) }
     */
  }

  // --- Internals TODO: Common with Scala2

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
end Macros

sealed trait OpaqueAlias[T] {}

object OpaqueAlias:
  import scala.quoted.{ Expr, Quotes, Type }

  inline given materialized[T]: OpaqueAlias[T] = ${ impl[T] }

  private def impl[T: Type](using q: Quotes): Expr[OpaqueAlias[T]] = {
    import q.reflect.*

    TypeRepr.of[T] match {
      case ref: TypeRef if ref.isOpaqueAlias =>
        ref.asType match {
          case tpe @ '[t] => '{ new OpaqueAlias[T] {} }
        }

      case tpr =>
        report.errorAndAbort(s"${tpr.show} is not an opaque alias")
    }
  }

end OpaqueAlias
