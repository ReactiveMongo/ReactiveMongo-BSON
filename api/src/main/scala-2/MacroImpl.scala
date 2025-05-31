package reactivemongo.api.bson

import scala.util.{ Try => UTry }

import scala.collection.immutable.Set

import scala.reflect.macros.blackbox.Context

private[api] class MacroImpl(val c: Context) {
  import c.universe._

  import Macros.Annotations, Annotations.{
    DefaultValue,
    Flatten,
    Ignore,
    Key,
    NoneAsNull,
    Reader,
    Writer
  }

  def reader[
      A: c.WeakTypeTag,
      Opts: c.WeakTypeTag
    ]: c.Expr[BSONDocumentReader[A]] =
    readerWithConfig[A, Opts](implicitOptionsConfig)

  def configuredReader[
      A: c.WeakTypeTag,
      Opts: c.WeakTypeTag
    ]: c.Expr[BSONDocumentReader[A]] =
    readerWithConfig[A, Opts](withOptionsConfig)

  @SuppressWarnings(Array("PointlessTypeBounds"))
  def valueReader[
      A <: AnyVal: c.WeakTypeTag,
      Opts: c.WeakTypeTag
    ]: c.Expr[BSONReader[A]] =
    reify(BSONReader.from[A] { macroVal =>
      createHelper[A, Opts](implicitOptionsConfig).valueReaderBody.splice
    })

  def writer[
      A: c.WeakTypeTag,
      Opts: c.WeakTypeTag
    ]: c.Expr[BSONDocumentWriter[A]] =
    writerWithConfig[A, Opts](implicitOptionsConfig)

  def configuredWriter[
      A: c.WeakTypeTag,
      Opts: c.WeakTypeTag
    ]: c.Expr[BSONDocumentWriter[A]] =
    writerWithConfig[A, Opts](withOptionsConfig)

  @SuppressWarnings(Array("PointlessTypeBounds"))
  def valueWriter[
      A <: AnyVal: c.WeakTypeTag,
      Opts: c.WeakTypeTag
    ]: c.Expr[BSONWriter[A]] =
    reify(BSONWriter.from[A] { macroVal =>
      createHelper[A, Opts](implicitOptionsConfig).valueWriterBody.splice
    })

  def handler[
      A: c.WeakTypeTag,
      Opts: c.WeakTypeTag
    ]: c.Expr[BSONDocumentHandler[A]] =
    handlerWithConfig[A, Opts](implicitOptionsConfig)

  @SuppressWarnings(Array("PointlessTypeBounds"))
  def valueHandler[
      A <: AnyVal: c.WeakTypeTag,
      Opts: c.WeakTypeTag
    ]: c.Expr[BSONHandler[A]] = {
    val config = implicitOptionsConfig

    reify(new BSONHandler[A] {

      private val r: BSONValue => UTry[A] = { macroVal =>
        createHelper[A, Opts](config).valueReaderBody.splice
      }

      @inline def readTry(bson: BSONValue) = r(bson)

      private val w: A => UTry[BSONValue] = { macroVal =>
        createHelper[A, Opts](config).valueWriterBody.splice
      }

      @inline def writeTry(v: A) = w(v)
    })
  }

  def configuredHandler[
      A: c.WeakTypeTag,
      Opts: c.WeakTypeTag
    ]: c.Expr[BSONDocumentHandler[A]] =
    handlerWithConfig[A, Opts](withOptionsConfig)

  def documentClass[A: c.WeakTypeTag]: c.Expr[DocumentClass[A]] = {
    val aTpe = c.weakTypeOf[A].dealias
    val tpeSym = aTpe.typeSymbol.asClass
    val bsonValueTpe = c.typeOf[BSONValue]
    val bsonDocTpe = c.typeOf[BSONDocument]

    if (aTpe <:< bsonValueTpe) {
      if (aTpe <:< bsonDocTpe) {
        reify(DocumentClass.unchecked[A])
      } else {
        c.abort(
          c.enclosingPosition,
          s"Type ${tpeSym.fullName} is not a document one"
        )
      }
    } else if (tpeSym.isSealed && tpeSym.isAbstract) { // sealed trait
      reify(DocumentClass.unchecked[A])
    } else
      tpeSym.companion.typeSignature.decl(TermName("unapply")) match {
        case NoSymbol =>
          c.abort(
            c.enclosingPosition,
            s"Type ${tpeSym.fullName} is not a document one"
          )

        case _ =>
          reify(DocumentClass.unchecked[A])
      }
  }

  @com.github.ghik.silencer.silent("dead\\ code")
  def migrationRequired[A: c.WeakTypeTag](
      details: c.Expr[String]
    ): c.Expr[A] = {

    if (
      !sys.props.get("reactivemongo.api.migrationRequired.nonFatal").exists {
        v => v.toLowerCase == "true" || v.toLowerCase == "yes"
      }
    ) {
      val msg: String = details.tree match {
        case Literal(Constant(str: String)) =>
          s"Migration required: $str"

        case v => {
          c.warning(
            c.enclosingPosition,
            s"Invalid 'details' parameter for 'migrationRequired': ${show(v)}"
          )

          "Migration required"
        }
      }

      c.abort(c.enclosingPosition, msg)
    }

    c.Expr[A](q"scala.Predef.`???`")
  }

  // ---

  private def readerWithConfig[A: c.WeakTypeTag, Opts: c.WeakTypeTag](
      config: c.Expr[MacroConfiguration]
    ): c.Expr[BSONDocumentReader[A]] = reify(new BSONDocumentReader[A] {

    private val r: BSONDocument => UTry[A] = { macroDoc =>
      createHelper[A, Opts](config).readBody.splice
    }

    lazy val forwardBSONReader: BSONDocumentReader[A] =
      BSONDocumentReader.from[A](r)

    def readDocument(document: BSONDocument): UTry[A] =
      forwardBSONReader.readDocument(document)
  })

  private def writerWithConfig[A: c.WeakTypeTag, Opts: c.WeakTypeTag](
      config: c.Expr[MacroConfiguration]
    ): c.Expr[BSONDocumentWriter[A]] = reify(new BSONDocumentWriter[A] {

    private val w: A => UTry[BSONDocument] = { macroVal =>
      createHelper[A, Opts](config).writeBody.splice
    }

    lazy val forwardBSONWriter: BSONDocumentWriter[A] =
      BSONDocumentWriter.from[A](w)

    def writeTry(v: A) = forwardBSONWriter.writeTry(v)
  })

  private def handlerWithConfig[A: c.WeakTypeTag, Opts: c.WeakTypeTag](
      config: c.Expr[MacroConfiguration]
    ): c.Expr[BSONDocumentHandler[A]] = reify(new BSONDocumentHandler[A] {

    private val r: BSONDocument => UTry[A] = { macroDoc =>
      createHelper[A, Opts](config).readBody.splice
    }

    lazy val forwardBSONReader: BSONDocumentReader[A] =
      BSONDocumentReader.from[A](r)

    private val w: A => UTry[BSONDocument] = { macroVal =>
      createHelper[A, Opts](config).writeBody.splice
    }

    lazy val forwardBSONWriter: BSONDocumentWriter[A] =
      BSONDocumentWriter.from[A](w)

    def readDocument(document: BSONDocument): UTry[A] =
      forwardBSONReader.readDocument(document)

    def writeTry(v: A) = forwardBSONWriter.writeTry(v)
  })

  /* Resolves `config: MacroConfiguration` from the enclosing class. */
  def withOptionsConfig: c.Expr[MacroConfiguration] =
    c.Expr[MacroConfiguration](c.typecheck(q"${c.prefix}.config"))

  def implicitOptionsConfig: c.Expr[MacroConfiguration] =
    c.Expr[MacroConfiguration](
      c.inferImplicitValue(c.typeOf[MacroConfiguration])
    )

  private def createHelper[A: c.WeakTypeTag, Opts: c.WeakTypeTag](
      config: c.Expr[MacroConfiguration]
    ) =
    new Helper[c.type, A](config)
      with MacroTopHelpers
      with ReaderHelpers
      with WriterHelpers {
      val aTpe = c.weakTypeOf[A]
      val optsTpe = c.weakTypeOf[Opts]
      lazy val macroCfg = TermName(c.freshName("macroCfg"))
    }

  private abstract class Helper[C <: Context, A](
      val config: c.Expr[MacroConfiguration])
      extends ImplicitResolver {
    self: MacroHelpers with ReaderHelpers with WriterHelpers =>

    lazy val readBody: c.Expr[UTry[A]] = {
      val nme = TermName("macroDoc")
      val reader = readerTree(id = Ident(nme), top = true)
      val result = c.Expr[UTry[A]](reader)

      debug(s"// Reader\n${show(reader)}")

      result
    }

    lazy val valueReaderBody: c.Expr[UTry[A]] = {
      val nme = TermName("macroVal")
      val reader = valueReaderTree(id = Ident(nme))

      debug(s"// Value reader\n${show(reader)}")

      c.Expr[UTry[A]](reader)
    }

    lazy val writeBody: c.Expr[UTry[BSONDocument]] = {
      val writer = writerTree(valNme = TermName("macroVal"), top = true)
      val result = c.Expr[UTry[BSONDocument]](writer)

      debug(s"// Writer\n${show(writer)}")

      result
    }

    lazy val valueWriterBody: c.Expr[UTry[BSONValue]] = {
      val valNme = TermName("macroVal")

      val body = valueWriterTree(id = Ident(valNme))

      debug(s"// Value writer\n${show(body)}")

      c.Expr[UTry[BSONValue]](body)
    }
  }

  // ---

  sealed trait ReaderHelpers { self: MacroHelpers with ImplicitResolver =>
    // --- Reader types ---

    private val readerType: Type =
      c.typeOf[BSONReader[_]].typeConstructor

    private val docReaderType: Type =
      c.typeOf[BSONDocumentReader[_]].typeConstructor

    private val readerAnnotationTpe = c.typeOf[Reader[_]]

    // --- Reader builders ---

    protected final def readerTree(id: Ident, top: Boolean): Tree =
      unionTypes.map { types =>
        val resolve = resolver(Map.empty, "BSONReader", debug)(readerType)

        val preparedTypes = types.map { typ =>
          val cls = q"implicitly[$reflPkg.ClassTag[$typ]].runtimeClass"

          Tuple3(
            typ,
            TermName(c.freshName("Type")),
            q"$macroCfg.typeNaming($cls)"
          )
        }

        val subHelper = createSubHelper(aTpe)

        @SuppressWarnings(Array("ListAppend"))
        def cases: List[CaseDef] = (preparedTypes.map {
          case (typ, pattern, _) =>
            val body = subHelper.readBodyFromImplicit(typ)(resolve).getOrElse {

              if (hasOption[MacroOptions.AutomaticMaterialization]) {
                // No existing implicit, but can fallback to automatic mat
                readBodyConstruct(id, typ, top)
              } else {
                abort(s"Implicit not found for '${typ.typeSymbol.name}': ${classOf[BSONReader[_]].getName}[${typ.typeSymbol.fullName}]")
              }
            }

            cq"${Ident(pattern)} => $body"
        }) :+ {
          val ftn = TermName(c freshName "tpe")

          cq"""${ftn} => ${utilPkg}.Failure(
            ${exceptionsPkg}.HandlerException("Invalid type: " + ${ftn}))"""
        }

        val dt = TermName(c freshName "discriminator")
        val da = q"val $dt: _root_.java.lang.String"

        val typePats = preparedTypes.map {
          case (_, nme, pat) =>
            q"val ${nme} = ${pat}"
        }

        q"""{
          $macroCfgInit

          ..$typePats

          macroDoc.getAsTry[String](
            $macroCfg.discriminator).flatMap { ${da} =>
            ${Match(Ident(dt), cases)}
          }
        }"""
      } getOrElse readBodyConstruct(id, aTpe, top)

    // For member of a union
    private def readBodyFromImplicit(
        tpe: Type
      )(r: Type => Implicit
      ): Option[Tree] = {
      val (reader, _) = r(tpe)

      if (!reader.isEmpty) {
        Some(q"$reader.readTry(macroDoc)")
      } else None
    }

    /*
     * @param top $topParam
     */
    @inline private def readBodyConstruct(
        id: Ident,
        tpe: Type,
        top: Boolean
      ): Tree = {
      if (isSingleton(tpe)) readBodyConstructSingleton(tpe)
      else readBodyConstructClass(id, tpe, top)
    }

    private def readBodyConstructSingleton(tpe: Type): Tree = {
      val sym = tpe match {
        case SingleType(_, sym) => sym
        case TypeRef(_, sym, _) => sym
        case _                  =>
          abort(s"Something weird is going on with '$tpe'. Should be a singleton but can't parse it")
      }

      q"${utilPkg}.Success(${Ident(TermName(sym.name.toString))})"
    }

    private sealed trait ReadableProperty {
      def symbol: Symbol
      def name: String
      def term: TermName
      def tpe: Type
      def default: Option[Tree]
    }

    private case class RequiredReadableProperty(
        symbol: Symbol,
        name: String,
        term: TermName,
        tpe: Type,
        default: Option[Tree],
        reader: Tree)
        extends ReadableProperty

    private case class OptionalReadableProperty(
        symbol: Symbol,
        name: String,
        term: TermName,
        tpe: Type,
        default: Option[Tree])
        extends ReadableProperty

    /*
     * @param top $topParam
     */
    private def readBodyConstructClass(
        id: Ident,
        tpe: Type,
        top: Boolean
      ): Tree = {

      val (constructor, _) = matchingApplyUnapply(tpe).getOrElse(
        abort(s"No matching apply/unapply found: $tpe")
      )

      val boundTypes: Map[String, Type] = {
        val bt = Map.newBuilder[String, Type]

        val tpeArgs: List[c.Type] = tpe match {
          case TypeRef(_, _, args)        => args
          case i @ ClassInfoType(_, _, _) => i.typeArgs
          case _                          => List.empty[c.Type]
        }

        lazyZip(constructor.typeParams, tpeArgs).foreach {
          case (sym, ty) => bt += (sym.fullName -> ty)
        }

        bt.result()
      }

      val resolve = resolver(boundTypes, "BSONReader", debug)(readerType)
      val bufErr = TermName(c.freshName("err"))

      def resolveReader(
          pname: String,
          sig: Type
        ): Option[Tree] = {
        val reader = resolve(sig)._1

        if (reader.nonEmpty) {
          Some(reader)
        } else if (!hasOption[MacroOptions.AutomaticMaterialization]) {
          None
        } else {
          val lt = leafType(sig)

          warn(s"Materializing ${classOf[BSONReader[_]].getName}[${lt}] for '${tpe}.$pname': it's recommended to declare it explicitly")

          val subHelper = createSubHelper(lt)

          val nme = TermName(c.freshName("macroVal"))
          val ln = TermName(c.freshName("leafReader"))

          val lr: Tree = {
            if (lt <:< anyValTpe) {
              val ra = q"val ${nme}: ${bsonPkg}.BSONValue"

              q"""implicit def ${ln}: ${appliedType(readerType, lt)} =
                ${bsonPkg}.BSONReader.from[${lt}] { ${ra} =>
                  ${subHelper.valueReaderTree(id = Ident(nme))}
                }"""

            } else {
              val ra = q"val ${nme}: ${bsonPkg}.BSONDocument"

              q"""implicit def ${ln}: ${appliedType(readerType, lt)} =
                ${bsonPkg}.BSONDocumentReader.from[${lt}] { ${ra} =>
                  ${subHelper.readerTree(id = Ident(nme), top = false)}
                }"""
            }
          }

          Some(q"""{
            $lr

            implicitly[${appliedType(readerType, sig)}]
          }""")
        }
      }

      val companionObject = tpe.typeSymbol.companion
      val params: Seq[ReadableProperty] =
        constructor.paramLists.headOption.toSeq.flatten
          .map(_.asTerm)
          .zipWithIndex
          .map {
            case (param, index) =>
              val pname = paramName(param)
              val sig = param.typeSignature.map { st =>
                boundTypes.getOrElse(st.typeSymbol.fullName, st)
              }

              val readerAnnTpe = appliedType(readerAnnotationTpe, List(sig))
              val defaultFromAnn = Seq.newBuilder[Tree]
              val readerAnns = Seq.newBuilder[Tree]

              param.annotations.foreach {
                case ann if ann.tree.tpe <:< defaultValueAnnotationTpe => {
                  defaultFromAnn ++= ann.tree.children.tail.flatMap {
                    case v if v.tpe <:< sig =>
                      Seq(v)

                    case invalid =>
                      abort(s"Invalid annotation @DefaultValue($invalid) for '${tpe}.$pname': $sig value expected")
                  }
                }

                case ann if ann.tree.tpe <:< readerAnnotationTpe => {
                  if (!(ann.tree.tpe <:< readerAnnTpe)) {
                    abort(s"Invalid annotation @Reader(${show(ann.tree)}) for '${tpe}.$pname': Reader[${sig}]")
                  }

                  readerAnns ++= ann.tree.children.tail
                }

                case _ =>
              }

              val readerFromAnn: Option[Tree] = readerAnns.result() match {
                case r +: other => {
                  if (other.nonEmpty) {
                    warn(s"Exactly one @Reader must be provided for each property; Ignoring invalid annotations for '${tpe}.$pname'")
                  }

                  Some(r)
                }

                case _ =>
                  None
              }

              val default: Option[Tree] = {
                if (param.isParamWithDefault) {
                  val getter = TermName(f"apply$$default$$" + (index + 1))
                  Some(q"$companionObject.$getter")
                } else
                  defaultFromAnn.result() match {
                    case dv +: other => {
                      if (other.nonEmpty) {
                        warn("Exactly one @DefaultValue must be provided for each property; Ignoring invalid annotations")
                      }

                      Some(dv)
                    }

                    case _ =>
                      None
                  }
              }

              val dealiased: Type = dealias(sig)

              val reader: Option[Tree] = readerFromAnn.orElse {
                resolveReader(pname, dealiased)
              }

              reader match {
                case Some(rdr) =>
                  RequiredReadableProperty(
                    symbol = param,
                    name = pname,
                    term = TermName(c.freshName(pname)),
                    tpe = dealiased,
                    default = default,
                    reader = rdr
                  )

                case _ if ignoreField(param) =>
                  RequiredReadableProperty(
                    symbol = param,
                    name = pname,
                    term = TermName(c.freshName(pname)),
                    tpe = dealiased,
                    default = default,
                    reader = q"???"
                  )

                case _ if !isOptionalType(dealiased) =>
                  abort(s"No implicit found for '${tpe}.${pname}': ${classOf[BSONWriter[_]].getName}[${sig}]")

                case _ =>
                  OptionalReadableProperty(
                    symbol = param,
                    name = pname,
                    term = TermName(c.freshName(pname)),
                    tpe = dealiased,
                    default = default
                  )
              }
          }

      val decls = q"""val ${bufErr} =
        ${colPkg}.Seq.newBuilder[${exceptionsPkg}.HandlerException]""" +: (params.map {
        p => q"val ${p.term} = new ${bsonPkg}.Macros.LocalVar[${p.tpe}]"
      })

      val errName = TermName(c.freshName("cause"))

      val values = params.map {
        case p: ReadableProperty if ignoreField(p.symbol) =>
          p.default match {
            case Some(default) =>
              q"${p.term}.take($default); ()"

            case _ =>
              abort(s"Cannot ignore '${tpe}.${paramName(p.symbol)}': not default value (see @DefaultValue)")
          }

        case RequiredReadableProperty(
              param,
              pname,
              vt,
              sig,
              default,
              reader
            ) => {

          val rt = TermName(c.freshName("read"))

          def tryWithDefault(`try`: Tree, dv: Tree) = q"""${`try`} match {
            case ${utilPkg}.Failure(
              _: ${exceptionsPkg}.BSONValueNotFoundException) =>
              ${utilPkg}.Success(${dv})

            case result =>
              result
          }"""

          val get: Tree = {
            if (param.annotations.exists(_.tree.tpe =:= typeOf[Flatten])) {
              if (reader.toString == "forwardBSONReader") {
                abort(
                  s"Cannot flatten reader for '${tpe}.$pname': recursive type"
                )
              }

              if (!(reader.tpe <:< appliedType(docReaderType, List(sig)))) {
                abort(s"Cannot flatten reader '$reader' for '${tpe}.$pname': doesn't conform BSONDocumentReader")
              }

              val readTry = q"${reader}.readTry(${id})"

              default.fold(readTry) { dv => tryWithDefault(readTry, dv) }
            } else {
              val field = q"$macroCfg.fieldNaming($pname)"

              val getAsTry: Tree = {
                if (isOptionalType(sig)) {
                  q"${id}.getRawAsTry($field)($reader)"
                } else {
                  q"${id}.getAsTry($field)($reader)"
                }
              }

              default.fold(getAsTry) { dv => tryWithDefault(getAsTry, dv) }
            }
          }

          q"""${get} match {
              case ${utilPkg}.Success($rt) => ${vt}.take($rt); ()

              case ${utilPkg}.Failure($errName) => 
                ${bufErr} += ${exceptionsPkg}.HandlerException($pname, $errName)
            }"""
        }

        case OptionalReadableProperty(
              param,
              pname,
              vt,
              sig @ OptionTypeParameter(ot),
              default
            ) => {
          val rt = TermName(c.freshName("read"))
          val reader: Tree = resolveReader(pname, ot) match {
            case Some(rdr) => rdr

            case _ =>
              abort(s"No implicit found for '${tpe}.${pname}': ${classOf[BSONWriter[_]].getName}[Option[${ot}]]")
          }

          def tryWithDefault(`try`: Tree, dv: Tree) = q"""${`try`} match {
            case ${utilPkg}.Failure(
              _: ${exceptionsPkg}.BSONValueNotFoundException) =>
              ${utilPkg}.Success(${dv})

            case result =>
              result
          }"""

          val get: Tree = {
            if (param.annotations.exists(_.tree.tpe =:= typeOf[Flatten])) {
              if (reader.toString == "forwardBSONReader") {
                abort(
                  s"Cannot flatten reader for '${tpe}.$pname': recursive type"
                )
              }

              if (!(reader.tpe <:< appliedType(docReaderType, List(sig)))) {
                abort(s"Cannot flatten reader '$reader' for '${tpe}.$pname': doesn't conform BSONDocumentReader")
              }

              val readTry = q"${reader}.readTry(${id})"

              default.fold(readTry) { dv => tryWithDefault(readTry, dv) }
            } else {
              val field = q"$macroCfg.fieldNaming($pname)"
              val getAsUnflattenedTry =
                q"${id}.getAsUnflattenedTry($field)($reader)"

              default match {
                case Some(dv) =>
                  q"${getAsUnflattenedTry}.map(_.orElse(${dv}))"

                case _ =>
                  getAsUnflattenedTry
              }
            }
          }

          q"""${get} match {
              case ${utilPkg}.Success($rt) => ${vt}.take($rt); ()

              case ${utilPkg}.Failure($errName) => 
                ${bufErr} += ${exceptionsPkg}.HandlerException($pname, $errName)
            }"""
        }

        case p =>
          abort(s"Unexpected field '${tpe}.${paramName(p.symbol)}'")
      }

      val applyArgs = params.map {
        case p: ReadableProperty => q"${p.term}.value()"
      }

      val accName = TermName(c.freshName("acc"))

      val rcfg = if (top && params.nonEmpty) macroCfgInit else EmptyTree

      q"""{
        $rcfg

        ..$decls 
        ..$values

        val ${accName} = ${bufErr}.result()

        ${accName}.headOption match {
          case _root_.scala.Some(${errName}) => 
            ${utilPkg}.Failure(${errName} suppress ${accName}.tail
              ): ${utilPkg}.Try[${tpe}]

          case _ => 
            ${utilPkg}.Success(${Ident(companion(tpe).name)}.
              apply(..${applyArgs})): ${utilPkg}.Try[${tpe}]
        }
      }"""
    }

    /* Returns the tree for value reader. */
    protected final def valueReaderTree(id: Ident): Tree = {
      val ctor = aTpe.decl(c.universe.termNames.CONSTRUCTOR).asMethod

      ctor.paramLists match {
        case List(v: TermSymbol) :: Nil => {
          val typ = v.info
          val resolve = resolver(Map.empty, "BSONReader", debug)(readerType)

          resolve(typ)._1 match {
            case EmptyTree =>
              abort(s"Implicit not found for '${typ.typeSymbol.name}': ${classOf[BSONReader[_]].getName}[${typ.typeSymbol.fullName}]")

            case reader =>
              q"${reader}.readTry($id).map { new ${aTpe}(_) }"
          }
        }

        case _ =>
          abort(s"Cannot resolve value reader for '${aTpe.typeSymbol.name}'")
      }
    }

    private def createSubHelper(tpe: Type) =
      new MacroHelpers with ReaderHelpers with ImplicitResolver {
        val aTpe = tpe
        val optsTpe = self.optsTpe
        val macroCfg = self.macroCfg
      }
  }

  sealed trait WriterHelpers { self: MacroHelpers with ImplicitResolver =>
    // --- Writer types ---

    private val writerType: Type = c.typeOf[BSONWriter[_]].typeConstructor

    private val docWriterType: Type =
      typeOf[BSONDocumentWriter[_]].typeConstructor

    private val writerAnnotationTpe: Type = c.typeOf[Writer[_]]

    // --- Writer builders ---

    protected final def writerTree(valNme: TermName, top: Boolean): Tree =
      unionTypes.map { types =>
        val resolve =
          resolver(Map.empty, "BSONDocumentWriter", debug)(writerType)

        val subHelper = createSubHelper(aTpe)
        val cases = types.map { typ =>
          val nme = TermName(c.freshName("macroVal"))
          val id = Ident(nme)
          val body = writeBodyFromImplicit(id, typ)(resolve).getOrElse {
            if (hasOption[MacroOptions.AutomaticMaterialization]) {
              // No existing implicit, but can fallback to automatic mat
              subHelper.writeBodyConstruct(id, typ, top)
            } else {
              abort(s"Implicit not found for '${typ.typeSymbol.name}': ${classOf[BSONWriter[_]].getName}[${typ.typeSymbol.fullName}]")
            }
          }

          cq"$nme: $typ => $body"
        }

        @SuppressWarnings(Array("ListAppend"))
        def matchBody = cases :+ cq"""
          _ => ${utilPkg}.Failure(
            ${bsonPkg}.exceptions.ValueDoesNotMatchException(
              ${valNme}.toString))
        """

        q"{ $macroCfgInit; ${Match(Ident(valNme), matchBody)} }"
      } getOrElse writeBodyConstruct(Ident(valNme), aTpe, top)

    private def writeBodyFromImplicit(
        id: Ident,
        tpe: Type
      )(r: Type => Implicit
      ): Option[Tree] = {
      val (writer, _) = r(tpe)

      if (!writer.isEmpty) {
        @inline def doc = q"$writer.writeTry($id)"

        Some(classNameTree(tpe).fold(doc) { de => q"${doc}.map { _ ++ $de }" })
      } else None
    }

    def valueWriterTree(id: Ident): Tree = {
      val ctor = aTpe.decl(c.universe.termNames.CONSTRUCTOR).asMethod

      ctor.paramLists match {
        case List(v: TermSymbol) :: Nil => {
          val typ = v.info
          val resolve = resolver(Map.empty, "BSONWriter", debug)(writerType)

          resolve(typ)._1 match {
            case EmptyTree =>
              abort(s"Implicit not found for '${typ.typeSymbol.name}': ${classOf[BSONWriter[_]].getName}[${typ.typeSymbol.fullName}]")

            case writer =>
              q"${writer}.writeTry(${id}.${v.name})"
          }
        }

        case _ =>
          abort(s"Cannot resolve value writer for '${aTpe.typeSymbol.name}'")

      }
    }

    /*
     * @param top $topParam
     */
    @inline private def writeBodyConstruct(
        id: Ident,
        tpe: Type,
        top: Boolean
      ): Tree = {
      if (isSingleton(tpe)) writeBodyConstructSingleton(tpe)
      else writeBodyConstructClass(id, tpe, top)
    }

    private def writeBodyConstructSingleton(tpe: Type): Tree =
      classNameTree(tpe).map { discriminator =>
        q"${utilPkg}.Success(${bsonPkg}.BSONDocument(${discriminator}))"
      } getOrElse q"${utilPkg}.Success(${bsonPkg}.BSONDocument.empty)"

    private sealed trait WritableProperty {
      def symbol: Symbol
      def index: Int
      def tpe: Type
    }

    private case class RequiredWritableProperty(
        symbol: Symbol,
        index: Int,
        tpe: Type,
        writer: Tree)
        extends WritableProperty

    private case class OptionalWritableProperty(
        symbol: Symbol,
        index: Int,
        tpe: Type)
        extends WritableProperty

    private def writeBodyConstructClass(
        id: Ident,
        tpe: Type,
        top: Boolean
      ): Tree = {
      val (constructor, deconstructor) = matchingApplyUnapply(tpe).getOrElse(
        abort(s"No matching apply/unapply found: $tpe")
      )

      val types = unapplyReturnTypes(deconstructor)
      val constructorParams: List[Symbol] =
        constructor.paramLists.headOption.getOrElse(List.empty[Symbol])

      val boundTypes: Map[String, Type] = {
        val bt = Map.newBuilder[String, Type]

        val tpeArgs: List[c.Type] = tpe match {
          case TypeRef(_, _, args)        => args
          case i @ ClassInfoType(_, _, _) => i.typeArgs
          case _                          => List.empty[c.Type]
        }

        lazyZip(constructor.typeParams, tpeArgs).map {
          case (sym, ty) => bt += (sym.fullName -> ty)
        }

        bt.result()
      }

      val resolve = resolver(boundTypes, "BSONWriter", debug)(writerType)
      val tupleName = TermName(c.freshName("tuple"))

      def resolveWriter(pname: String, wtpe: Type): Option[Tree] = {
        val (writer, _) = resolve(wtpe)

        if (writer.nonEmpty) {
          Some(writer)
        } else if (!hasOption[MacroOptions.AutomaticMaterialization]) {
          None
        } else {
          val lt = leafType(wtpe)

          warn(s"Materializing ${classOf[BSONWriter[_]].getName}[${lt}] for '${tpe}.$pname': it's recommended to declare it explicitly")

          val subHelper = createSubHelper(lt)

          val nme = TermName(c.freshName("leafVal"))
          val lw: Tree = {
            if (lt <:< anyValTpe) {
              subHelper.valueWriterTree(id = Ident(nme))
            } else {
              subHelper.writerTree(valNme = nme, top = false)
            }
          }

          val ln = TermName(c.freshName("leafWriter"))
          val la = q"val ${nme}: ${lt}"

          Some(q"""{
            implicit def ${ln}: ${appliedType(writerType, lt)} =
              ${bsonPkg}.BSONWriter.from[${lt}] { ${la} => $lw }

            implicitly[${appliedType(writerType, wtpe)}]
          }""")
        }
      }

      val (optional, required) =
        lazyZip(constructorParams.zipWithIndex, types).collect {
          case ((sym, i), o @ OptionTypeParameter(st)) if !ignoreField(sym) => {
            val sig = boundTypes.get(st.typeSymbol.fullName).fold(o) { t =>
              o.substituteTypes(List(st.typeSymbol), List(t))
            }

            Tuple3(sym, i, dealias(sig))
          }

          case ((sym, i), sig) if !ignoreField(sym) =>
            Tuple3(sym, i, dealias(sig))

        }.map {
          case (sym, i, sig) =>
            val writerAnnTpe = appliedType(writerAnnotationTpe, List(sig))
            val pname = paramName(sym)

            val writerAnns = sym.annotations.flatMap {
              case ann if ann.tree.tpe <:< writerAnnotationTpe => {
                if (!(ann.tree.tpe <:< writerAnnTpe)) {
                  abort(s"Invalid annotation @Writer(${show(ann.tree)}) for '${tpe}.${pname}': Writer[${sig}]")
                }

                ann.tree.children.tail
              }

              case _ =>
                Seq.empty[Tree]
            }

            val writerFromAnn: Option[Tree] = writerAnns match {
              case w +: other => {
                if (other.nonEmpty) {
                  warn(s"Exactly one @Writer must be provided for each property; Ignoring invalid annotations for '${tpe}.${paramName(sym)}'")
                }

                Some(w)
              }

              case _ =>
                None
            }

            val writer: Option[Tree] = writerFromAnn.orElse {
              resolveWriter(pname, sig)
            }

            writer match {
              case Some(wrt) =>
                RequiredWritableProperty(sym, i, sig, wrt)

              case _ if !isOptionalType(sig) =>
                abort(s"Implicit not found for '${tpe}.$pname': ${classOf[BSONWriter[_]].getName}[${sig.typeSymbol.fullName}]")

              case _ =>
                OptionalWritableProperty(sym, i, sig)
            }
        }.partition {
          case _: OptionalWritableProperty => true
          case _                           => false
        }

      val tupleElement: Int => Tree = {
        val tuple = Ident(tupleName)
        if (types.length == 1) { (_: Int) => tuple }
        else { (i: Int) => Select(tuple, TermName("_" + (i + 1))) }
      }

      val bufOk = TermName(c.freshName("ok"))
      val bufErr = TermName(c.freshName("err"))

      def mustFlatten(
          param: Symbol,
          pname: String,
          sig: Type,
          writer: Tree
        ): Boolean = {
        if (param.annotations.exists(_.tree.tpe =:= typeOf[Flatten])) {
          if (writer.toString == "forwardBSONWriter") {
            abort(s"Cannot flatten writer for '${tpe}.$pname': recursive type")
          }

          if (!(writer.tpe <:< appliedType(docWriterType, List(sig)))) {
            abort(s"Cannot flatten writer '$writer' for '${tpe}.$pname': doesn't conform BSONDocumentWriter")
          }

          true
        } else false
      }

      val errName = TermName(c.freshName("cause"))

      val values = required.map {
        case OptionalWritableProperty(param, _, _) =>
          abort(s"Unexpected optional field '${tpe}.${paramName(param)}'")

        case p: RequiredWritableProperty => p
      }.map {
        case RequiredWritableProperty(param, i, sig, writer) =>
          val pname = paramName(param)

          val writeCall =
            q"$writer.writeTry(${tupleElement(i)}): ${utilPkg}.Try[${bsonPkg}.BSONValue]"

          val vt = TermName(c.freshName(pname))

          val field = q"$macroCfg.fieldNaming(${pname})"
          val appendCall = q"${bufOk} += ${bsonPkg}.BSONElement($field, $vt)"
          val appendDocCall = if (mustFlatten(param, pname, sig, writer)) {
            q"${bufOk} ++= $vt.elements"
          } else {
            appendCall
          }

          q"""${writeCall} match {
            case ${utilPkg}.Success(
              $vt: ${bsonPkg}.BSONDocument) => $appendDocCall; ()

            case ${utilPkg}.Success($vt) => $appendCall; ()

            case ${utilPkg}.Failure($errName) => 
              ${bufErr} += ${exceptionsPkg}.HandlerException($pname, $errName)
              ()
          }"""
      }

      val extra = optional.map {
        case RequiredWritableProperty(param, _, _, _) =>
          abort(s"Unexpected non-optional field '${tpe}.${paramName(param)}'")

        case p: OptionalWritableProperty => p
      }.map {
        case OptionalWritableProperty(
              param,
              i,
              OptionTypeParameter(sig)
            ) =>
          val pname = paramName(param)
          val field = q"$macroCfg.fieldNaming($pname)"

          val bt = TermName(c.freshName("bson"))
          val vt = TermName(c.freshName(pname))

          def writeCall(wr: Tree) =
            q"""($wr.writeTry($vt): ${utilPkg}.Try[${bsonPkg}.BSONValue]) match {
            case ${utilPkg}.Success(${bt}) =>
              ${bufOk} += ${bsonPkg}.BSONElement($field, $bt)
              ()

            case ${utilPkg}.Failure(${errName}) =>
              ${bufErr} += ${exceptionsPkg}.HandlerException($pname, $errName)
              ()
          }"""

          def empty =
            q"${bufOk} += ${bsonPkg}.BSONElement($field, ${bsonPkg}.BSONNull)"

          val vp = ValDef(
            Modifiers(Flag.PARAM),
            vt,
            TypeTree(sig),
            EmptyTree
          ) // ${vt} =>

          def implicitWriter = resolveWriter(pname, sig) match {
            case Some(wrt) =>
              wrt

            case _ =>
              abort(s"Implicit not found for '${tpe}.$pname': ${classOf[BSONWriter[_]].getName}[Option[${sig.typeSymbol.fullName}]]")
          }

          val call = writeCall(implicitWriter)

          if (param.annotations.exists(_.tree.tpe =:= typeOf[NoneAsNull])) {
            q"${tupleElement(i)}.fold({ ${empty}; () }) { ${vp} => $call }"
          } else {
            q"${tupleElement(i)}.foreach { ${vp} => $call }"
          }

        case OptionalWritableProperty(param, _, _) =>
          abort(s"Invalid optional field '${tpe}.${paramName(param)}'")
      }

      // List[Tree] corresponding to fields appended to the buffer/builder
      def fields = values ++ extra ++ classNameTree(tpe).map { cn =>
        q"$bufOk += $cn"
      }

      val accName = TermName(c.freshName("acc"))

      val wcfg = {
        if (top && constructorParams.nonEmpty) macroCfgInit
        else EmptyTree
      }

      def writer =
        q"""$wcfg

        val ${bufOk} = ${colPkg}.Seq.newBuilder[${bsonPkg}.BSONElement]
        val ${bufErr} = 
          ${colPkg}.Seq.newBuilder[${exceptionsPkg}.HandlerException]

        ..${fields.toSeq}

        val ${accName} = ${bufErr}.result()

        ${accName}.headOption match {
          case _root_.scala.Some(${errName}) => 
            ${utilPkg}.Failure(${errName} suppress ${accName}.tail
              ): ${utilPkg}.Try[${bsonPkg}.BSONDocument]

          case _ => ${utilPkg}.Success(
            ${bsonPkg}.BSONDocument(${bufOk}.result(): _*)
          ): ${utilPkg}.Try[${bsonPkg}.BSONDocument]
        }
        """

      if (values.isEmpty && extra.isEmpty) {
        q"{..$writer}"
      } else {
        // Extract/unapply the class instance as ${tupleDef}
        q"""${Ident(companion(tpe).name)}.unapply($id) match {
          case _root_.scala.Some(${tupleName}) => $writer

          case _ => ${utilPkg}.Failure(
            ${bsonPkg}.exceptions.HandlerException(
              "Fails to unapply: " + $id))
        }"""
      }
    }

    private def createSubHelper(tpe: Type) =
      new MacroHelpers with WriterHelpers with ImplicitResolver {
        val aTpe = tpe
        val optsTpe = self.optsTpe
        val macroCfg = self.macroCfg
      }

    // --- Type helpers ---

    private def classNameTree(tpe: c.Type): Option[Tree] = {
      val tpeSym = aTpe.typeSymbol.asClass

      if (
        hasOption[
          MacroOptions.UnionType[_]
        ] || tpeSym.isSealed && tpeSym.isAbstract
      ) Some {
        val cls = q"implicitly[$reflPkg.ClassTag[$tpe]].runtimeClass"

        q"""${bsonPkg}.BSONElement(
          $macroCfg.discriminator, 
          ${bsonPkg}.BSONString($macroCfg.typeNaming($cls)))"""

      }
      else None
    }
  }

  sealed trait MacroHelpers { _i: ImplicitResolver =>
    /* Type of compile-time options; See [[MacroOptions]] */
    protected def optsTpe: Type

    protected def aTpe: Type

    // --- Shared trees and types

    private val scalaPkg = q"_root_.scala"
    protected final val colPkg = q"${scalaPkg}.collection.immutable"
    protected final val utilPkg = q"${scalaPkg}.util"
    protected final val reflPkg = q"${scalaPkg}.reflect"

    protected final val bsonPkg = q"_root_.reactivemongo.api.bson"

    protected final val exceptionsPkg = q"${bsonPkg}.exceptions"

    protected final val optionTpe: Type = c.typeOf[Option[_]]

    protected final val anyValTpe: Type = c.typeOf[AnyVal]

    protected final val defaultValueAnnotationTpe = c.typeOf[DefaultValue[_]]

    // --- Macro configuration helpers ---

    // Init MacroConfiguration (possibility lazy) in a local val,
    // to avoid evaluating the configuration each time required
    // in the generated handler.
    protected def macroCfg: TermName

    @inline protected def macroCfgInit: Tree = EmptyTree

    // --- Case classes helpers ---

    protected final def paramName(param: c.Symbol): String = {
      param.annotations.collect {
        case ann if ann.tree.tpe =:= typeOf[Key] =>
          ann.tree.children.tail.collect {
            case l: Literal =>
              l.value.value

            case _ =>
              abort(
                "Annotation @Key must be provided with a pure/literal value"
              )

          }.collect { case value: String => value }
      }.flatten.headOption getOrElse param.name.toString
    }

    protected final def ignoreField(param: Symbol): Boolean =
      param.annotations.exists(ann =>
        ann.tree.tpe =:= typeOf[Ignore] || ann.tree.tpe =:= typeOf[transient]
      )

    // --- Union helpers ---

    protected final lazy val unionTypes: Option[List[c.Type]] =
      parseUnionTypes orElse directKnownSubclasses

    protected def parseUnionTypes = Option.empty[List[c.Type]]

    private def directKnownSubclasses: Option[List[Type]] = {
      // Workaround for SI-7046: https://issues.scala-lang.org/browse/SI-7046
      val tpeSym = aTpe.typeSymbol.asClass

      @annotation.tailrec
      def allSubclasses(
          path: Iterable[Symbol],
          subclasses: Set[Type]
        ): Set[Type] = path.headOption match {
        case Some(cls: ClassSymbol)
            if (tpeSym != cls && !cls.isAbstract &&
              cls.selfType.baseClasses.contains(tpeSym)) => {
          val newSub: Set[Type] = if ({
            val tpe = cls.typeSignature
            !applyMethod(tpe).isDefined || !unapplyMethod(tpe).isDefined
          }) {
            warn(s"Cannot handle class ${cls.fullName}: no case accessor")
            Set.empty
          } else if (cls.typeParams.nonEmpty) {
            abort(s"Generic type ${cls.fullName} is not supported as sub-type of ${aTpe.typeSymbol.name}")
          } else Set(cls.selfType)

          allSubclasses(path.tail, subclasses ++ newSub)
        }

        case Some(o: ModuleSymbol)
            if (o.companion == NoSymbol && // not a companion object
              o.typeSignature.baseClasses.contains(tpeSym)) =>
          allSubclasses(path.tail, subclasses + o.typeSignature)

        case Some(o: ModuleSymbol)
            if (
              o.companion == NoSymbol // not a companion object
            ) =>
          allSubclasses(path.tail, subclasses)

        case Some(_) => allSubclasses(path.tail, subclasses)

        case _ => subclasses
      }

      if (tpeSym.isSealed && tpeSym.isAbstract) {
        Some(allSubclasses(tpeSym.owner.typeSignature.decls, Set.empty).toList)
      } else None
    }

    // --- Type helpers ---

    @inline protected final def isOptionalType(
        implicit
        A: c.Type
      ): Boolean =
      optionTpe.typeConstructor == A.typeConstructor

    @annotation.tailrec
    protected final def leafType(t: Type): Type =
      t.typeArgs.headOption match {
        case Some(arg) => leafType(arg)
        case _         => t
      }

    /* Some(A) for Option[A] else None */
    protected object OptionTypeParameter {

      def unapply(tpe: c.Type): Option[c.Type] = {
        if (isOptionalType(tpe)) {
          tpe match {
            case TypeRef(_, _, args) => args.headOption
            case _                   => None
          }
        } else None
      }
    }

    @inline protected def isSingleton(tpe: Type): Boolean =
      tpe <:< typeOf[Singleton]

    @inline protected def companion(tpe: Type): Symbol =
      tpe.typeSymbol.companion

    private def applyMethod(
        implicit
        tpe: Type
      ): Option[Symbol] =
      companion(tpe).typeSignature.decl(TermName("apply")) match {
        case NoSymbol => {
          debug(s"No apply function found for $tpe")

          None
        }

        case s => Some(s)
      }

    private def unapplyMethod(
        implicit
        tpe: Type
      ): Option[MethodSymbol] =
      companion(tpe).typeSignature.decl(TermName("unapply")) match {
        case NoSymbol => {
          debug(s"No unapply function found for $tpe")

          None
        }

        case s => {
          val alt = s.asTerm.alternatives

          if (alt.tail.nonEmpty) {
            warn(
              s"""Multiple 'unapply' declared on '${tpe.typeSymbol.fullName}': ${alt
                  .map(_.info)
                  .mkString("\n- ", "\n- ", "\n\n")}"""
            )
          }

          alt.headOption.map(_.asMethod)
        }
      }

    protected final def unapplyReturnTypes(
        deconstructor: MethodSymbol
      ): List[c.Type] = {
      val opt = deconstructor.returnType match {
        case TypeRef(_, _, Nil) => Some(Nil)

        case TypeRef(_, _, (t @ TypeRef(_, _, Nil)) :: _) =>
          Some(List(t))

        case TypeRef(_, _, (typ @ TypeRef(_, t, args)) :: _) =>
          Some(if (t.name.toString.matches("Tuple\\d\\d?")) args else List(typ))

        case _ => None
      }

      opt getOrElse abort("something wrong with unapply type")
    }

    /**
     * @return (apply symbol, unapply symbol)
     */
    @SuppressWarnings(Array("ListSize"))
    protected final def matchingApplyUnapply(
        implicit
        tpe: Type
      ): Option[(MethodSymbol, MethodSymbol)] = for {
      applySymbol <- applyMethod(tpe)
      unapply <- unapplyMethod(tpe)
      alternatives = applySymbol.asTerm.alternatives.map(_.asMethod)
      u = unapplyReturnTypes(unapply)

      apply <- alternatives.find { alt =>
        alt.paramLists match {
          case params :: ps
              if (ps.isEmpty || ps.headOption
                .flatMap(_.headOption)
                .exists(_.isImplicit)) =>
            if (params.size != u.size) false
            else {

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
        case Some(
              (TypeRef(NoPrefix, a, _), TypeRef(NoPrefix, b, _))
            ) => { // for generic parameter
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
          if (a =:= b) deepConforms(types.tail)
          else {
            warn(s"Types are not compatible: $a != $b")

            false
          }

        case Some((a, b))
            if (a.baseClasses
              .map(_.fullName) != b.baseClasses.map(_.fullName)) => {
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
      if (hasOption[MacroOptions.DisableWarnings]) { (_: String) => () }
      else { (msg: String) => c.warning(c.enclosingPosition, msg) }
    }

    /* Prints debug entry, if allowed. */
    protected final lazy val debug: String => Unit = {
      if (!hasOption[MacroOptions.Verbose]) { (_: String) => () }
      else { (msg: String) => c.echo(c.enclosingPosition, msg) }
    }

    @inline protected final def abort(msg: String) =
      c.abort(c.enclosingPosition, msg)
  }

  sealed trait MacroTopHelpers extends MacroHelpers { _i: ImplicitResolver =>
    protected def config: c.Expr[MacroConfiguration]

    protected final override lazy val macroCfgInit: Tree =
      q"val $macroCfg: ${bsonPkg}.MacroConfiguration = ${config}"

    protected override def parseUnionTypes: Option[List[c.Type]] = {
      val unionOption = c.typeOf[MacroOptions.UnionType[_]]
      val union = c.typeOf[MacroOptions.\/[_, _]]

      @annotation.tailrec
      def parseUnionTree(trees: List[Type], found: List[Type]): List[Type] =
        trees match {
          case tr :: rem => {
            if (tr <:< union) {
              tr match {
                case TypeRef(_, _, List(a, b)) =>
                  parseUnionTree(a :: b :: rem, found)

                case _ =>
                  c.abort(
                    c.enclosingPosition,
                    s"Union type parameters expected: $tr"
                  )
              }
            } else parseUnionTree(rem, tr :: found)
          }

          case _ => found
        }

      val tree = optsTpe.dealias match {
        case t @ TypeRef(_, _, lst) if t <:< unionOption =>
          lst.headOption

        case RefinedType(types, _) =>
          types
            .filter(_ <:< unionOption)
            .flatMap {
              case TypeRef(_, _, args) => args
              case _                   => List.empty
            }
            .headOption

        case _ => None
      }

      tree.map { t => parseUnionTree(List(t), Nil) }
    }
  }

  sealed trait ImplicitResolver {
    protected def aTpe: Type

    import Macros.Placeholder

    // The placeholder type
    private val PlaceholderType: Type = typeOf[Placeholder]

    /* Refactor the input types, by replacing any type matching the `filter`,
     * by the given `replacement`.
     */
    @annotation.tailrec
    private def refactor(
        boundTypes: Map[String, Type]
      )(in: List[Type],
        base: TypeSymbol,
        out: List[Type],
        tail: List[(List[Type], TypeSymbol, List[Type])],
        filter: Type => Boolean,
        replacement: Type,
        altered: Boolean
      ): (Type, Boolean) = in match {
      case tpe :: ts =>
        boundTypes.getOrElse(tpe.typeSymbol.fullName, tpe) match {
          case t if (filter(t)) =>
            refactor(boundTypes)(
              ts,
              base,
              (replacement :: out),
              tail,
              filter,
              replacement,
              true
            )

          case TypeRef(_, sym, as) if as.nonEmpty =>
            refactor(boundTypes)(
              as,
              sym.asType,
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

      case _ => {
        val tpe = appliedType(base, out.reverse)

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
        boundTypes: Map[String, Type]
      )(tpe: Type
      ): (Type, Boolean) =
      boundTypes.getOrElse(tpe.typeSymbol.fullName, tpe) match {
        case t if (t =:= aTpe) => PlaceholderType -> true

        case TypeRef(_, sym, args) if args.nonEmpty =>
          refactor(boundTypes)(
            args,
            sym.asType,
            List.empty,
            List.empty,
            _ =:= aTpe,
            PlaceholderType,
            false
          )

        case t => t -> false
      }

    /* Restores reference to the type itself when Placeholder is found. */
    private def denormalized(boundTypes: Map[String, Type])(ptype: Type): Type =
      ptype match {
        case PlaceholderType => aTpe

        case TypeRef(_, sym, args) if args.nonEmpty =>
          refactor(boundTypes)(
            args,
            sym.asType,
            List.empty,
            List.empty,
            _ == PlaceholderType,
            aTpe,
            false
          )._1

        case _ => ptype
      }

    private class ImplicitTransformer(
        boundTypes: Map[String, Type],
        forwardSuffix: String)
        extends Transformer {
      private val denorm = denormalized(boundTypes) _
      val forwardName = TermName(s"forward$forwardSuffix")

      override def transform(tree: Tree): Tree = tree match {
        case tt: TypeTree =>
          super.transform(TypeTree(denorm(tt.tpe)))

        case Select(Select(This(TypeName("Macros")), t), sym)
            if (t.toString == "Placeholder" && sym.toString == "Handler") =>
          super.transform(q"$forwardName")

        case _ => super.transform(tree)
      }
    }

    private def createImplicit(
        debug: String => Unit,
        boundTypes: Map[String, Type]
      )(tc: Type,
        ptype: Type,
        tx: Transformer
      ): Implicit = {
      val tpe = ptype
      val (ntpe, selfRef) = normalized(boundTypes)(tpe)
      val ptpe = boundTypes.getOrElse(ntpe.typeSymbol.fullName, ntpe)

      // infers implicit
      val neededImplicitType = appliedType(tc.typeConstructor, ptpe)
      val neededImplicit = if (!selfRef) {
        c.inferImplicitValue(neededImplicitType)
      } else
        c.untypecheck(
          // Reset the type attributes on the refactored tree for the implicit
          tx.transform(c.inferImplicitValue(neededImplicitType))
        )

      debug(s"// Resolve implicit ${tc} for ${ntpe} as ${neededImplicitType} (self? ${selfRef}) = ${neededImplicit}")

      neededImplicit -> selfRef
    }

    @inline def dealias(t: Type): Type =
      if (t.typeSymbol.name.toString == "<refinement>") t
      else t.dealias

    protected def resolver(
        boundTypes: Map[String, Type],
        forwardSuffix: String,
        debug: String => Unit
      )(tc: Type
      ): Type => Implicit = {
      val tx = new ImplicitTransformer(boundTypes, forwardSuffix)
      createImplicit(debug, boundTypes)(tc, _: Type, tx)
    }

    type Implicit = (Tree, Boolean)
  }
}
