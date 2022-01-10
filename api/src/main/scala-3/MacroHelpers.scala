package reactivemongo.api.bson

import scala.util.{ Try => TryResult }

import scala.quoted.{ Expr, Quotes, Type }

import Macros.Annotations.{ Ignore, Key }

private[bson] trait MacroHelpers[A] extends OptionSupport with MacroLogging {
  _i: ImplicitResolver[A] with QuotesHelper =>

  type Q <: Quotes
  protected val quotes: Q

  import quotes.reflect.*

    // format: off
    protected given q: Q = quotes
    // format: on

  protected[api] def aTpe: Type[A]
  protected[api] def aTpeRepr: TypeRepr
  protected given aType: Type[A] = aTpe

  // --- Shared trees and types

  protected final lazy val optionTpe: TypeRepr = TypeRepr.of[Option[_]]

  protected final lazy val tryTpe: TypeRepr = TypeRepr.of[TryResult[_]]

  protected lazy val bsonDocTpe = TypeRepr.of[BSONDocument]

  protected lazy val bsonValTpe = TypeRepr.of[BSONValue]

  protected def keyType: Type[Key]

  protected final lazy val keyRepr: TypeRepr = TypeRepr.of(using keyType)

  protected def ignoreType: Type[Ignore]

  protected final lazy val ignoreRepr: TypeRepr =
    TypeRepr.of(using ignoreType)

  protected final lazy val transientRepr: TypeRepr = TypeRepr.of[transient]

  // --- Macro configuration helpers ---

  protected def macroCfgInit: Expr[MacroConfiguration]

  protected def withMacroCfg[T: Type](
      body: Expr[MacroConfiguration] => Expr[T]
    ): Expr[T] = '{
    val config: MacroConfiguration = ${ macroCfgInit }
    ${ body('config) }
  }

  // --- Case classes helpers ---

  @inline protected final def isClass(tpr: TypeRepr): Boolean = {
    lazy val tpeFlags = tpr.typeSymbol.flags

    !tpeFlags.is(Flags.Trait) && !tpeFlags.is(Flags.Abstract) &&
    !tpeFlags.is(Flags.Module)
  }

  /**
   * @param param the parameter/field symbol
   */
  protected final def fieldKey(param: Symbol): String =
    param.getAnnotation(keyRepr.typeSymbol).collect {
      case Apply(_, List(Literal(StringConstant(value)))) => value
    } getOrElse param.name.toString

  protected final def ignoreField(param: Symbol): Boolean =
    param.annotations.exists { ann =>
      ann.tpe =:= ignoreRepr || ann.tpe =:= transientRepr
    }

  /**
   * Returns the name of the BSON field according the field key
   * (either the Scala property name or the value of the `@Key` annotation).
   */
  protected final lazy val fieldName: Function2[Expr[MacroConfiguration], String, Expr[String]] = {
    (cfg, nme) => '{ ${ cfg }.fieldNaming(${ Expr(nme) }) }
  }

  // --- Union helpers ---

  protected final lazy val subTypes: Option[(List[TypeRepr], /* exhaustive: */ Boolean)] = {
    lazy val subClasses = knownSubclasses(aTpeRepr)

    val restricted = parseRestrictedSubTypes

    restricted match {
      case None =>
        subClasses.map(_ -> true)

      case Some(types) =>
        subClasses match {
          case Some(classes) => {
            val exhaustive = types.size == classes.size

            Some(types -> exhaustive)
          }

          case None =>
            Some(types -> false)
        }
    }
  }

  protected def unionTypes = Option.empty[List[TypeRepr]]

  protected def parseRestrictedSubTypes = Option.empty[List[TypeRepr]]

  // --- Type helpers ---

  @inline protected final def isOptionalType(tpr: TypeRepr): Boolean =
    tpr <:< optionTpe

  @annotation.tailrec
  protected final def leafType(tpr: TypeRepr): TypeRepr = tpr match {
    case AppliedType(_, a :: _) =>
      leafType(a)

    case _ =>
      tpr
  }

  /* Some(A) for Option[A] else None */
  protected object OptionTypeParameter {

    def unapply(tpr: TypeRepr): Option[TypeRepr] = {
      if (isOptionalType(tpr)) {
        tpr.typeArgs.headOption // TODO: Test case like OptionalInt in play-json
      } else None
    }
  }
}

private[bson] trait MacroTopHelpers[A] extends MacroHelpers[A] {
  _i: ImplicitResolver[A] with QuotesHelper =>

  import quotes.reflect.*

  private lazy val unionOptionTpe = TypeRepr.of[MacroOptions.UnionType]
  private lazy val unionTpe = TypeRepr.of[MacroOptions.\/]

  protected override def unionTypes: Option[List[TypeRepr]] = {
    @annotation.tailrec
    def parse(in: List[TypeRepr], out: List[TypeRepr]): List[TypeRepr] =
      in.headOption match {
        case Some(OrType(a, b)) =>
          parse(a :: b :: in.tail, out)

        case Some(o) if isClass(o) =>
          parse(in.tail, o :: out)

        case Some(_) =>
          List.empty

        case _ =>
          out.reverse
      }

    aTpeRepr.dealias match {
      case union @ OrType(a, b) =>
        Some(parse(a :: b :: Nil, Nil))

      case _ =>
        None
    }
  }

  protected override def parseRestrictedSubTypes: Option[List[TypeRepr]] = {
    @annotation.tailrec
    def parseTypes(
        types: List[TypeRepr],
        found: List[TypeRepr]
      ): List[TypeRepr] =
      types match {
        case AppliedType(t, List(a, b)) :: rem if (t <:< unionTpe) =>
          parseTypes(a :: b :: rem, found)

        case (app @ AppliedType(_, _)) :: _ =>
          report.errorAndAbort(
            s"Union type parameters expected: ${prettyType(app)}"
          )

        case typ :: rem if (typ <:< aTpeRepr) =>
          parseTypes(rem, typ :: found)

        case _ :: rem =>
          parseTypes(rem, found)

        case _ => found
      }

    @annotation.tailrec
    def tree(in: List[TypeRepr]): Option[TypeRepr] = in.headOption match {
      case Some(AppliedType(t, lst)) if t <:< unionOptionTpe =>
        lst.headOption

      case Some(Refinement(parent, _, _)) if parent <:< unionOptionTpe =>
        parent match {
          case AppliedType(_, args) => args.headOption
          case _                    => None
        }

      case Some(and: AndType) =>
        tree(and.left :: and.right :: in.tail)

      case Some(t) =>
        tree(in.tail)

      case None =>
        Option.empty[TypeRepr]
    }

    tree(List(optsTpr)).flatMap { t =>
      val types = parseTypes(List(t), Nil)

      if (types.isEmpty) None else Some(types)
    }
  }
}

private[bson] trait MacroLogging { _self: OptionSupport =>
  type Q <: Quotes
  protected val quotes: Q

  import quotes.reflect.*

  protected given disableWarningsTpe: Type[MacroOptions.DisableWarnings]

  protected given verboseTpe: Type[MacroOptions.Verbose]

  // --- Context helpers ---

  /* Prints a compilation warning, if allowed. */
  final lazy val warn: String => Unit = {
    if (hasOption[MacroOptions.DisableWarnings]) { (_: String) => () }
    else {
      report.warning(_: String)
    }
  }

  /* Prints debug entry, if allowed. */
  final lazy val debug: String => Unit = {
    if (!hasOption[MacroOptions.Verbose]) { (_: String) => () }
    else {
      report.info(_: String)
    }
  }
}

private[bson] trait OptionSupport {
  type Q <: Quotes
  protected val quotes: Q

  import quotes.reflect.*

    // format: off
    private given q: Q = quotes

    protected type Opts <: MacroOptions

    /* Type of compile-time options; See [[MacroOptions]] */
    protected def optsTpe: Type[Opts]
    protected def optsTpr: TypeRepr

    @inline protected final def hasOption[O: Type]: Boolean =
      optsTpr <:< TypeRepr.of[O]
  }
