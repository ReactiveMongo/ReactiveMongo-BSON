package reactivemongo.api.bson

import scala.util.{ Try => TryResult }

import scala.collection.View
import scala.collection.mutable.{ Builder => MBuilder }

import scala.deriving.Mirror.ProductOf
import scala.quoted.{ quotes, Expr, Quotes, Type }
import scala.reflect.ClassTag

import exceptions.HandlerException

private[api] object MacroImpl:
  import Macros.Annotations,
  Annotations.{ DefaultValue, Ignore, Key, Writer, Flatten, NoneAsNull /*,
    ,
    Reader,
   */ }

  /*
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
    ]: c.Expr[BSONReader[A]] = reify(BSONReader.from[A] { macroVal =>
    createHelper[A, Opts](implicitOptionsConfig).valueReaderBody.splice
  })
   */

  def writer[A: Type, Opts <: MacroOptions.Default: Type](
      using
      q: Quotes,
      wat: Type[Writer],
      it: Type[Ignore],
      kt: Type[Key],
      flt: Type[Flatten],
      nant: Type[NoneAsNull]
    ): Expr[BSONDocumentWriter[A]] =
    writerWithConfig[A, Opts](implicitOptionsConfig)

  // TODO: Remove, directly call writerWithConfig
  def configuredWriter[A: Type, Opts <: MacroOptions.Default: Type](
      conf: Expr[MacroConfiguration]
    )(using
      q: Quotes,
      wat: Type[Writer],
      it: Type[Ignore],
      kt: Type[Key],
      flt: Type[Flatten],
      nant: Type[NoneAsNull]
    ): Expr[BSONDocumentWriter[A]] =
    writerWithConfig[A, Opts](conf)

  def valueWriter[A <: AnyVal: Type, Opts <: MacroOptions.Default: Type](
      using
      q: Quotes,
      wat: Type[Writer],
      it: Type[Ignore],
      kt: Type[Key],
      flt: Type[Flatten],
      nant: Type[NoneAsNull]
    ): Expr[BSONWriter[A]] = {
    import q.reflect.*

    val wlm = Lambda(
      Symbol.spliceOwner,
      MethodType(List("macroVal"))(
        _ => List(TypeRepr.of[A]),
        _ => TypeRepr.of[TryResult[BSONValue]]
      ),
      {
        case (_ /* TODO: owner */, List(arg: Term)) =>
          createHelper[A, Opts](implicitOptionsConfig)
            .valueWriterBody(arg, ??? /* TODO */ )
            .asTerm

        case _ =>
          report.errorAndAbort("Fails compile value writer")
      }
    ).asExprOf[A => TryResult[BSONValue]]

    '{ BSONWriter.from[A](${ wlm }) }
  }

  /* TODO
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
   */

  def documentClass[A: Type](using q: Quotes): Expr[DocumentClass[A]] = {
    import q.reflect.*

    val anyValTpe = TypeRepr.of[AnyVal]
    val bsonValueTpe = TypeRepr.of[BSONValue]
    val bsonDocTpe = TypeRepr.of[BSONDocument]
    val aTpe = TypeRepr.of[A].dealias

    def throwNotDoc =
      report.errorAndAbort(s"Type ${aTpe.show} is not a document one")

    if (aTpe <:< bsonValueTpe) {
      if (aTpe <:< bsonDocTpe) {
        '{ DocumentClass.unchecked[A] }
      } else {
        throwNotDoc
      }
    } else {
      val helper = new QuotesHelper {
        type Q = q.type
        val quotes = q
      }
      val pof = helper.productOf(aTpe)

      aTpe.classSymbol match {
        case Some(tpeSym) => {
          if (
            (tpeSym.flags.is(Flags.Abstract) &&
              tpeSym.flags.is(Flags.Sealed) &&
              !(aTpe <:< anyValTpe)) ||
            (tpeSym.flags.is(Flags.Sealed) &&
              tpeSym.flags.is(Flags.Trait)) || pof.nonEmpty
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

      q.reflect.report.errorAndAbort(msg)
    }

    '{ scala.Predef.`???` }
  }

  // ---

  /* TODO:
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
   */

  private def writerWithConfig[A: Type, Opts: Type](
      config: Expr[MacroConfiguration]
    )(using
      q: Quotes,
      wat: Type[Writer],
      it: Type[Ignore],
      kt: Type[Key],
      flt: Type[Flatten],
      nant: Type[NoneAsNull]
    ): Expr[BSONDocumentWriter[A]] = {
    import q.reflect.*

    val tp = Type.of[BSONDocumentWriter[A]]
    val wtpe = TypeRepr.of[BSONDocumentWriter[A]](using tp)

    val helper = createHelper[A, Opts](config)

    val tlm = Lambda(
      Symbol.spliceOwner,
      MethodType(List("forwardBSONWriter"))(
        _ => List(wtpe),
        _ => TypeRepr.of[A => TryResult[BSONDocument]]
      ),
      {
        case (top, List(forwardRef: Term)) =>
          Lambda(
            Symbol.spliceOwner,
            MethodType(List("macroVal"))(
              _ => List(TypeRepr.of[A]),
              _ => TypeRepr.of[TryResult[BSONDocument]]
            ),
            {
              case (m, List(macroVal: Term)) =>
                helper
                  .writeBody(macroVal, forwardRef.asExprOf(using tp))
                  .asTerm
                  .changeOwner(m)

              case (m, _) =>
                report.errorAndAbort(s"Fails compile writer lambda: ${top}")
            }
          ).changeOwner(top)

        case (top, _) =>
          report.errorAndAbort(s"Fails compile top lambda: ${top}")
      }
    ).asExprOf[BSONDocumentWriter[A] => (A => TryResult[BSONDocument])]

    '{
      def withSelf[T](
          f: BSONDocumentWriter[T] => (T => TryResult[BSONDocument])
        ): BSONDocumentWriter[T] = {
        new BSONDocumentWriter[T] { self =>
          val underlying = f(self)
          def writeTry(v: T) = underlying(v)
        }
      }

      withSelf(${ tlm })
    }
  }

  /* TODO
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
   */

  def implicitOptionsConfig(
      using
      q: Quotes,
      ct: Type[MacroConfiguration]
    ): Expr[MacroConfiguration] = {
    import q.reflect.*

    Expr.summon[MacroConfiguration](using ct) match {
      case Some(resolved) =>
        resolved

      case _ =>
        report.errorAndAbort("Fails to resolve a MacroConfiguration instance")
    }
  }

  private def createHelper[A: Type, Opts: Type](
      config: Expr[MacroConfiguration]
    )(using
      _quotes: Quotes,
      dwt: Type[BSONDocumentWriter],
      wat: Type[Writer],
      wt: Type[BSONWriter],
      it: Type[Ignore],
      kt: Type[Key],
      flt: Type[Flatten],
      nant: Type[NoneAsNull]
    ) =
    new Helper[A](config)
      with MacroTopHelpers[A]
      //with ReaderHelpers[A]
      with WriterHelpers[A] {
      type Q = _quotes.type
      val quotes = _quotes

      import quotes.reflect.*

      val docWriterType = dwt
      val writerAnnotationTpe = wat
      val writerType = wt
      val ignoreType = it
      val keyType = kt
      val flattenType = flt
      val noneAsNullType = nant

      val aTpeRepr = TypeRepr.of[A]
      val optsTpe = TypeRepr.of[Opts]
    }

  private abstract class Helper[A](
      val config: Expr[MacroConfiguration])
      extends ImplicitResolver[A]
      with QuotesHelper {
    self: MacroHelpers[A] /*TODO: with ReaderHelpers */ with WriterHelpers[A] =>

    import quotes.reflect.*

    /* TODO
    lazy val readBody: Expr[TryResult[A]] = {
      val nme = TermName("macroDoc")
      val reader = readerTree(id = Ident(nme), top = true)
      val result = c.Expr[TryResult[A]](reader)

      debug(s"// Reader\n${show(reader)}")

      result
    }

    lazy val valueReaderBody: Expr[TryResult[A]] = {
      val nme = TermName("macroVal")
      val reader = valueReaderTree(id = Ident(nme))

      debug(s"// Value reader\n${show(reader)}")

      c.Expr[TryResult[A]](reader)
    }
     */

    def writeBody(
        macroVal: Term,
        forwardExpr: Expr[BSONDocumentWriter[A]]
      ): Expr[TryResult[BSONDocument]] = {
      val writer = writerTree(macroVal, forwardExpr, top = true)

      //TODO:debug(s"// Writer\n${writer.show}")

      writer
    }

    def valueWriterBody(
        macroVal: Term,
        forwardExpr: Expr[BSONWriter[A]]
      ): Expr[TryResult[BSONValue]] = {
      val writer = valueWriterTree(macroVal, forwardExpr)

      debug(s"// Value writer\n${writer.show}")

      writer
    }
  }

  // ---

  sealed trait WriterHelpers[A] {
    self: MacroHelpers[A] with ImplicitResolver[A] with QuotesHelper =>

    import quotes.reflect.*

    // --- Writer types ---

    protected def writerType: Type[BSONWriter]

    private final lazy val writerTypeRepr: TypeRepr =
      TypeRepr.of(using writerType)

    protected def docWriterType: Type[BSONDocumentWriter]

    private final lazy val docWriterRepr: TypeRepr =
      TypeRepr.of(using docWriterType)

    protected def writerAnnotationTpe: Type[Writer]

    private final lazy val writerAnnotationRepr: TypeRepr =
      TypeRepr.of[Writer](using writerAnnotationTpe)

    private final lazy val writerAnyAnnotationRepr: TypeRepr = {
      given t: Type[Writer] = writerAnnotationTpe
      TypeRepr.of[Writer[_]]
    }

    private final lazy val successBsonVal =
      TypeRepr.of[scala.util.Success[BSONValue]]

    // --- Writer builders ---

    private lazy val tryWriteVal: Function2[Expr[BSONWriter[_]], Term, Expr[TryResult[BSONValue]]] = {
      val writeTry = writerTypeRepr.typeSymbol.declaredMethod("writeTry").head

      { (writerExpr, arg) =>
        writerExpr.asTerm
          .select(writeTry)
          .appliedTo(arg)
          .asExprOf[TryResult[BSONValue]]
      }
    }

    private lazy val tryWriteDoc: Function2[Expr[BSONDocumentWriter[_]], Term, Expr[TryResult[BSONDocument]]] = {
      val writeTry = docWriterRepr.typeSymbol.declaredMethod("writeTry").head

      { (writerExpr, arg) =>
        writerExpr.asTerm
          .select(writeTry)
          .appliedTo(arg)
          .asExprOf[TryResult[BSONDocument]]
      }
    }

    private lazy val fieldName: Function2[Term, String, Expr[String]] = {
      val cfgTpe = TypeRepr.of(using Type.of[MacroConfiguration])
      val nmgTpe = TypeRepr.of(using Type.of[FieldNaming])

      val fn = cfgTpe.typeSymbol.declaredMethod("fieldNaming").head
      val am = nmgTpe.typeSymbol.declaredMethod("apply").head

      { (cfg, nme) =>
        val fieldNaming = cfg.select(fn)

        fieldNaming.select(am).appliedTo(Expr(nme).asTerm).asExprOf[String]
      }
    }

    /**
     * @param valNme the term to be written (of type `aTpeRepr`)
     */
    protected final def writerTree(
        macroVal: Term,
        forwardExpr: Expr[BSONWriter[A]],
        top: Boolean
      ): Expr[TryResult[BSONDocument]] = withMacroCfg { cfgId =>
      unionTypes.map { types =>
        val resolve = resolver[BSONDocumentWriter](
          Map.empty,
          forwardExpr,
          debug
        )(docWriterType)

        val subHelper = createSubHelper(aTpeRepr)

        def cases = types.zipWithIndex.map { (typ, i) =>
          val bind =
            Symbol.newBind(
              Symbol.spliceOwner,
              s"macroTpe${i}",
              Flags.Case,
              typ
            )

          val br = Ref(bind)

          val body =
            writeBodyFromImplicit(cfgId, br, typ)(resolve).getOrElse {
              if (hasOption[MacroOptions.AutomaticMaterialization]) {
                // No existing implicit, but can fallback to automatic mat
                subHelper.writeBodyConstruct(
                  cfgId,
                  br,
                  forwardExpr,
                  typ,
                  top
                )

              } else {
                report.errorAndAbort(s"Instance not found for '${typ.typeSymbol.fullName}': ${classOf[BSONWriter[_]].getName}[${typ.typeSymbol.fullName}]")
              }
            }

          CaseDef(
            Typed(br, Inferred(typ)),
            guard = None,
            rhs = body.asTerm
          )
        }

        def fallback = CaseDef(
          Wildcard(),
          None,
          '{
            scala.util.Failure(
              reactivemongo.api.bson.exceptions.ValueDoesNotMatchException(${
                macroVal.asExpr
              }.toString)
            )
          }.asTerm
        )

        Match(macroVal, cases :+ fallback)
      } getOrElse {
        writeBodyConstruct(
          cfgId,
          macroVal,
          forwardExpr,
          aTpeRepr,
          top
        ).asTerm
      }
    }.asExprOf[TryResult[BSONDocument]]

    /**
     * @param macroVal the value to be written
     * @param tpe the type of the `macroVal`
     */
    private def writeBodyFromImplicit(
        macroCfgId: Ref,
        macroVal: Term,
        tpe: TypeRepr
      )(r: TypeRepr => Option[Implicit]
      ): Option[Expr[TryResult[BSONDocument]]] = r(tpe).map { (writer, _) =>
      def doc = tryWriteDoc(writer.asExprOf[BSONDocumentWriter[_]], macroVal)

      classNameTree(macroCfgId, tpe) match {
        case None =>
          doc

        case Some(de) =>
          '{ ${ doc }.map { _ ++ $de } }
      }
    }

    def valueWriterTree(
        macroVal: Term,
        forwardExpr: Expr[BSONWriter[A]]
      ): Expr[TryResult[BSONDocument]] = {
      val ctor = aTpeRepr.typeSymbol.primaryConstructor

      ctor.paramSymss match {
        case List(v: Symbol) :: Nil =>
          v.tree match {
            case term: Term => {
              val typ = term.tpe
              val resolve = resolver(Map.empty, forwardExpr, debug)(writerType)

              resolve(typ) match {
                case Some((writer, _)) => {
                  val cp = macroVal.select(
                    Symbol.newMethod(macroVal.symbol, v.name, typ)
                  )

                  tryWriteDoc(writer.asExprOf[BSONDocumentWriter[_]], cp)
                }

                case None =>
                  report.errorAndAbort(s"Instance not found for '${typ.typeSymbol.fullName}': ${classOf[BSONWriter[_]].getName}[${typ.typeSymbol.fullName}]")
              }
            }

            case _ =>
              report.errorAndAbort(
                s"Constructor parameter expected, found: ${v}"
              )
          }

        case _ =>
          report.errorAndAbort(
            s"Cannot resolve value writer for '${aTpeRepr.typeSymbol.name}'"
          )

      }
    }

    /*
     * @param top $topParam
     */
    @inline private def writeBodyConstruct(
        macroCfgId: Ref,
        macroVal: Term,
        forwardExpr: Expr[BSONWriter[A]],
        tpe: TypeRepr,
        top: Boolean
      ): Expr[TryResult[BSONDocument]] = {
      if (tpe.isSingleton) writeBodyConstructSingleton(macroCfgId, tpe)
      else writeBodyConstructClass(macroVal, forwardExpr, tpe, top)
    }

    private def writeBodyConstructSingleton(
        macroCfgId: Ref,
        repr: TypeRepr
      ): Expr[TryResult[BSONDocument]] = classNameTree(macroCfgId, repr) match {
      case Some(discriminator) =>
        '{ scala.util.Success(BSONDocument($discriminator)) }

      case None =>
        '{ scala.util.Success(BSONDocument.empty) }
    }

    private type WritableProperty =
      Tuple4[Symbol, Int, TypeRepr, Option[Expr[BSONWriter[_]]]]

    private object WritableProperty {

      def apply(
          symbol: Symbol,
          index: Int,
          tpe: TypeRepr,
          writerFromAnnotation: Option[Expr[BSONWriter[_]]]
        ) =
        Tuple4(symbol, index, tpe, writerFromAnnotation)

      def unapply(property: WritableProperty) = Some(property)
    }

    private lazy val writerCompanion =
      '{ reactivemongo.api.bson.BSONWriter }.asTerm

    private lazy val tryDocTpe = TypeRepr.of[TryResult[BSONDocument]]

    protected def flattenType: Type[Flatten]

    protected final lazy val flattenRepr: TypeRepr =
      TypeRepr.of(using flattenType)

    protected def noneAsNullType: Type[NoneAsNull]

    protected final lazy val noneAsNullRepr: TypeRepr =
      TypeRepr.of(using noneAsNullType)

    /**
     * @param id the ident of the value to be written
     * @param tpe the value type
     */
    private def writeBodyConstructClass(
        macroVal: Term,
        forwardExpr: Expr[BSONWriter[A]],
        tpe: TypeRepr,
        top: Boolean
      ): Expr[TryResult[BSONDocument]] = {
      val pof: Expr[ProductOf[Any]] = productOf(tpe) match {
        case Some(of) =>
          of

        case _ =>
          report.errorAndAbort(
            s"Instance not found for 'scala.deriving.Mirror.ProductOf[${tpe.typeSymbol.fullName}]'"
          )
      }

      val tpeElements = productElements(tpe, pof)
      val types = tpeElements.map(_._2)

      val resolve =
        resolver[BSONWriter](Map.empty, forwardExpr, debug)(writerType)

      val (optional, required) = tpeElements.zipWithIndex.view.filterNot {
        case ((sym, _), _) => ignoreField(sym)
      }.map {
        case ((sym, pt), i) =>
          val writerAnnTpe = writerAnnotationRepr.appliedTo(pt)

          val writerAnns = sym.annotations.flatMap {
            case ann if (ann.tpe <:< writerAnyAnnotationRepr) => {
              if (!(ann.tpe <:< writerAnnTpe)) {
                report.errorAndAbort(s"Invalid annotation @Writer(${ann.show}) for '${tpe.typeSymbol.fullName}.${sym.name}': Writer[${pt.typeSymbol.fullName}]")
              }

              sym.getAnnotation(writerAnnTpe.typeSymbol).collect {
                case Apply(_, List(writer)) => writer.asExprOf[BSONWriter[_]]
              }
            }

            case a =>
              Seq.empty[Expr[BSONWriter[_]]]
          }

          val writerFromAnn: Option[Expr[BSONWriter[_]]] = writerAnns match {
            case w +: other => {
              if (other.nonEmpty) {
                warn(s"Exactly one @Writer must be provided for each property; Ignoring invalid annotations for '${tpe.typeSymbol.fullName}.${sym.name}'")
              }

              Some(w)
            }

            case _ =>
              None
          }

          WritableProperty(sym, i, pt, writerFromAnn)
      }.partition(t => isOptionalType(t._3))

      /**
       * @param pname the parameter/field name
       * @param wtpe type parameter for the writer to be resolved
       */
      def resolveWriter( // TODO: Move outside
          pname: String,
          wtpe: TypeRepr
        ): Expr[BSONWriter[_]] = resolve(wtpe) match {
        case Some((writer, _)) =>
          writer.asExprOf[BSONWriter[_]]

        case None => {
          if (hasOption[MacroOptions.AutomaticMaterialization]) {
            val lt = leafType(wtpe)

            warn(s"Materializing ${classOf[BSONWriter[_]].getName}[${lt}] for '${tpe}.$pname': it's recommended to declare it explicitly")

            val subHelper = createSubHelper(lt)

            val nme = s"${pname}LeafVal"

            val wlm = Lambda(
              Symbol.spliceOwner,
              MethodType(List(nme))(_ => List(lt), _ => tryDocTpe),
              {
                case (m, List(arg: Term)) => {
                  val writenDoc: Expr[TryResult[BSONDocument]] = {
                    if (lt <:< anyValTpe) {
                      subHelper.valueWriterTree(arg, forwardExpr)
                    } else {
                      subHelper.writerTree(
                        macroVal = Ref(arg.symbol),
                        forwardExpr = forwardExpr,
                        top = false
                      )
                    }
                  }

                  writenDoc.asTerm
                }

                case (_, args) =>
                  report.errorAndAbort(
                    s"Unexpected arguments for writer lambda: $args"
                  )
              }
            )

            val ltw = TypeRepr.of(using writerType).appliedTo(lt)

            val createLeafWriter = writerCompanion
              .select(
                Symbol.newMethod(
                  writerCompanion.symbol,
                  "from",
                  writerCompanion.tpe
                )
              )
              .appliedTo(wlm)

            val vn = s"${pname}leafWriter"

            val ln = ValDef(
              Symbol.newVal(
                Symbol.spliceOwner,
                vn,
                ltw,
                Flags.Implicit,
                Symbol.noSymbol
              ),
              Some(createLeafWriter)
            )

            val imply: Term = summonInlineTerm.appliedToType(wtpe)

            Block(List(ln), imply).asExprOf[BSONWriter[_]]
          } else {
            report.errorAndAbort(s"Implicit not found for '${tpe.show}.$pname': ${classOf[BSONWriter[_]].getName}[${wtpe.typeSymbol.fullName}]")
          }
        }
      }

      def mustFlatten( // TODO: Move outside
          param: Symbol,
          pname: String,
          sig: TypeRepr,
          writer: Expr[BSONWriter[_]]
        ): Boolean = {
        if (param.annotations.exists(_.tpe =:= flattenRepr)) {
          if (writer.toString == "forwardBSONWriter") {
            report.errorAndAbort(
              s"Cannot flatten writer for '${tpe.typeSymbol.fullName}.$pname': recursive type"
            )
          }

          if (!(writer.asTerm.tpe <:< docWriterRepr.appliedTo(sig))) {
            report.errorAndAbort(s"Cannot flatten writer '${writer.asTerm.tpe.typeSymbol.fullName}' for '${tpe.typeSymbol.fullName}.$pname': doesn't conform BSONDocumentWriter")
          }

          true
        } else false
      }

      val withOk = ValDef.let(
        Symbol.spliceOwner,
        "ok",
        '{ Seq.newBuilder[BSONElement] }.asTerm
      )

      val withErr = ValDef.let(
        Symbol.spliceOwner,
        "err",
        '{ Seq.newBuilder[HandlerException] }.asTerm
      )

      val withIdents: Function3[Ref, Ref, Ref, Term] => Term = { f =>
        withMacroCfg { cfgId =>
          withOk { ok => withErr { err => f(cfgId, ok, err) } }
        }
      }

      val (tupleTpe, withTupled) = withTuple(tpe, types)

      withTupled(macroVal) { tupled =>
        val fieldMap = withFields(tupled, tupleTpe, tpeElements, debug)

        withIdents { (cfgId, ok, err) =>
          val bufOk = ok.asExprOf[MBuilder[BSONElement, Seq[BSONElement]]]
          val bufErr = err.asExprOf[MBuilder[HandlerException, Seq[
            HandlerException
          ]]]

          /*
           * @param field the name for the document field
           */
          def appendCall(
              field: Expr[String],
              bson: Expr[BSONValue]
            ): Expr[MBuilder[BSONElement, Seq[BSONElement]]] =
            '{ ${ bufOk } += BSONElement($field, $bson) }

          val values: View[Expr[Unit]] = required.map {
            case WritableProperty(param, i, sig, writerFromAnn) =>
              val pname = param.name
              val withField = fieldMap.get(pname) match {
                case Some(f) => f

                case _ =>
                  report.errorAndAbort(
                    s"Field not found: ${tpe.typeSymbol.fullName}.${pname}"
                  )
              }

              val writer = writerFromAnn getOrElse resolveWriter(pname, sig)

              val writeCall = (withField { f => tryWriteVal(writer, f).asTerm })
                .asExprOf[TryResult[BSONValue]]

              val field = fieldName(cfgId, fieldKey(param))

              def appendDocCall(
                  doc: Expr[BSONDocument]
                ): Expr[MBuilder[BSONElement, Seq[BSONElement]]] = {
                if (mustFlatten(param, pname, sig, writer)) {
                  '{
                    ${ bufOk } ++= ${ doc }.elements
                  }
                } else appendCall(field, doc)
              }

              if (writeCall.asTerm.tpe <:< successBsonVal) {
                // SafeBSONWriter directly return Success

                '{
                  ${ writeCall }.get match {
                    case doc: BSONDocument =>
                      ${ appendDocCall('{ doc }) }
                      ()

                    case bson =>
                      ${ appendCall(field, '{ bson }) }
                      ()
                  }
                }
              } else {
                '{
                  ${ writeCall } match {
                    case scala.util.Success(doc: BSONDocument) =>
                      ${ appendDocCall('{ doc }) }
                      ()

                    case scala.util.Success(bson) =>
                      ${ appendCall(field, '{ bson }) }
                      ()

                    case scala.util.Failure(err) =>
                      ${ bufErr } += HandlerException(${ Expr(pname) }, err)
                      ()
                  }
                }
              }
          } // end of required.map

          val extra: View[Expr[Unit]] = optional.collect {
            case WritableProperty(
                  param,
                  i,
                  optType @ OptionTypeParameter(sig),
                  writerFromAnn
                ) =>
              val pname = param.name
              val withField = fieldMap.get(pname) match {
                case Some(f) => f

                case _ =>
                  report.errorAndAbort(
                    s"Optional field not found: ${tpe.typeSymbol.fullName}.${pname}"
                  )
              }

              val writer = writerFromAnn getOrElse resolveWriter(pname, sig)
              val field = fieldName(cfgId, fieldKey(param))

              def appendDocCall(
                  doc: Expr[BSONDocument]
                ): Expr[MBuilder[BSONElement, Seq[BSONElement]]] = {
                if (mustFlatten(param, pname, sig, writer)) {
                  '{
                    ${ bufOk } ++= ${ doc }.elements
                  }
                } else appendCall(field, doc)
              }

              def writeCall(v: Term): Expr[Unit] = {
                val res = tryWriteVal(writer, v)

                // TODO: Flatten?
                if (res.asTerm.tpe <:< successBsonVal) {
                  // SafeBSONWriter directly return Success
                  '{
                    ${ appendCall(field, '{ ${ res }.get }) }
                    ()
                  }
                } else {
                  '{
                    ${ res }.fold[Unit](
                      { e =>
                        ${ bufErr } += HandlerException(
                          ${ Expr(pname) },
                          e
                        )
                        ()
                      },
                      { bson =>
                        ${ appendCall(field, '{ bson }) }
                        ()
                      }
                    )
                  }
                }
              }

              if (writerFromAnn.nonEmpty) {
                (withField { f => writeCall(f).asTerm }).asExprOf[Unit]
              } else {
                (withField { f =>
                  // sig => Unit
                  val ml = Lambda(
                    Symbol.spliceOwner,
                    MethodType(List("v"))(
                      _ => List(sig),
                      _ => TypeRepr.of[Unit]
                    ),
                    {
                      case (_, pv :: Nil) =>
                        writeCall(pv.asExpr.asTerm).asTerm

                      case x =>
                        report.errorAndAbort(
                          s"Unexpected Option.map lambda: $x"
                        )
                    }
                  )

                  if (param.annotations.exists(_.tpe =:= noneAsNullRepr)) {
                    val empty: Expr[Unit] = '{
                      ${ appendCall(field, '{ BSONNull }) }
                      ()
                    }

                    val mapped = f
                      .select(optType.typeSymbol.declaredMethod("map").head)
                      .appliedToType(TypeRepr.of[Unit])
                      .appliedTo(ml)

                    mapped
                      .select(
                        optType.typeSymbol.declaredMethod("getOrElse").head
                      )
                      .appliedToType(TypeRepr.of[Unit])
                      .appliedTo(empty.asTerm)

                  } else {
                    f.select(optType.typeSymbol.declaredMethod("foreach").head)
                      .appliedToType(TypeRepr.of[Unit])
                      .appliedTo(ml)
                  }
                }).asExprOf[Unit]
              }
          } // end of extra.collect

          // List[Tree] corresponding to fields appended to the buffer/builder
          def fields = values ++ extra ++ classNameTree(cfgId, tpe).map { cn =>
            '{ ${ bufOk } += ${ cn }; () }
          }

          val resExpr: Expr[TryResult[BSONDocument]] = '{
            val acc = ${ bufErr }.result()

            acc.headOption match {
              case Some(error) =>
                scala.util.Failure[BSONDocument](error suppress acc.tail)

              case _ =>
                scala.util.Success(BSONDocument(${ bufOk }.result(): _*))
            }
          }

          // => TryResult[BSONDocument]
          def writer = Block(fields.map(_.asTerm).toList, resExpr.asTerm)

          if (values.isEmpty && extra.isEmpty) {
            writer
          } else {
            Block(tupled :: Nil, writer)
          }
        }
      }.asExprOf[TryResult[BSONDocument]]
    }

    private def createSubHelper(tpe: TypeRepr) =
      new MacroHelpers[A]
        with WriterHelpers[A]
        with ImplicitResolver[A]
        with QuotesHelper {

        type Q = self.quotes.type
        val quotes = self.quotes

        val docWriterType = self.docWriterType
        val writerAnnotationTpe = self.writerAnnotationTpe
        val writerType = self.writerType
        val ignoreType = self.ignoreType
        val keyType = self.keyType
        val flattenType = self.flattenType
        val noneAsNullType = self.noneAsNullType

        val aTpeRepr = tpe
        val optsTpe = self.optsTpe
      }

    // --- Type helpers ---

    private lazy val classTagRepr = TypeRepr.of[ClassTag]

    private def classNameTree(
        macroCfgId: Ref,
        repr: TypeRepr
      ): Option[Expr[BSONElement]] = {
      val tpeFlags = aTpeRepr.typeSymbol.flags

      if (
        hasOption[
          MacroOptions.UnionType[_]
        ] || tpeFlags.is(Flags.Sealed) && tpeFlags.is(Flags.Abstract)
      ) {
        val tpe = repr.asType
        val tagTpe = classTagRepr
          .appliedTo(TypeRepr.of(using tpe))
          .asType
          .asInstanceOf[Type[ClassTag[_]]]

        val macroCfg = macroCfgId.asExprOf[MacroConfiguration]

        Expr.summon[ClassTag[?]](using tagTpe).map { cls =>
          '{
            import _root_.reactivemongo.api.bson.{ BSONElement, BSONString }

            BSONElement(
              ${ macroCfg }.discriminator,
              BSONString(${ macroCfg }.typeNaming($cls.runtimeClass))
            )
          }
        }
      } else None
    }
  }

  sealed trait MacroHelpers[A] { _i: ImplicitResolver[A] =>
    protected val quotes: Quotes

    import quotes.reflect.*

    // format: off
    protected given q: Quotes = quotes
    // format: on

    /* Type of compile-time options; See [[MacroOptions]] */
    protected def optsTpe: TypeRepr

    protected[api] def aTpeRepr: TypeRepr

    // --- Shared trees and types

    protected final lazy val optionTpe: TypeRepr = TypeRepr.of[Option[_]]

    protected final lazy val tryTpe: TypeRepr = TypeRepr.of[TryResult[_]]

    protected final lazy val anyValTpe: TypeRepr = TypeRepr.of[AnyVal]

    protected lazy val bsonDocTpe = TypeRepr.of[BSONDocument]

    protected lazy val bsonValTpe = TypeRepr.of[BSONValue]

    // format: off
    private given defaultValueAnnotationTpe: Type[DefaultValue] =
      Type.of[DefaultValue]
    // format: on

    protected final lazy val defaultValueAnnotationRepr: TypeRepr =
      TypeRepr.of[DefaultValue]

    protected def keyType: Type[Key]

    protected final lazy val keyRepr: TypeRepr = TypeRepr.of(using keyType)

    protected def ignoreType: Type[Ignore]

    protected final lazy val ignoreRepr: TypeRepr =
      TypeRepr.of(using ignoreType)

    protected final lazy val transientRepr: TypeRepr = TypeRepr.of[transient]

    // --- Macro configuration helpers ---

    protected lazy val macroCfgInit: Option[Expr[MacroConfiguration]] =
      Option.empty[Expr[MacroConfiguration]]

    protected def withMacroCfg(body: Ref => Term): Term =
      macroCfgInit.orElse(Expr.summon[MacroConfiguration]) match {
        case None =>
          report.errorAndAbort("Missing MacroConfiguration")

        case Some(expr) =>
          ValDef.let(Symbol.spliceOwner, "macroCfg", expr.asTerm)(body)
      }

    // --- Case classes helpers ---

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

    // --- Union helpers ---

    // TODO: Scala3 union type
    protected final lazy val unionTypes: Option[List[TypeRepr]] =
      parseUnionTypes orElse directKnownSubclasses

    protected def parseUnionTypes = Option.empty[List[TypeRepr]]

    private def directKnownSubclasses: Option[List[TypeRepr]] =
      aTpeRepr.classSymbol.flatMap { cls =>
        val types = cls.children.collect(Function.unlift {
          _.tree match {
            case tpd: Typed =>
              Some(tpd.tpt.tpe)

            case _ =>
              None
          }
        })

        if (types.isEmpty) None else Some(types)
      }

    // --- Type helpers ---

    @inline protected final def isOptionalType(tpe: TypeRepr): Boolean =
      tpe <:< optionTpe

    @annotation.tailrec
    protected final def leafType(t: TypeRepr): TypeRepr = t match {
      case AppliedType(_, a :: _) =>
        leafType(a)

      case _ =>
        t
    }

    /* Some(A) for Option[A] else None */
    protected object OptionTypeParameter {

      def unapply(tpe: TypeRepr): Option[TypeRepr] = {
        if (isOptionalType(tpe)) {
          tpe match {
            case AppliedType(_, args) =>
              args.headOption

            case _ =>
              None
          }
        } else None
      }
    }

    @inline protected def companion(tpe: TypeRepr): Symbol =
      tpe.typeSymbol.companionModule

    @inline protected def companionTpe(tpe: TypeRepr): TypeRepr =
      TypeRepr.typeConstructorOf(Class.forName(tpe.typeSymbol.fullName + '$'))

    private object ParamSymbolType {

      def unapply(sym: Symbol): Option[TypeRepr] = sym.tree match {
        case term: Term =>
          Some(term.tpe)

        case _ =>
          None
      }
    }

    /* TODO: Remove; Deep check for type compatibility
    @annotation.tailrec
    @SuppressWarnings(Array("ListSize"))
    private def deepConforms(types: Seq[(TypeRepr, TypeRepr)]): Boolean =
      types.headOption match {
        case Some((a, b))
            if (a.typeSymbol.paramSymss.map(_.size) != b.typeSymbol.paramSymss
              .map(_.size)) => {
          warn(s"Type parameters are not matching: $a != $b")

          false
        }

        case Some((a, b)) if a.typeSymbol.paramSymss.isEmpty =>
          if (a =:= b) deepConforms(types.tail)
          else {
            warn(s"Types are not compatible: $a != $b")

            false
          }

        case Some((a, b)) if (a.baseClasses != b.baseClasses) => {
          warn(s"Generic types are not compatible: $a != $b")

          false
        }

        case Some((AppliedType(a, aArgs), AppliedType(b, bArgs))) => {
          // for generic parameter
          if (a.typeSymbol.fullName != b.typeSymbol.fullName) {
            warn(s"Type symbols are not compatible: $a != $b")

            false
          } else {
            deepConforms(lazyZip(aArgs, bArgs) ++: types.tail)
          }
        }

        case Some((a, b)) =>
          deepConforms(types.tail)

        case _ => true
      }
     */

    // --- Context helpers ---

    @inline protected final def hasOption[O: Type]: Boolean =
      optsTpe <:< TypeRepr.of[O]

    /* Prints a compilation warning, if allowed. */
    protected final lazy val warn: String => Unit = {
      if (hasOption[MacroOptions.DisableWarnings]) { (_: String) => () }
      else {
        report.warning(_: String)
      }
    }

    /* Prints debug entry, if allowed. */
    protected final lazy val debug: String => Unit = {
      if (!hasOption[MacroOptions.Verbose]) { (_: String) => () }
      else {
        report.info(_: String)
      }
    }
  }

  sealed trait MacroTopHelpers[A] extends MacroHelpers[A] {
    _i: ImplicitResolver[A] =>

    import quotes.reflect.*

    private lazy val unionOptionTpe = TypeRepr.of[MacroOptions.UnionType]
    private lazy val unionTpe = TypeRepr.of[MacroOptions.\/]

    protected override def parseUnionTypes: Option[List[TypeRepr]] = {
      @annotation.tailrec
      def parseUnionTree(
          trees: List[TypeRepr],
          found: List[TypeRepr]
        ): List[TypeRepr] =
        trees match {
          case tree :: rem =>
            if (tree <:< unionTpe) {
              tree match {
                case AppliedType(_, List(a, b)) =>
                  parseUnionTree(a :: b :: rem, found)

                case _ =>
                  report.errorAndAbort(
                    s"Union type parameters expected: ${tree.typeSymbol.fullName}"
                  )
              }
            } else parseUnionTree(rem, tree :: found)

          case _ => found
        }

      val tree: Option[TypeRepr] = optsTpe match {
        case t @ AppliedType(_, lst) if t <:< unionOptionTpe =>
          lst.headOption

        case Refinement(parent, _, _) if parent <:< unionOptionTpe =>
          parent match {
            case AppliedType(_, args) => args.headOption
            case _                    => None
          }

        case _ => None
      }

      tree.flatMap { t =>
        val types = parseUnionTree(List(t), Nil)

        if (types.isEmpty) None else Some(types)
      }
    }
  }

  sealed trait ImplicitResolver[A] {
    protected val quotes: Quotes

    import quotes.reflect.*

    // format: off
    private given q: Quotes = quotes

    //protected given aTpe: Type[A]
    // format: on

    protected val aTpeRepr: TypeRepr

    import Macros.Placeholder

    // The placeholder type
    protected final lazy val PlaceholderType: TypeRepr =
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

    private val PlaceholderHandlerName =
      "reactivemongo.api.bson.Macros.Placeholder.Handler"

    /**
     * @param tc the type representation of the typeclass
     * @param forwardExpr the `Expr` that forward to the materialized instance itself
     */
    private class ImplicitTransformer(
        boundTypes: Map[String, TypeRepr], // TODO: Remove?
        forwardExpr: Expr[BSONWriter[A]])
        extends TreeMap {
      private val denorm = denormalized(boundTypes) _

      override def transformTree(tree: Tree)(owner: Symbol): Tree = tree match {
        case tt: TypeTree =>
          super.transformTree(TypeTree.of(using denorm(tt.tpe).asType))(owner)

        case id @ Ident(_) if (id.show == PlaceholderHandlerName) =>
          forwardExpr.asTerm

        case _ => super.transformTree(tree)(owner)
      }
    }

    private def createImplicit[M[_]](
        debug: String => Unit,
        boundTypes: Map[String, TypeRepr]
      )(tc: Type[M],
        ptype: TypeRepr,
        tx: TreeMap
      ): Option[Implicit] = {
      val pt = ptype.asType
      val (ntpe, selfRef) = normalized(boundTypes)(ptype)
      val ptpe = boundTypes.getOrElse(ntpe.typeSymbol.fullName, ntpe)

      // infers given
      val neededGivenType = TypeRepr.of[M](using tc).appliedTo(ptpe)

      val neededGiven: Option[Term] = Implicits.search(neededGivenType) match {
        case suc: ImplicitSearchSuccess => {
          if (!selfRef) {
            Some(suc.tree)
          } else {
            tx.transformTree(suc.tree)(suc.tree.symbol) match {
              case t: Term => Some(t)
              case _       => Option.empty[Term]
            }
          }
        }

        case _ =>
          Option.empty[Term]
      }

      debug(
        s"// Resolve given ${TypeRepr.of(using tc).show} for ${ntpe.show} as ${neededGivenType.show} (self? ${selfRef}) = ${neededGiven.map(_.show).mkString}"
      )

      neededGiven.map(_ -> selfRef)
    }

    protected def resolver[M[_]](
        boundTypes: Map[String, TypeRepr],
        forwardExpr: Expr[BSONWriter[A]],
        debug: String => Unit
      )(tc: Type[M]
      ): TypeRepr => Option[Implicit] = {
      val tx =
        new ImplicitTransformer(boundTypes, forwardExpr)

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

    type Implicit = (Term, Boolean)
  }

  trait QuotesHelper {
    protected type Q <: Quotes

    protected val quotes: Q

    import quotes.reflect.*

    // format: off
    private given q: Q = quotes
    // format: on

    protected lazy val summonInlineTerm: Term = Ref(
      Symbol.requiredMethod("scala.compiletime.summonInline")
    )

    private lazy val fromProductTyped =
      Ref(Symbol.requiredMethod("scala.Tuple.fromProductTyped"))

    @annotation.tailrec
    private def withElems(
        tupled: Term,
        fields: List[(Symbol, TypeRepr, Symbol)],
        prepared: List[Tuple2[String, (Ref => Term) => Term]]
      ): Map[String, (Ref => Term) => Term] = fields match {
      case (sym, t, f) :: tail => {
        val elem = ValDef.let(
          Symbol.spliceOwner,
          s"tuple${f.name}",
          Typed(tupled.select(f), Inferred(t))
        )

        withElems(tupled, tail, (sym.name -> elem) :: prepared)
      }

      case _ => prepared.reverse.toMap
    }

    /**
     * @param tupled the tupled term
     * @param tupleTpe the tuple type
     * @param decls the field declarations
     */
    def withFields(
        tupled: Term,
        tupleTpe: TypeRepr,
        decls: List[(Symbol, TypeRepr)],
        debug: String => Unit
      ): Map[String, (Term => Term) => Term] = {
      val fields = decls.zipWithIndex.flatMap {
        case ((sym, t), i) =>
          val field = tupleTpe.typeSymbol.declaredMethod(s"_${i + 1}")

          field.map { meth =>
            debug(
              s"// Field: ${sym.owner.owner.fullName}.${sym.name}, type = ${t.typeSymbol.fullName}, annotations = [${sym.annotations
                .map(_.show) mkString ", "}]"
            )

            Tuple3(sym, t, meth)
          }
      }

      withElems(tupled, fields, List.empty)
    }

    /**
     * @param tpe the type for which a `ProductOf` is provided
     * @param types the types of the elements (fields)
     *
     * @return The tuple type + `{ v: Term => { tuple: Ref => ... } }`
     * with `v` a term of type `tpe`, and `tuple` the product created from.
     */
    def withTuple(
        tpe: TypeRepr,
        types: List[TypeRepr]
      ): Tuple2[TypeRepr, Term => (Ref => Term) => Term] = {
      val productOfTpe = TypeRepr.of[ProductOf].appliedTo(tpe)

      val unappliedTupleTpe =
        TypeRepr.typeConstructorOf(Class.forName(s"scala.Tuple${types.size}"))

      val tupleTpe = unappliedTupleTpe.appliedTo(types)

      tupleTpe -> { (id: Term) =>
        val toTuple = fromProductTyped
          .appliedToType(tpe)
          .appliedTo(id)
          .appliedTo(summonInlineTerm appliedToType productOfTpe)

        ValDef.let(
          Symbol.spliceOwner,
          "tuple",
          Typed(toTuple, Inferred(tupleTpe))
        )
      }
    }

    def productOf(tpe: TypeRepr): Option[Expr[ProductOf[Any]]] = {
      val pt = TypeRepr
        .of[ProductOf]
        .appliedTo(tpe)
        .asType
        .asInstanceOf[Type[ProductOf[Any]]]

      Expr.summon[ProductOf[Any]](using pt)
    }

    /**
     * Returns the elements type for `product`.
     *
     * @param owner the type representation for `T`
     */
    def productElements[T](
        owner: TypeRepr,
        product: Expr[ProductOf[T]]
      ): List[(Symbol, TypeRepr)] = {

      @annotation.tailrec
      def elementTypes(
          tpes: List[TypeRepr],
          ts: List[TypeRepr]
        ): List[TypeRepr] = tpes.headOption match {
        case Some(AppliedType(ty, as)) if (ty <:< TypeRepr.of[Tuple]) =>
          elementTypes(as ::: tpes.tail, ts)

        case Some(t) if (t =:= TypeRepr.of[EmptyTuple]) =>
          elementTypes(tpes.tail, ts)

        case Some(t) =>
          elementTypes(tpes.tail, t :: ts)

        case _ =>
          ts.reverse
      }

      val ownerSym = owner.typeSymbol
      val paramss = ownerSym.primaryConstructor.paramSymss.flatten.map { s =>
        s.name -> s
      }.toMap

      product.asTerm.tpe match {
        case Refinement(
              Refinement(_, _, TypeBounds(t1 @ AppliedType(tycon1, _), _)),
              _,
              TypeBounds(t2 @ AppliedType(tycon2, _), _)
            )
            if (tycon1 <:< TypeRepr.of[Tuple] && tycon2 <:< TypeRepr
              .of[Tuple]) => {
          val names = elementTypes(List(t2), List.empty).collect {
            case ConstantType(StringConstant(n)) => n
          }

          lazyZip(names, elementTypes(List(t1), List.empty)).map {
            case (n, t) =>
              val csym = paramss.get(n)
              def fsym =
                Option(ownerSym declaredField n).filterNot(_ == Symbol.noSymbol)

              val psym: Symbol = csym
                .orElse(fsym)
                .orElse {
                  ownerSym.declaredMethod(n).headOption
                }
                .getOrElse(
                  Symbol.newVal(
                    ownerSym,
                    n,
                    t,
                    Flags.EmptyFlags,
                    Symbol.noSymbol
                  )
                )

              psym -> t
          }.toList
        }

        case _ =>
          List.empty[(Symbol, TypeRepr)]
      }
    }
  }

end MacroImpl
