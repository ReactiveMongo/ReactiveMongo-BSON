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

  // --- Internals TODO: Common

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

/* TODO
  /** Only for internal purposes */
  final class LocalVar[@specialized T] {
    private var underlying: T = _

    def take(value: T): LocalVar[T] = {
      underlying = value
      this
    }

    def value(): T = underlying
  }
 */
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
