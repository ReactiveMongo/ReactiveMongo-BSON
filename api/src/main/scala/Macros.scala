package reactivemongo.api.bson

import scala.util.Success

/**
 * Macros for generating `BSONReader` and `BSONWriter` at compile time.
 *
 * {{{
 * case class Person(name: String, surname: String)
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
object Macros {
  import language.experimental.macros

  /**
   * $readerMacro.
   * $defaultCfg.
   *
   * $tparam
   */
  @SuppressWarnings(Array("NullParameter"))
  def reader[A]: BSONDocumentReader[A] = macro MacroImpl.reader[A, MacroOptions.Default]

  /**
   * $readerMacro.
   * $defaultCfg, with given additional options.
   *
   * $tparam
   * $tparamOpts
   */
  @SuppressWarnings(Array("NullParameter"))
  def readerOpts[A, Opts <: MacroOptions.Default]: BSONDocumentReader[A] = macro MacroImpl.reader[A, Opts]

  /**
   * $writerMacro.
   * $defaultCfg.
   *
   * $tparam
   */
  @SuppressWarnings(Array("NullParameter"))
  def writer[A]: BSONDocumentWriter[A] = macro MacroImpl.writer[A, MacroOptions.Default]

  /**
   * $writerMacro.
   * $defaultCfg, with given additional options.
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
   * $tparam
   */
  @SuppressWarnings(Array("NullParameter"))
  def handler[A]: BSONDocumentHandler[A] = macro MacroImpl.handler[A, MacroOptions.Default]

  /**
   * $handlerMacro.
   * $defaultCfg, with given additional options.
   *
   * $tparam
   * $tparamOpts
   */
  @SuppressWarnings(Array("NullParameter"))
  def handlerOpts[A, Opts <: MacroOptions.Default]: BSONDocumentHandler[A] = macro MacroImpl.handler[A, Opts]

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
   * import reactivemongo.api.bson.{
   *   BSONDocumentReader, MacroConfiguration, Macros
   * }
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

  // ---

  /** Annotations to use on case classes that are being processed by macros. */
  object Annotations {
    import scala.annotation.{ StaticAnnotation, meta }

    /**
     * Specify a key different from field name in your case class.
     * Convenient to use when you'd like to leverage mongo's `_id` index
     * but don't want to actually use `_id` in your code.
     *
     * {{{
     * case class Website(@Key("_id") url: String)
     * }}}
     *
     * Generated handler will map the `url` field in your code
     * to `_id` field in BSON
     *
     * @param key the desired key to use in BSON
     */
    @meta.param
    case class Key(key: String) extends StaticAnnotation

    /** Ignores a field */
    @meta.param
    final class Ignore extends StaticAnnotation {
      override def hashCode: Int = 1278101060

      override def equals(that: Any): Boolean = that match {
        case _: Ignore => true
        case _ => false
      }
    }

    /**
     * Indicates that if a property is represented as a document itself,
     * the document fields are directly included in top document,
     * rather than nesting it.
     *
     * {{{
     * case class Range(start: Int, end: Int)
     *
     * case class LabelledRange(
     *   name: String,
     *   @Flatten range: Range)
     *
     * // Flattened
     * BSONDocument("name" -> "foo", "start" -> 0, "end" -> 1)
     *
     * // Rather than:
     * // BSONDocument("name" -> "foo", "range" -> BSONDocument(
     * //   "start" -> 0, "end" -> 1))
     * }}}
     */
    @meta.param
    final class Flatten extends StaticAnnotation {
      override def hashCode: Int = 488571557

      override def equals(that: Any): Boolean = that match {
        case _: Flatten => true
        case _ => false
      }
    }

    /**
     * Indicates that if an `Option` property is empty,
     * it will be represented by `BSONNull` rather than being omitted.
     *
     * {{{
     * case class Foo(
     *   title: String,
     *   @NoneAsNull description: Option[String])
     * }}}
     */
    @meta.param
    final class NoneAsNull extends StaticAnnotation {
      override def hashCode: Int = 1667526726

      override def equals(that: Any): Boolean = that match {
        case _: NoneAsNull => true
        case _ => false
      }
    }
  }

  // --- Internal

  /** Only for internal purposes */
  final class Placeholder private () {}

  /** Only for internal purposes */
  object Placeholder {
    private val instance = new Placeholder()

    implicit object Handler
      extends BSONDocumentReader[Placeholder]
      with BSONDocumentWriter[Placeholder]
      with BSONHandler[Placeholder] {

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
