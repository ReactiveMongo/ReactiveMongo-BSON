package reactivemongo.api.bson

import scala.quoted.{ Expr, Quotes, Type, quotes }

private[api] object MacroImpl {

  def documentClass[A: Type](using q: Quotes): Expr[DocumentClass[A]] = {
    import q.reflect.*

    val anyValTpe = TypeRepr.of[AnyVal]
    val bsonValueTpe = TypeRepr.of[BSONValue]
    val bsonDocTpe = TypeRepr.of[BSONDocument]
    val aTpe = TypeRepr.of[A].dealias

    def throwNotDoc =
      report.throwError(s"Type ${aTpe.show} is not a document one")

    if (aTpe <:< bsonValueTpe) {
      if (aTpe <:< bsonDocTpe) {
        '{ DocumentClass.unchecked[A] }
      } else {
        throwNotDoc
      }
    } else
      aTpe.classSymbol match {
        case Some(tpeSym) => {
          if (
            (tpeSym.flags.is(Flags.Abstract) && !(aTpe <:< anyValTpe)) ||
            tpeSym.flags.is(Flags.Trait) ||
            tpeSym.companionClass.declaredMethod("unapply").nonEmpty
          ) {
            '{ DocumentClass.unchecked[A] }
          } else {
            throwNotDoc
          }
        }

        case _ =>
          throwNotDoc
      }
  }

  def migrationRequired[A: Type](
      details: Expr[String]
    )(using
      Quotes
    ): Expr[A] = {
    if (
      !sys.props.get("reactivemongo.api.migrationRequired.nonFatal").exists {
        v => v.toLowerCase == "true" || v.toLowerCase == "yes"
      }
    ) {
      val q = quotes
      val msg: String = q.value(details) match {
        case Some(str) =>
          s"Migration required: $str"

        case _ => {
          q.reflect.report.warning(
            s"Invalid 'details' parameter for 'migrationRequired': ${q show details}"
          )

          "Migration required"
        }
      }

      q.reflect.report.throwError(msg)
    }

    '{ scala.Predef.`???` }
  }

  // ---

  import Macros.Annotations, Annotations.{ DefaultValue, Key /*,
    Flatten,
    Ignore,
    NoneAsNull,
    Reader,
    Writer*/ }

  sealed trait MacroHelpers[A] { _i: ImplicitResolver[A] =>
    protected val quotes: Quotes

    import quotes.reflect.*
    private
    given q: Quotes = quotes

    /* Type of compile-time options; See [[MacroOptions]] */
    protected def optsTpe: TypeRepr

    protected def aTpe: TypeRepr

    // --- Shared trees and types

    private val scalaPkg = "_root_.scala" // TODO: Term
    protected final val colPkg = s"${scalaPkg}.collection.immutable"
    protected final val utilPkg = s"${scalaPkg}.util"
    protected final val reflPkg = s"${scalaPkg}.reflect"

    protected final val bsonPkg = "_root_.reactivemongo.api.bson"

    protected final val exceptionsPkg = "${bsonPkg}.exceptions"

    protected final val optionTpe: TypeRepr = TypeRepr.of[Option[_]]

    protected final val anyValTpe: TypeRepr = TypeRepr.of[AnyVal]

    private

    given defaultValueAnnotationTpe: Type[DefaultValue] =
      Type.of[DefaultValue]

    protected final val defaultValueAnnotationRepr: TypeRepr =
      TypeRepr.of[DefaultValue]

    // --- Macro configuration helpers ---

    // Init MacroConfiguration (possibility lazy) in a local val,
    // to avoid evaluating the configuration each time required
    // in the generated handler.
    protected def macroCfg: String //TODO: TermName

    // TODO: @inline protected def macroCfgInit: Tree = EmptyTree

    // --- Case classes helpers ---

    protected final def paramName(param: Symbol): String =
      param.annotations.collect {
        case ann if ann.tpe =:= typeOf[Key] =>
          ann.tree.children.tail.collect {
            case l: Literal =>
              l.value.value

            case _ =>
              abort(
                "Annotation @Key must be provided with a pure/literal value"
              )

          }.collect { case value: String => value }
      }.flatten.headOption getOrElse param.name.toString

    /*
    protected final def ignoreField(param: Symbol): Boolean =
      param.annotations.exists(ann =>
        ann.tree.tpe =:= typeOf[Ignore] || ann.tree.tpe =:= typeOf[transient])

    // --- Union helpers ---

    protected final lazy val unionTypes: Option[List[c.Type]] =
      parseUnionTypes orElse directKnownSubclasses

    protected def parseUnionTypes = Option.empty[List[c.Type]]

    private def directKnownSubclasses: Option[List[Type]] = {
      // Workaround for SI-7046: https://issues.scala-lang.org/browse/SI-7046
      val tpeSym = aTpe.typeSymbol.asClass

      @annotation.tailrec
      def allSubclasses(path: Iterable[Symbol], subclasses: Set[Type]): Set[Type] = path.headOption match {
        case Some(cls: ClassSymbol) if (
          tpeSym != cls && !cls.isAbstract &&
          cls.selfType.baseClasses.contains(tpeSym)) => {
          val newSub: Set[Type] = if ({
            val tpe = cls.typeSignature
            !applyMethod(tpe).isDefined || !unapplyMethod(tpe).isDefined
          }) {
            warn(s"Cannot handle class ${cls.fullName}: no case accessor")
            Set.empty
          } else if (cls.typeParams.nonEmpty) {
            warn(s"Cannot handle class ${cls.fullName}: type parameter not supported")
            Set.empty
          } else Set(cls.selfType)

          allSubclasses(path.tail, subclasses ++ newSub)
        }

        case Some(o: ModuleSymbol) if (
          o.companion == NoSymbol && // not a companion object
          o.typeSignature.baseClasses.contains(tpeSym)) => {
          val newSub: Set[Type] = if (!o.moduleClass.asClass.isCaseClass) {
            warn(s"Cannot handle object ${o.fullName}: no case accessor")
            Set.empty
          } else Set(o.typeSignature)

          allSubclasses(path.tail, subclasses ++ newSub)
        }

        case Some(o: ModuleSymbol) if (
          o.companion == NoSymbol // not a companion object
        ) => allSubclasses(path.tail, subclasses)

        case Some(_) => allSubclasses(path.tail, subclasses)

        case _ => subclasses
      }

      if (tpeSym.isSealed && tpeSym.isAbstract) {
        Some(allSubclasses(tpeSym.owner.typeSignature.decls, Set.empty).toList)
      } else None
    }

    // --- Type helpers ---

    @inline protected final def isOptionalType(implicit A: c.Type): Boolean =
      optionTpe.typeConstructor == A.typeConstructor

    @annotation.tailrec protected final def leafType(t: Type): Type =
      t.typeArgs.headOption match {
        case Some(arg) => leafType(arg)
        case _ => t
      }

    /* Some(A) for Option[A] else None */
    protected object OptionTypeParameter {
      def unapply(tpe: c.Type): Option[c.Type] = {
        if (isOptionalType(tpe)) {
          tpe match {
            case TypeRef(_, _, args) => args.headOption
            case _ => None
          }
        } else None
      }
    }

    @inline protected def isSingleton(tpe: Type): Boolean =
      tpe <:< typeOf[Singleton]

    @inline protected def companion(tpe: Type): Symbol =
      tpe.typeSymbol.companion

    private def applyMethod(implicit tpe: Type): Option[Symbol] =
      companion(tpe).typeSignature.decl(TermName("apply")) match {
        case NoSymbol => {
          debug(s"No apply function found for $tpe")

          None
        }

        case s => Some(s)
      }

    private def unapplyMethod(implicit tpe: Type): Option[MethodSymbol] = companion(tpe).typeSignature.decl(TermName("unapply")) match {
      case NoSymbol => {
        debug(s"No unapply function found for $tpe")

        None
      }

      case s => {
        val alt = s.asTerm.alternatives

        if (alt.tail.nonEmpty) {
          warn(s"""Multiple 'unapply' declared on '${tpe.typeSymbol.fullName}': ${alt.map(_.info).mkString("\n- ", "\n- ", "\n\n")}""")
        }

        alt.headOption.map(_.asMethod)
      }
    }

    protected final def unapplyReturnTypes(deconstructor: MethodSymbol): List[c.Type] = {
      val opt = deconstructor.returnType match {
        case TypeRef(_, _, Nil) => Some(Nil)

        case TypeRef(_, _, (t @ TypeRef(_, _, Nil)) :: _) =>
          Some(List(t))

        case TypeRef(_, _, (typ @ TypeRef(_, t, args)) :: _) =>
          Some(
            if (t.name.toString.matches("Tuple\\d\\d?")) args else List(typ))

        case _ => None
      }

      opt getOrElse abort("something wrong with unapply type")
    }

    /**
     * @return (apply symbol, unapply symbol)
     */
    @SuppressWarnings(Array("ListSize"))
    protected final def matchingApplyUnapply(implicit tpe: Type): Option[(MethodSymbol, MethodSymbol)] = for {
      applySymbol <- applyMethod(tpe)
      unapply <- unapplyMethod(tpe)
      alternatives = applySymbol.asTerm.alternatives.map(_.asMethod)
      u = unapplyReturnTypes(unapply)

      apply <- alternatives.find { alt =>
        alt.paramLists match {
          case params :: ps if (ps.isEmpty || ps.headOption.flatMap(
            _.headOption).exists(_.isImplicit)) => if (params.size != u.size) false else {

            // TODO: Better warning, allow to skip
            deepConforms(lazyZip(params.map(_.typeSignature), u).toSeq)
          }

          case _ => {
            warn(s"Constructor with multiple parameter lists is not supported: ${tpe.typeSymbol.name}${alt.typeSignature}")

            false
          }
        }
      }
    } yield apply -> unapply

    /* Deep check for type compatibility */
    @annotation.tailrec
    @SuppressWarnings(Array("ListSize"))
    private def deepConforms(types: Seq[(Type, Type)]): Boolean =
      types.headOption match {
        case Some((TypeRef(NoPrefix, a, _),
          TypeRef(NoPrefix, b, _))) => { // for generic parameter
          if (a.fullName != b.fullName) {
            warn(s"Type symbols are not compatible: $a != $b")

            false
          } else deepConforms(types.tail)
        }

        case Some((a, b)) if (a.typeArgs.size != b.typeArgs.size) => {
          warn(s"Type parameters are not matching: $a != $b")

          false
        }

        case Some((a, b)) if a.typeArgs.isEmpty =>
          if (a =:= b) deepConforms(types.tail) else {
            warn(s"Types are not compatible: $a != $b")

            false
          }

        case Some((a, b)) if (a.baseClasses != b.baseClasses) => {
          warn(s"Generic types are not compatible: $a != $b")

          false
        }

        case Some((a, b)) =>
          deepConforms(lazyZip(a.typeArgs, b.typeArgs) ++: types.tail)

        case _ => true
      }

    // --- Context helpers ---

    @inline protected final def hasOption[O: c.TypeTag]: Boolean =
      optsTpe <:< typeOf[O]

    /* Prints a compilation warning, if allowed. */
    protected final lazy val warn: String => Unit = {
      if (hasOption[MacroOptions.DisableWarnings]) {
        (_: String) => ()
      } else { (msg: String) =>
        c.warning(c.enclosingPosition, msg)
      }
    }

    /* Prints debug entry, if allowed. */
    protected final lazy val debug: String => Unit = {
      if (!hasOption[MacroOptions.Verbose]) {
        (_: String) => ()
      } else { (msg: String) =>
        c.echo(c.enclosingPosition, msg)
      }
    }

    @inline protected final def abort(msg: String) =
      c.abort(c.enclosingPosition, msg)
     */
  }

  sealed trait ImplicitResolver[A] {
    protected val quotes: Quotes

    import quotes.reflect.*
    private
    given q: Quotes = quotes

    protected
    given aTpe: Type[A]
    protected val aTpeRepr: TypeRepr

    import Macros.Placeholder

    // The placeholder type
    protected final val PlaceholderType: TypeRepr =
      TypeRepr.of[Placeholder]

    /**
     * Refactor the input types, by replacing any type matching the `filter`,
     * by the given `replacement`.
     */
    @annotation.tailrec
    private def refactor(
        boundTypes: Map[String, TypeRepr]
      )(in: List[TypeRepr],
        base: (TypeRepr, /*Type*/ Symbol),
        out: List[TypeRepr],
        tail: List[
          (List[TypeRepr], (TypeRepr, /*Type*/ Symbol), List[TypeRepr])
        ],
        filter: TypeRepr => Boolean,
        replacement: TypeRepr,
        altered: Boolean
      ): (TypeRepr, Boolean) = in match {
      case tpe :: ts => {
        boundTypes.getOrElse(tpe.typeSymbol.fullName, tpe) match {
          case t if filter(t) =>
            refactor(boundTypes)(
              ts,
              base,
              (replacement :: out),
              tail,
              filter,
              replacement,
              true
            )

          case AppliedType(t, as) if as.nonEmpty =>
            refactor(boundTypes)(
              as,
              t -> t.typeSymbol,
              List.empty,
              (ts, base, out) :: tail,
              filter,
              replacement,
              altered
            )

          case t =>
            refactor(boundTypes)(
              ts,
              base,
              (t :: out),
              tail,
              filter,
              replacement,
              altered
            )
        }
      }

      case _ => {
        val tpe = base._1.appliedTo(out.reverse)

        tail match {
          case (x, y, more) :: ts =>
            refactor(boundTypes)(
              x,
              y,
              (tpe :: more),
              ts,
              filter,
              replacement,
              altered
            )

          case _ => tpe -> altered
        }
      }
    }

    /**
     * Replaces any reference to the type itself by the Placeholder type.
     * @return the normalized type + whether any self reference has been found
     */
    private def normalized(
        boundTypes: Map[String, TypeRepr]
      )(tpe: TypeRepr
      ): (TypeRepr, Boolean) =
      boundTypes.getOrElse(tpe.typeSymbol.fullName, tpe) match {
        case t if (t =:= aTpeRepr) => PlaceholderType -> true

        case AppliedType(t, args) if args.nonEmpty =>
          refactor(boundTypes)(
            args,
            t -> t.typeSymbol,
            List.empty,
            List.empty,
            _ =:= aTpeRepr,
            PlaceholderType,
            false
          )

        case t => t -> false
      }

    /* Restores reference to the type itself when Placeholder is found. */
    private def denormalized(
        boundTypes: Map[String, TypeRepr]
      )(ptype: TypeRepr
      ): TypeRepr = ptype match {
      case t if (t =:= PlaceholderType) =>
        aTpeRepr

      case AppliedType(_, args) if args.nonEmpty =>
        refactor(boundTypes)(
          args,
          ptype -> ptype.typeSymbol,
          List.empty,
          List.empty,
          _ == PlaceholderType,
          aTpeRepr,
          false
        )._1

      case _ => ptype
    }

    private class ImplicitTransformer(
        boundTypes: Map[String, TypeRepr],
        forwardSuffix: String)
        extends TreeMap {
      private val denorm = denormalized(boundTypes) _

      private def forwardName = {
        val t = aTpeRepr // TODO: Review
        TermRef(t, s"forward$forwardSuffix")
      }

      override def transformTree(tree: Tree)(owner: Symbol): Tree = tree match {
        case tt: TypeTree =>
          super.transformTree(TypeTree.of(using denorm(tt.tpe).asType))(owner)

        case Select(Select(This(Some("Macros")), t), sym)
            if (t.toString == "Placeholder" && sym.toString == "Handler") =>
          super.transformTree(Ident(forwardName))(owner)

        case _ => super.transformTree(tree)(owner)
      }
    }

    private def createImplicit(
        debug: String => Unit,
        boundTypes: Map[String, TypeRepr]
      )(tc: TypeRepr,
        ptype: TypeRepr,
        tx: TreeMap
      ): Implicit = {
      val tpe = ptype
      val (ntpe, selfRef) = normalized(boundTypes)(tpe)
      val ptpe = boundTypes.getOrElse(ntpe.typeSymbol.fullName, ntpe)

      // infers given
      val neededGivenType = tc.appliedTo(ptpe).asType
      val summoned = summon(using neededGivenType)

      val neededGiven: Tree = if (!selfRef) {
        TypeTree.of(using summoned)
      } else { //c.untypecheck(
        // Reset the type attributes on the refactored tree for the given
        val g = TypeTree.of(using summoned)
        tx.transformTree(g)(g.symbol)
      }

      debug(s"// Resolve given ${tc} for ${ntpe} as ${neededGivenType} (self? ${selfRef}) = ${neededGiven}")

      neededGiven -> selfRef
    }

    protected def resolver(
        boundTypes: Map[String, TypeRepr],
        forwardSuffix: String,
        debug: String => Unit
      )(tc: TypeRepr
      ): TypeRepr => Implicit = {
      val tx = new ImplicitTransformer(boundTypes, forwardSuffix)
      createImplicit(debug, boundTypes)(tc, _: TypeRepr, tx)
    }

    // To print the implicit types in the compiler messages
    private def prettyType(
        boundTypes: Map[String, TypeRepr]
      )(t: TypeRepr
      ): String =
      boundTypes.getOrElse(t.typeSymbol.fullName, t) match {
        case AppliedType(base, args) if args.nonEmpty =>
          s"""${base.typeSymbol.fullName}[${args
            .map(prettyType(boundTypes)(_))
            .mkString(", ")}]"""

        case t => t.typeSymbol.fullName
      }

    type Implicit = (Tree, Boolean)
  }
}
