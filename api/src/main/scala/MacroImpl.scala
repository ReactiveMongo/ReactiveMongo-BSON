package reactivemongo.api.bson

import scala.util.{ Try => UTry }

import scala.collection.immutable.Set

import scala.reflect.macros.blackbox.Context

private[bson] class MacroImpl(val c: Context) {
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

  def reader[A: c.WeakTypeTag, Opts: c.WeakTypeTag]: c.Expr[BSONDocumentReader[A]] = readerWithConfig[A, Opts](implicitOptionsConfig)

  def configuredReader[A: c.WeakTypeTag, Opts: c.WeakTypeTag]: c.Expr[BSONDocumentReader[A]] = readerWithConfig[A, Opts](withOptionsConfig)

  @SuppressWarnings(Array("PointlessTypeBounds"))
  def valueReader[A <: AnyVal: c.WeakTypeTag, Opts: c.WeakTypeTag]: c.Expr[BSONReader[A]] = reify(BSONReader.from[A] { macroVal =>
    createHelper[A, Opts](implicitOptionsConfig).valueReaderBody.splice
  })

  def writer[A: c.WeakTypeTag, Opts: c.WeakTypeTag]: c.Expr[BSONDocumentWriter[A]] = writerWithConfig[A, Opts](implicitOptionsConfig)

  def configuredWriter[A: c.WeakTypeTag, Opts: c.WeakTypeTag]: c.Expr[BSONDocumentWriter[A]] = writerWithConfig[A, Opts](withOptionsConfig)

  @SuppressWarnings(Array("PointlessTypeBounds"))
  def valueWriter[A <: AnyVal: c.WeakTypeTag, Opts: c.WeakTypeTag]: c.Expr[BSONWriter[A]] = reify(BSONWriter.from[A] { macroVal =>
    createHelper[A, Opts](implicitOptionsConfig).valueWriterBody.splice
  })

  def handler[A: c.WeakTypeTag, Opts: c.WeakTypeTag]: c.Expr[BSONDocumentHandler[A]] = handlerWithConfig[A, Opts](implicitOptionsConfig)

  @SuppressWarnings(Array("PointlessTypeBounds"))
  def valueHandler[A <: AnyVal: c.WeakTypeTag, Opts: c.WeakTypeTag]: c.Expr[BSONHandler[A]] = {
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

  def configuredHandler[A: c.WeakTypeTag, Opts: c.WeakTypeTag]: c.Expr[BSONDocumentHandler[A]] = handlerWithConfig[A, Opts](withOptionsConfig)

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
          s"Type ${tpeSym.fullName} is not a document one")
      }
    } else if (tpeSym.isSealed && tpeSym.isAbstract) { // sealed trait
      reify(DocumentClass.unchecked[A])
    } else tpeSym.companion.typeSignature.decl(TermName("unapply")) match {
      case NoSymbol =>
        c.abort(
          c.enclosingPosition,
          s"Type ${tpeSym.fullName} is not a document one")

      case _ =>
        reify(DocumentClass.unchecked[A])
    }
  }

  @com.github.ghik.silencer.silent("dead\\ code")
  def migrationRequired[A: c.WeakTypeTag](
    details: c.Expr[String]): c.Expr[A] = {

    if (!sys.props.get("reactivemongo.api.migrationRequired.nonFatal").exists {
      v => v.toLowerCase == "true" || v.toLowerCase == "yes"
    }) {
      val msg: String = details.tree match {
        case Literal(Constant(str: String)) =>
          s"Migration required: $str"

        case v => {
          c.warning(
            c.enclosingPosition,
            s"Invalid 'details' parameter for 'migrationRequired': ${show(v)}")

          "Migration required"
        }
      }

      c.abort(c.enclosingPosition, msg)
    }

    c.Expr[A](q"scala.Predef.`???`")
  }

  // ---

  private def readerWithConfig[A: c.WeakTypeTag, Opts: c.WeakTypeTag](config: c.Expr[MacroConfiguration]): c.Expr[BSONDocumentReader[A]] = reify(new BSONDocumentReader[A] {
    private val r: BSONDocument => UTry[A] = { macroDoc =>
      createHelper[A, Opts](config).readBody.splice
    }

    lazy val forwardBSONReader: BSONDocumentReader[A] =
      BSONDocumentReader.from[A](r)

    def readDocument(document: BSONDocument): UTry[A] =
      forwardBSONReader.readDocument(document)
  })

  private def writerWithConfig[A: c.WeakTypeTag, Opts: c.WeakTypeTag](config: c.Expr[MacroConfiguration]): c.Expr[BSONDocumentWriter[A]] = reify(new BSONDocumentWriter[A] {
    private val w: A => UTry[BSONDocument] = { macroVal =>
      createHelper[A, Opts](config).writeBody.splice
    }

    lazy val forwardBSONWriter: BSONDocumentWriter[A] =
      BSONDocumentWriter.from[A](w)

    def writeTry(v: A) = forwardBSONWriter.writeTry(v)
  })

  private def handlerWithConfig[A: c.WeakTypeTag, Opts: c.WeakTypeTag](config: c.Expr[MacroConfiguration]): c.Expr[BSONDocumentHandler[A]] = reify(new BSONDocumentHandler[A] {
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

  private def withOptionsConfig: c.Expr[MacroConfiguration] =
    c.Expr[MacroConfiguration](c.typecheck(q"${c.prefix}.config"))

  private def implicitOptionsConfig: c.Expr[MacroConfiguration] =
    c.Expr[MacroConfiguration](c.inferImplicitValue(
      c.typeOf[MacroConfiguration]))

  private def createHelper[A: c.WeakTypeTag, Opts: c.WeakTypeTag](
    config: c.Expr[MacroConfiguration]) = new Helper[c.type, A](config) {
    val aTpe = c.weakTypeOf[A]
    val optsTpe = c.weakTypeOf[Opts]
  }

  /**
   * @define topParam is it a top tree (or not in case of auto-mat of sub-type)
   */
  private abstract class Helper[C <: Context, A](
    config: c.Expr[MacroConfiguration]) extends ImplicitResolver[C] {

    private val bsonPkg = q"_root_.reactivemongo.api.bson"
    private val exceptionsPkg = q"${bsonPkg}.exceptions"
    private val colPkg = q"_root_.scala.collection.immutable"
    private val utilPkg = q"_root_.scala.util"
    private val reflPkg = q"_root_.scala.reflect"

    private val optionTpe = c.typeOf[Option[_]]
    private val defaultValueAnnotationTpe = c.typeOf[DefaultValue[_]]
    private val readerAnnotationTpe = c.typeOf[Reader[_]]
    private val writerAnnotationTpe = c.typeOf[Writer[_]]

    protected def aTpe: Type
    protected def optsTpe: Type

    private val writerType: Type = typeOf[BSONWriter[_]].typeConstructor
    private val docWriterType: Type =
      typeOf[BSONDocumentWriter[_]].typeConstructor

    private val readerType: Type = typeOf[BSONReader[_]].typeConstructor
    private val docReaderType: Type =
      typeOf[BSONDocumentReader[_]].typeConstructor

    // Init MacroConfiguration (possibility lazy) in a local val,
    // to avoid evaluating the configuration each time required
    // in the generated handler.
    lazy val macroCfg = TermName(c.freshName("macroCfg"))
    lazy val macroCfgInit =
      q"val $macroCfg: ${bsonPkg}.MacroConfiguration = ${config}"

    private lazy val debugEnabled = hasOption[MacroOptions.Verbose]

    lazy val readBody: c.Expr[UTry[A]] = {
      val reader = unionTypes.map { types =>
        val resolve = resolver(
          Map.empty, "BSONReader", debugEnabled)(readerType)

        val preparedTypes = types.map { typ =>
          val cls = q"implicitly[$reflPkg.ClassTag[$typ]].runtimeClass"

          Tuple3(
            typ,
            TermName(c.freshName("Type")),
            q"$macroCfg.typeNaming($cls)")
        }

        val cases: List[CaseDef] = preparedTypes.map {
          case (typ, pattern, _) =>
            val body = readBodyFromImplicit(typ)(resolve).getOrElse {
              if (hasOption[MacroOptions.AutomaticMaterialization]) {
                // No existing implicit, but can fallback to automatic mat
                readBodyConstruct(typ, top = false)
              } else {
                c.abort(c.enclosingPosition, s"Implicit not found for '${typ.typeSymbol.name}': ${classOf[BSONReader[_]].getName}[${typ.typeSymbol.fullName}]")
              }
            }

            cq"${Ident(pattern)} => $body"
        }

        val dt = TermName(c.freshName("discriminator"))
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
      } getOrElse readBodyConstruct(aTpe, top = true)

      val result = c.Expr[UTry[A]](reader)

      if (debugEnabled) {
        c.echo(c.enclosingPosition, s"// Reader\n${show(reader)}")
      }

      result
    }

    lazy val valueReaderBody: c.Expr[UTry[A]] = {
      val ctor = aTpe.decl(c.universe.termNames.CONSTRUCTOR).asMethod

      ctor.paramLists match {
        case List(v: TermSymbol) :: Nil => {
          val typ = v.info
          val resolve = resolver(
            Map.empty, "BSONReader", debugEnabled)(readerType)

          resolve(typ)._1 match {
            case EmptyTree =>
              c.abort(c.enclosingPosition, s"Implicit not found for '${typ.typeSymbol.name}': ${classOf[BSONReader[_]].getName}[${typ.typeSymbol.fullName}]")

            case reader => {
              val body =
                q"${reader}.readTry(macroVal).map { new ${aTpe}(_) }"

              if (debugEnabled) {
                c.echo(
                  c.enclosingPosition,
                  s"// Value reader\n${show(body)}")
              }

              c.Expr[UTry[A]](body)
            }
          }
        }
      }
    }

    lazy val writeBody: c.Expr[UTry[BSONDocument]] = {
      val valNme = TermName("macroVal")
      val writer = unionTypes.map { types =>
        val resolve = resolver(
          Map.empty, "BSONDocumentWriter", debugEnabled)(writerType)

        val cases = types.map { typ =>
          val nme = TermName(c.freshName("macroVal"))
          val id = Ident(nme)
          val body = writeBodyFromImplicit(id, typ)(resolve).getOrElse {
            if (hasOption[MacroOptions.AutomaticMaterialization]) {
              // No existing implicit, but can fallback to automatic mat
              writeBodyConstruct(id, typ, top = false)
            } else {
              c.abort(c.enclosingPosition, s"Implicit not found for '${typ.typeSymbol.name}': ${classOf[BSONWriter[_]].getName}[${typ.typeSymbol.fullName}]")
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
      } getOrElse writeBodyConstruct(Ident(valNme), aTpe, top = true)

      val result = c.Expr[UTry[BSONDocument]](writer)

      if (debugEnabled) {
        c.echo(c.enclosingPosition, s"// Writer\n${show(writer)}")
      }

      result
    }

    lazy val valueWriterBody: c.Expr[UTry[BSONValue]] = {
      val ctor = aTpe.decl(c.universe.termNames.CONSTRUCTOR).asMethod

      ctor.paramLists match {
        case List(v: TermSymbol) :: Nil => {
          val typ = v.info
          val resolve = resolver(
            Map.empty, "BSONWriter", debugEnabled)(writerType)

          resolve(typ)._1 match {
            case EmptyTree =>
              c.abort(c.enclosingPosition, s"Implicit not found for '${typ.typeSymbol.name}': ${classOf[BSONWriter[_]].getName}[${typ.typeSymbol.fullName}]")

            case writer => {
              val body = q"${writer}.writeTry(macroVal.${v.name})"

              if (debugEnabled) {
                c.echo(
                  c.enclosingPosition,
                  s"// Value writer\n${show(body)}")
              }

              c.Expr[UTry[BSONValue]](body)
            }
          }
        }
      }
    }

    // For member of a union
    private def readBodyFromImplicit(tpe: Type)(r: Type => Implicit): Option[Tree] = {
      val (reader, _) = r(tpe)

      if (!reader.isEmpty) {
        Some(q"$reader.readTry(macroDoc)")
      } else None
    }

    /*
     * @param top $topParam
     */
    @inline private def readBodyConstruct(tpe: Type, top: Boolean): Tree =
      if (isSingleton(tpe)) readBodyConstructSingleton(tpe)
      else readBodyConstructClass(tpe, top)

    private def readBodyConstructSingleton(tpe: Type): Tree = {
      val sym = tpe match {
        case SingleType(_, sym) => sym
        case TypeRef(_, sym, _) => sym
        case _ => c.abort(c.enclosingPosition, s"Something weird is going on with '$tpe'. Should be a singleton but can't parse it")
      }

      q"${utilPkg}.Success(${Ident(TermName(sym.name.toString))})"
    }

    private type ReadableProperty = Tuple6[Symbol, String, TermName, Type, /*default:*/ Option[Tree], /*reader:*/ Option[Tree]]

    private object ReadableProperty {
      def apply(
        symbol: Symbol,
        name: String,
        term: TermName,
        tpe: Type,
        default: Option[Tree],
        reader: Option[Tree]): ReadableProperty = Tuple6(symbol, name, term, tpe, default, reader)

      def unapply(p: ReadableProperty) = Some(p)
    }

    /*
     * @param top $topParam
     */
    private def readBodyConstructClass(tpe: Type, top: Boolean): Tree = {
      val (constructor, _) = matchingApplyUnapply(tpe).getOrElse(
        c.abort(c.enclosingPosition, s"No matching apply/unapply found: $tpe"))

      val boundTypes: Map[String, Type] = {
        val bt = Map.newBuilder[String, Type]

        val tpeArgs: List[c.Type] = tpe match {
          case TypeRef(_, _, args) => args
          case i @ ClassInfoType(_, _, _) => i.typeArgs
        }

        lazyZip(constructor.typeParams, tpeArgs).foreach {
          case (sym, ty) => bt += (sym.fullName -> ty)
        }

        bt.result()
      }

      val resolve = resolver(boundTypes, "BSONReader", debugEnabled)(readerType)
      val bufErr = TermName(c.freshName("err"))

      val companionObject = tpe.typeSymbol.companion
      val params: Seq[ReadableProperty] =
        constructor.paramLists.headOption.toSeq.flatten.
          map(_.asTerm).zipWithIndex.map {
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
                      c.abort(c.enclosingPosition, s"Invalid annotation @DefaultValue($invalid) for '$pname': $sig value expected") // TODO: abort
                  }
                }

                case ann if ann.tree.tpe <:< readerAnnotationTpe => {
                  if (!(ann.tree.tpe <:< readerAnnTpe)) {
                    abort(s"Invalid annotation @Reader(${show(ann.tree)}) for '$pname': Reader[${sig}]")
                  }

                  readerAnns ++= ann.tree.children.tail
                }

                case _ =>
              }

              val readerFromAnn: Option[Tree] = readerAnns.result() match {
                case r +: other => {
                  if (other.nonEmpty) {
                    warn(s"Exactly one @Reader must be provided for each property; Ignoring invalid annotations for '${pname}'")
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
                } else defaultFromAnn.result() match {
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

              ReadableProperty(
                symbol = param,
                name = pname,
                term = TermName(c.freshName(pname)),
                tpe = sig,
                default = default,
                reader = readerFromAnn)
          }

      val decls = q"""val ${bufErr} =
        ${colPkg}.Seq.newBuilder[${exceptionsPkg}.HandlerException]""" +: (
        params.map {
          case ReadableProperty(_, _, vt, sig, _, _) =>
            q"val ${vt} = new ${bsonPkg}.Macros.LocalVar[${sig}]"
        })

      val rt = TermName(c.freshName("read"))
      val errName = TermName(c.freshName("cause"))

      val values = params.map {
        case ReadableProperty(param, pname, vt, sig, default, fieldReader) =>
          val reader: Tree = fieldReader match {
            case Some(r) => r

            case _ => sig match {
              case OptionTypeParameter(ot) => resolve(sig) match {
                case (r, _) if r.nonEmpty =>
                  r // a reader explicitly defined for an Option[x]

                case _ => resolve(ot)._1
              }

              case _ => resolve(sig)._1
            }
          }

          if (reader.isEmpty) {
            c.abort(c.enclosingPosition, s"Implicit not found for '$pname': ${classOf[BSONReader[_]].getName}[$sig]")
          }

          def tryWithDefault(`try`: Tree, dv: Tree) = q"""${`try`} match {
            case readSuccess @ ${utilPkg}.Success(_) => 
              readSuccess

            case ${utilPkg}.Failure(
              _: ${exceptionsPkg}.BSONValueNotFoundException) =>
              ${utilPkg}.Success(${dv})

            case readFailure @ ${utilPkg}.Failure(_) =>
              readFailure
          }"""

          val get: Tree = {
            if (param.annotations.exists(_.tree.tpe =:= typeOf[Flatten])) {
              if (reader.toString == "forwardBSONReader") {
                c.abort(
                  c.enclosingPosition,
                  s"Cannot flatten reader for '$pname': recursive type")
              }

              if (!(reader.tpe <:< appliedType(docReaderType, List(sig)))) {
                c.abort(c.enclosingPosition, s"Cannot flatten reader '$reader': doesn't conform BSONDocumentReader")
              }

              val readTry = q"${reader}.readTry(macroDoc)"

              default.fold(readTry) { dv => tryWithDefault(readTry, dv) }
            } else {
              val field = q"$macroCfg.fieldNaming($pname)"

              OptionTypeParameter.unapply(sig) match {
                case Some(_) if fieldReader.isEmpty => {
                  val getAsUnflattenedTry =
                    q"macroDoc.getAsUnflattenedTry($field)($reader)"

                  default match {
                    case Some(dv) =>
                      q"${getAsUnflattenedTry}.map(_.orElse(${dv}))"

                    case _ =>
                      getAsUnflattenedTry
                  }
                }

                case _ => {
                  val getAsTry = q"macroDoc.getAsTry($field)($reader)"

                  default.fold(getAsTry) { dv => tryWithDefault(getAsTry, dv) }
                }
              }
            }
          }

          q"""${get} match {
              case ${utilPkg}.Success($rt) => ${vt}.take($rt); ()

              case ${utilPkg}.Failure($errName) => 
                ${bufErr} += ${exceptionsPkg}.HandlerException($pname, $errName)
            }"""
      }

      val applyArgs = params.map {
        case ReadableProperty(_, _, vt, _, _, _) => q"${vt}.value()"
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

    private def writeBodyFromImplicit(id: Ident, tpe: Type)(r: Type => Implicit): Option[Tree] = {
      val (writer, _) = r(tpe)

      if (!writer.isEmpty) {
        @inline def doc = q"$writer.writeTry($id)"

        Some(classNameTree(tpe).fold(doc) { de =>
          q"""${doc}.map { _ ++ $de }"""
        })
      } else None
    }

    /*
     * @param top $topParam
     */
    @inline private def writeBodyConstruct(
      id: Ident,
      tpe: Type,
      top: Boolean): Tree = {
      if (isSingleton(tpe)) writeBodyConstructSingleton(tpe)
      else writeBodyConstructClass(id, tpe, top)
    }

    private def writeBodyConstructSingleton(tpe: Type): Tree =
      classNameTree(tpe).map { discriminator =>
        q"${utilPkg}.Success(${bsonPkg}.BSONDocument(${discriminator}))"
      } getOrElse q"${utilPkg}.Success(${bsonPkg}.BSONDocument.empty)"

    private type WritableProperty = Tuple4[Symbol, Int, Type, Option[Tree]]

    private object WritableProperty {
      def apply(symbol: Symbol, index: Int, tpe: Type, writer: Option[Tree]) =
        Tuple4(symbol, index, tpe, writer)

      def unapply(property: WritableProperty) = Some(property)
    }

    private def writeBodyConstructClass(
      id: Ident,
      tpe: Type,
      top: Boolean): Tree = {
      val (constructor, deconstructor) = matchingApplyUnapply(tpe).getOrElse(
        c.abort(c.enclosingPosition, s"No matching apply/unapply found: $tpe"))

      val types = unapplyReturnTypes(deconstructor)
      val constructorParams: List[Symbol] =
        constructor.paramLists.headOption.getOrElse(List.empty[Symbol])

      val boundTypes: Map[String, Type] = {
        val bt = Map.newBuilder[String, Type]

        val tpeArgs: List[c.Type] = tpe match {
          case TypeRef(_, _, args) => args
          case i @ ClassInfoType(_, _, _) => i.typeArgs
        }

        lazyZip(constructor.typeParams, tpeArgs).map {
          case (sym, ty) => bt += (sym.fullName -> ty)
        }

        bt.result()
      }

      val resolve = resolver(boundTypes, "BSONWriter", debugEnabled)(writerType)
      val tupleName = TermName(c.freshName("tuple"))

      val (optional, required) =
        lazyZip(constructorParams.zipWithIndex, types).collect {
          case ((sym, i), o @ OptionTypeParameter(st)) if !ignoreField(sym) => {
            val sig = boundTypes.get(st.typeSymbol.fullName).fold(o) { t =>
              o.substituteTypes(List(st.typeSymbol), List(t))
            }

            Tuple3(sym, i, sig)
          }

          case ((sym, i), sig) if !ignoreField(sym) =>
            Tuple3(sym, i, sig)

        }.map {
          case (sym, i, sig) =>
            val writerAnnTpe = appliedType(writerAnnotationTpe, List(sig))

            val writerAnns = sym.annotations.flatMap {
              case ann if ann.tree.tpe <:< writerAnnotationTpe => {
                if (!(ann.tree.tpe <:< writerAnnTpe)) {
                  abort(s"Invalid annotation @Writer(${show(ann.tree)}) for '${paramName(sym)}': Writer[${sig}]")
                }

                ann.tree.children.tail
              }

              case _ =>
                Seq.empty[Tree]
            }

            val writerFromAnn: Option[Tree] = writerAnns match {
              case w +: other => {
                if (other.nonEmpty) {
                  warn(s"Exactly one @Writer must be provided for each property; Ignoring invalid annotations for '${paramName(sym)}'")
                }

                Some(w)
              }

              case _ =>
                None
            }

            WritableProperty(sym, i, sig, writerFromAnn)
        }.partition(t => isOptionalType(t._3))

      def resolveWriter(pname: String, tpe: Type) = {
        val (writer, _) = resolve(tpe)

        if (writer.isEmpty) {
          c.abort(c.enclosingPosition, s"Implicit not found for '$pname': ${classOf[BSONWriter[_]].getName}[$tpe]")
        }

        writer
      }

      val tupleElement: Int => Tree = {
        val tuple = Ident(tupleName)
        if (types.length == 1) { _: Int => tuple }
        else { i: Int => Select(tuple, TermName("_" + (i + 1))) }
      }

      val bufOk = TermName(c.freshName("ok"))
      val bufErr = TermName(c.freshName("err"))

      def mustFlatten(
        param: Symbol,
        pname: String,
        sig: Type,
        writer: Tree): Boolean = {
        if (param.annotations.exists(_.tree.tpe =:= typeOf[Flatten])) {
          if (writer.toString == "forwardBSONWriter") {
            c.abort(
              c.enclosingPosition,
              s"Cannot flatten writer for '$pname': recursive type")
          }

          if (!(writer.tpe <:< appliedType(docWriterType, List(sig)))) {
            c.abort(c.enclosingPosition, s"Cannot flatten writer '$writer': doesn't conform BSONDocumentWriter")
          }

          true
        } else false
      }

      val errName = TermName(c.freshName("cause"))

      val values = required.map {
        case WritableProperty(param, i, sig, writerFromAnn) =>
          val pname = paramName(param)
          val writer = writerFromAnn getOrElse resolveWriter(pname, sig)

          val writeCall = q"$writer.writeTry(${tupleElement(i)}): ${utilPkg}.Try[${bsonPkg}.BSONValue]"

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

      val extra = optional.collect {
        case WritableProperty(param, i, optType @ OptionTypeParameter(sig), _ /*TODO*/ ) =>
          val pname = paramName(param)

          val writer = resolve(optType) match {
            case (w, _) if w.nonEmpty =>
              w // a writer explicitly defined for a Option[x]

            case _ => resolveWriter(pname, sig)
          }
          val field = q"$macroCfg.fieldNaming($pname)"

          val vt = TermName(c.freshName(pname))
          val vp = ValDef(
            Modifiers(Flag.PARAM),
            vt, TypeTree(sig), EmptyTree) // ${vt} =>

          val bt = TermName(c.freshName("bson"))

          def empty =
            q"${bufOk} += ${bsonPkg}.BSONElement($field, ${bsonPkg}.BSONNull)"

          val writeCall = q"""($writer.writeTry($vt): ${utilPkg}.Try[${bsonPkg}.BSONValue]) match {
            case ${utilPkg}.Success(${bt}) =>
              ${bufOk} += ${bsonPkg}.BSONElement($field, $bt)
              ()

            case ${utilPkg}.Failure(${errName}) =>
              ${bufErr} += ${exceptionsPkg}.HandlerException($pname, $errName)
              ()
          }"""

          if (param.annotations.exists(_.tree.tpe =:= typeOf[NoneAsNull])) {
            q"${tupleElement(i)}.fold({ ${empty}; () }) { ${vp} => $writeCall }"
          } else {
            q"${tupleElement(i)}.foreach { ${vp} => $writeCall }"
          }
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

    private def classNameTree(tpe: c.Type): Option[Tree] = {
      val tpeSym = aTpe.typeSymbol.asClass

      if (hasOption[MacroOptions.UnionType[_]] || tpeSym.isSealed && tpeSym.isAbstract) Some {
        val cls = q"implicitly[$reflPkg.ClassTag[$tpe]].runtimeClass"

        q"""${bsonPkg}.BSONElement(
          $macroCfg.discriminator, 
          ${bsonPkg}.BSONString($macroCfg.typeNaming($cls)))"""

      }
      else None
    }

    private lazy val unionTypes: Option[List[c.Type]] =
      parseUnionTypes orElse directKnownSubclasses

    private def parseUnionTypes: Option[List[c.Type]] = {
      val unionOption = c.typeOf[MacroOptions.UnionType[_]]
      val union = c.typeOf[MacroOptions.\/[_, _]]

      @annotation.tailrec
      def parseUnionTree(trees: List[Type], found: List[Type]): List[Type] =
        trees match {
          case tree :: rem => if (tree <:< union) {
            tree match {
              case TypeRef(_, _, List(a, b)) =>
                parseUnionTree(a :: b :: rem, found)

              case _ => c.abort(
                c.enclosingPosition,
                s"Union type parameters expected: $tree")
            }
          } else parseUnionTree(rem, tree :: found)

          case _ => found
        }

      val tree = optsTpe match {
        case t @ TypeRef(_, _, lst) if t <:< unionOption =>
          lst.headOption

        case RefinedType(types, _) =>
          types.filter(_ <:< unionOption).flatMap {
            case TypeRef(_, _, args) => args
          }.headOption

        case _ => None
      }

      tree.map { t => parseUnionTree(List(t), Nil) }
    }

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

    private def hasOption[O: c.TypeTag]: Boolean = optsTpe <:< typeOf[O]

    private def unapplyReturnTypes(deconstructor: MethodSymbol): List[c.Type] = {
      val opt = deconstructor.returnType match {
        case TypeRef(_, _, Nil) => Some(Nil)

        case TypeRef(_, _, (t @ TypeRef(_, _, Nil)) :: _) =>
          Some(List(t))

        case TypeRef(_, _, (typ @ TypeRef(_, t, args)) :: _) =>
          Some(
            if (t.name.toString.matches("Tuple\\d\\d?")) args else List(typ))

        case _ => None
      }

      opt getOrElse c.abort(c.enclosingPosition, "something wrong with unapply type")
    }

    //Some(A) for Option[A] else None
    private object OptionTypeParameter {
      def unapply(tpe: c.Type): Option[c.Type] = {
        if (isOptionalType(tpe)) {
          tpe match {
            case TypeRef(_, _, args) => args.headOption
            case _ => None
          }
        } else None
      }
    }

    private def isOptionalType(implicit A: c.Type): Boolean =
      (optionTpe.typeConstructor == A.typeConstructor)

    private def paramName(param: c.Symbol): String = {
      param.annotations.collect {
        case ann if ann.tree.tpe =:= typeOf[Key] =>
          ann.tree.children.tail.collect {
            case l: Literal =>
              l.value.value

            case _ =>
              c.abort(
                c.enclosingPosition,
                "Annotation @Key must be provided with a pure/literal value")

          }.collect {
            case value: String => value
          }
      }.flatten.headOption getOrElse param.name.toString
    }

    private def ignoreField(param: Symbol): Boolean =
      param.annotations.exists(ann =>
        ann.tree.tpe =:= typeOf[Ignore] || ann.tree.tpe =:= typeOf[transient])

    private def applyMethod(implicit tpe: Type): Option[Symbol] =
      companion(tpe).typeSignature.decl(TermName("apply")) match {
        case NoSymbol => {
          if (hasOption[MacroOptions.Verbose]) {
            c.echo(c.enclosingPosition, s"No apply function found for $tpe")
          }

          None
        }

        case s => Some(s)
      }

    private def unapplyMethod(implicit tpe: Type): Option[MethodSymbol] =
      companion(tpe).typeSignature.decl(TermName("unapply")) match {
        case NoSymbol => {
          if (hasOption[MacroOptions.Verbose]) {
            c.echo(c.enclosingPosition, s"No unapply function found for $tpe")
          }

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

    /* Deep check for type compatibility */
    @annotation.tailrec
    @SuppressWarnings(Array("ListSize"))
    private def conforms(types: Seq[(Type, Type)]): Boolean =
      types.headOption match {
        case Some((TypeRef(NoPrefix, a, _),
          TypeRef(NoPrefix, b, _))) => { // for generic parameter
          if (a.fullName != b.fullName) {
            c.warning(
              c.enclosingPosition,
              s"Type symbols are not compatible: $a != $b")

            false
          } else conforms(types.tail)
        }

        case Some((a, b)) if (a.typeArgs.size != b.typeArgs.size) => {
          warn(s"Type parameters are not matching: $a != $b")

          false
        }

        case Some((a, b)) if a.typeArgs.isEmpty =>
          if (a =:= b) conforms(types.tail) else {
            warn(s"Types are not compatible: $a != $b")

            false
          }

        case Some((a, b)) if (a.baseClasses != b.baseClasses) => {
          warn(s"Generic types are not compatible: $a != $b")

          false
        }

        case Some((a, b)) =>
          conforms(lazyZip(a.typeArgs, b.typeArgs) ++: types.tail)

        case _ => true
      }

    /**
     * @return (apply symbol, unapply symbol)
     */
    @SuppressWarnings(Array("ListSize"))
    private def matchingApplyUnapply(implicit tpe: Type): Option[(MethodSymbol, MethodSymbol)] = for {
      applySymbol <- applyMethod(tpe)
      unapply <- unapplyMethod(tpe)
      alternatives = applySymbol.asTerm.alternatives.map(_.asMethod)
      u = unapplyReturnTypes(unapply)

      apply <- alternatives.find { alt =>
        alt.paramLists match {
          case params :: ps if (ps.isEmpty || ps.headOption.flatMap(
            _.headOption).exists(_.isImplicit)) => if (params.size != u.size) false else {
            conforms(lazyZip(params.map(_.typeSignature), u).toSeq)
          }

          case _ => {
            warn(s"Constructor with multiple parameter lists is not supported: ${tpe.typeSymbol.name}${alt.typeSignature}")

            false
          }
        }
      }
    } yield apply -> unapply

    private def isSingleton(tpe: Type): Boolean = tpe <:< typeOf[Singleton]

    @inline private def companion(tpe: Type): Symbol = tpe.typeSymbol.companion

    private lazy val warn: String => Unit = {
      if (hasOption[MacroOptions.DisableWarnings]) {
        (_: String) => ()
      } else { msg: String =>
        c.warning(c.enclosingPosition, msg)
      }
    }

    @inline private def abort(msg: String): Unit =
      c.abort(c.enclosingPosition, msg)
  }

  sealed trait ImplicitResolver[C <: Context] {
    protected def aTpe: Type

    import Macros.Placeholder

    // The placeholder type
    private val PlaceholderType: Type = typeOf[Placeholder]

    /* Refactor the input types, by replacing any type matching the `filter`,
     * by the given `replacement`.
     */
    @annotation.tailrec
    private def refactor(boundTypes: Map[String, Type])(in: List[Type], base: TypeSymbol, out: List[Type], tail: List[(List[Type], TypeSymbol, List[Type])], filter: Type => Boolean, replacement: Type, altered: Boolean): (Type, Boolean) = in match {
      case tpe :: ts =>
        boundTypes.getOrElse(tpe.typeSymbol.fullName, tpe) match {
          case t if (filter(t)) =>
            refactor(boundTypes)(ts, base, (replacement :: out), tail,
              filter, replacement, true)

          case TypeRef(_, sym, as) if as.nonEmpty =>
            refactor(boundTypes)(
              as, sym.asType, List.empty, (ts, base, out) :: tail,
              filter, replacement, altered)

          case t => refactor(boundTypes)(
            ts, base, (t :: out), tail, filter, replacement, altered)
        }

      case _ => {
        val tpe = appliedType(base, out.reverse)

        tail match {
          case (x, y, more) :: ts => refactor(boundTypes)(
            x, y, (tpe :: more), ts, filter, replacement, altered)

          case _ => tpe -> altered
        }
      }
    }

    /**
     * Replaces any reference to the type itself by the Placeholder type.
     * @return the normalized type + whether any self reference has been found
     */
    private def normalized(boundTypes: Map[String, Type])(tpe: Type): (Type, Boolean) = boundTypes.getOrElse(tpe.typeSymbol.fullName, tpe) match {
      case t if (t =:= aTpe) => PlaceholderType -> true

      case TypeRef(_, sym, args) if args.nonEmpty =>
        refactor(boundTypes)(args, sym.asType, List.empty, List.empty,
          _ =:= aTpe, PlaceholderType, false)

      case t => t -> false
    }

    /* Restores reference to the type itself when Placeholder is found. */
    private def denormalized(boundTypes: Map[String, Type])(ptype: Type): Type =
      ptype match {
        case PlaceholderType => aTpe

        case TypeRef(_, sym, args) if args.nonEmpty =>
          refactor(boundTypes)(args, sym.asType, List.empty, List.empty,
            _ == PlaceholderType, aTpe, false)._1

        case _ => ptype
      }

    private class ImplicitTransformer(
      boundTypes: Map[String, Type],
      forwardSuffix: String) extends Transformer {
      private val denorm = denormalized(boundTypes) _
      val forwardName = TermName(s"forward$forwardSuffix")

      override def transform(tree: Tree): Tree = tree match {
        case tt: TypeTree =>
          super.transform(TypeTree(denorm(tt.tpe)))

        case Select(Select(This(TypeName("Macros")), t), sym) if (
          t.toString == "Placeholder" && sym.toString == "Handler") => super.transform(q"$forwardName")

        case _ => super.transform(tree)
      }
    }

    private def createImplicit(
      debugEnabled: Boolean,
      boundTypes: Map[String, Type])(tc: Type, ptype: Type, tx: Transformer): Implicit = {
      val tpe = ptype
      val (ntpe, selfRef) = normalized(boundTypes)(tpe)
      val ptpe = boundTypes.getOrElse(ntpe.typeSymbol.fullName, ntpe)

      // infers implicit
      val neededImplicitType = appliedType(tc.typeConstructor, ptpe)
      val neededImplicit = if (!selfRef) {
        c.inferImplicitValue(neededImplicitType)
      } else c.untypecheck(
        // Reset the type attributes on the refactored tree for the implicit
        tx.transform(c.inferImplicitValue(neededImplicitType)))

      if (debugEnabled) {
        c.echo(c.enclosingPosition, s"// Resolve implicit ${tc} for ${ntpe} as ${neededImplicitType} (self? ${selfRef}) = ${neededImplicit}")
      }

      neededImplicit -> selfRef
    }

    // To print the implicit types in the compiler messages
    private def prettyType(boundTypes: Map[String, Type])(t: Type): String =
      boundTypes.getOrElse(t.typeSymbol.fullName, t) match {
        case TypeRef(_, base, args) if args.nonEmpty => s"""${base.asType.fullName}[${args.map(prettyType(boundTypes)(_)).mkString(", ")}]"""

        case t => t.typeSymbol.fullName
      }

    protected def resolver(
      boundTypes: Map[String, Type],
      forwardSuffix: String,
      debugEnabled: Boolean)(tc: Type): Type => Implicit = {
      val tx = new ImplicitTransformer(boundTypes, forwardSuffix)
      createImplicit(debugEnabled, boundTypes)(tc, _: Type, tx)
    }

    type Implicit = (Tree, Boolean)
  }
}
