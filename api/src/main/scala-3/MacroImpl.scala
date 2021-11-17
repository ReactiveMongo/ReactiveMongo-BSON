package reactivemongo.api.bson

import scala.util.{ Try => TryResult }

import scala.collection.View
import scala.collection.mutable.{ Builder => MBuilder }

import scala.deriving.Mirror.ProductOf
import scala.quoted.{ quotes, Expr, Quotes, Type }
import scala.reflect.ClassTag

import exceptions.HandlerException

// TODO: Enum
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

  /* TODO: Remove, directly call writerWithConfig
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
   */

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

    /* TODO
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
     */

    ???
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
    val aTpr = TypeRepr.of[A].dealias

    def throwNotDoc =
      report.errorAndAbort(s"Type ${aTpr.show} is not a document one")

    if (aTpr <:< bsonValueTpe) {
      if (aTpr <:< bsonDocTpe) {
        '{ DocumentClass.unchecked[A] }
      } else {
        throwNotDoc
      }
    } else {
      aTpr match {
        case OrType(ar, br) =>
          (ar.asType -> br.asType) match {
            case ('[a], '[b])
                if (Expr.summon[DocumentClass[a]].nonEmpty &&
                  Expr.summon[DocumentClass[b]].nonEmpty) =>
              '{ DocumentClass.unchecked[A] }

            case _ =>
              throwNotDoc
          }

        case _ =>
          val helper = new QuotesHelper {
            type Q = q.type
            val quotes = q
          }

          def isProduct: Boolean = {
            def pof = Expr.summon[ProductOf[A]]
            def conv = Expr.summon[Conversion[A, _ <: Product]]

            ((aTpr <:< TypeRepr.of[Product] || conv.nonEmpty) &&
            pof.nonEmpty)
          }

          aTpr.classSymbol match {
            case Some(tpeSym) => {
              if (
                (tpeSym.flags.is(Flags.Abstract) &&
                  tpeSym.flags.is(Flags.Sealed) &&
                  !(aTpr <:< anyValTpe)) ||
                (tpeSym.flags.is(Flags.Sealed) &&
                  tpeSym.flags.is(Flags.Trait)) ||
                isProduct
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

  private inline def withSelfWriter[T](
      f: BSONDocumentWriter[T] => (T => TryResult[BSONDocument])
    ): BSONDocumentWriter[T] = {
    new BSONDocumentWriter[T] { self =>
      val underlying = f(self)
      def writeTry(v: T) = underlying(v)
    }
  }

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

    val helper = createHelper[A, Opts](config)

    '{
      withSelfWriter {
        (forwardBSONWriter: BSONDocumentWriter[A]) =>
          { (macroVal: A) =>
            ${ helper.writeBody('{ macroVal }, '{ forwardBSONWriter }) }
          }
      }
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

  private def createHelper[A, Opts: Type](
      config: Expr[MacroConfiguration]
    )(using
      _quotes: Quotes,
      at: Type[A],
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

      val aTpe = at
      val aTpeRepr = TypeRepr.of[A](using at)

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
        macroVal: Expr[A],
        forwardExpr: Expr[BSONDocumentWriter[A]]
      ): Expr[TryResult[BSONDocument]] = {
      val writer = documentWriter(macroVal, forwardExpr, top = true)

      //TODO:debug(s"// Writer\n${writer.show}")

      writer
    }

    def valueWriterBody(
        macroVal: Expr[A],
        forwardExpr: Expr[BSONWriter[_ /*TODO*/ ]]
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

    private final lazy val successBsonDoc =
      TypeRepr.of[scala.util.Success[BSONDocument]]

    // --- Writer builders ---

    private lazy val fieldName: Function2[Expr[MacroConfiguration], String, Expr[String]] = {
      (cfg, nme) => '{ ${ cfg }.fieldNaming(${ Expr(nme) }) }
    }

    /**
     * @param macroVal the term to be written
     */
    protected final def documentWriter(
        macroVal: Expr[A],
        forwardExpr: Expr[BSONDocumentWriter[A]],
        top: Boolean
      ): Expr[TryResult[BSONDocument]] = withMacroCfg { config =>
      val discriminatedUnion: Option[Expr[TryResult[BSONDocument]]] =
        (unionTypes.map { types =>
          if (types.isEmpty) {
            report.errorAndAbort(
              s"Type ${prettyType(aTpeRepr)} is not a supported union"
            )
          }

          val resolve = resolver[BSONDocumentWriter, A](
            Map.empty,
            forwardExpr,
            debug
          )(docWriterType)

          val cases = types.zipWithIndex.map { (tpr, i) =>
            tpr.asType match {
              case tt @ '[at] =>
                writeDiscriminatedCase[at](
                  macroVal,
                  '{ ${ forwardExpr }.asInstanceOf[BSONWriter[at]] },
                  top,
                  config,
                  resolve,
                  tpr,
                  name = s"macroTpe${i}"
                )(using tt)
            }
          }

          Match(macroVal.asTerm, cases).asExprOf[TryResult[BSONDocument]]
        })

      val discriminatedSubTypes: Option[Expr[TryResult[BSONDocument]]] =
        subTypes.map { types =>
          val resolve = resolver[BSONDocumentWriter, A](
            Map.empty,
            forwardExpr,
            debug
          )(docWriterType)

          type Subtype[U <: A] = U

          val cases = types.zipWithIndex.map { (tpr, i) =>
            tpr.asType match {
              case tt @ '[Subtype[at]] =>
                writeDiscriminatedCase[at](
                  macroVal,
                  '{ ${ forwardExpr }.narrow[at] },
                  top,
                  config,
                  resolve,
                  tpr,
                  name = s"macroTpe${i}"
                )(using tt)
            }
          }

          def fallback = CaseDef(
            Wildcard(),
            None,
            '{
              scala.util.Failure(
                reactivemongo.api.bson.exceptions.ValueDoesNotMatchException(${
                  macroVal
                }.toString)
              )
            }.asTerm
          )

          Match(macroVal.asTerm, cases :+ fallback)
            .asExprOf[TryResult[BSONDocument]]
        }

      discriminatedUnion orElse discriminatedSubTypes getOrElse {
        writeBodyConstruct(
          config,
          macroVal,
          forwardExpr,
          top
        )(using aTpe)
      }
    }

    /**
     * @tparam Constraint the type constraint for the discriminated types
     */
    private def writeDiscriminatedCase[T](
        macroVal: Expr[A],
        forwardBSONWriter: Expr[BSONWriter[T]],
        top: Boolean,
        config: Expr[MacroConfiguration],
        resolve: TypeRepr => Option[Implicit],
        tpr: TypeRepr,
        name: String
      )(using
        tpe: Type[T]
      ): CaseDef = {

      val subHelper = createSubHelper[T](tpe, tpr)

      val bind =
        Symbol.newBind(
          Symbol.spliceOwner,
          name,
          Flags.Case,
          tpr
        )

      val be = Ref(bind).asExprOf[T]

      val body: Expr[TryResult[BSONDocument]] =
        givenWriter[T](config, be, tpe)(resolve).getOrElse {
          if (hasOption[MacroOptions.AutomaticMaterialization]) {
            // No existing implicit, but can fallback to automatic mat
            subHelper.writeBodyConstruct[T](
              config,
              be,
              forwardBSONWriter,
              top
            )(using tpe)
          } else {
            report.errorAndAbort(s"Instance not found: ${classOf[BSONWriter[_]].getName}[${prettyType(tpr)}]")
          }
        }

      CaseDef(
        Bind(bind, Typed(Wildcard(), Inferred(tpr))),
        guard = None,
        rhs = body.asTerm
      )
    }

    /**
     * Writes the value using a writer resolved from the implicit scope.
     *
     * @param macroVal the value to be written
     * @param tpe the type of the `macroVal`
     */
    private def givenWriter[T](
        config: Expr[MacroConfiguration],
        macroVal: Expr[T],
        tpe: Type[T]
      )(r: TypeRepr => Option[Implicit]
      ): Option[Expr[TryResult[BSONDocument]]] = {
      given typ: Type[T] = tpe
      val tpr = TypeRepr.of[T](using tpe)

      r(tpr).map { (writer, _) =>
        def doc: Expr[TryResult[BSONDocument]] = '{
          ${ writer.asExprOf[BSONDocumentWriter[T]] }.writeTry(${
            macroVal.asExprOf[T]
          })
        }

        '{
          def discriminator = ${ discriminatorElement[T](config) }
          ${ doc }.map { _ ++ discriminator }
        }
      }
    }

    def valueWriterTree(
        macroVal: Expr[A],
        forwardExpr: Expr[BSONWriter[_ /*TODO*/ ]]
      ): Expr[TryResult[BSONDocument]] = {
      /* TODO
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
                  report.errorAndAbort(s"Instance not found: ${classOf[BSONWriter[_]].getName}[${prettyType(typ)}]")
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
       */

      ???
    }

    /*
     * @param top $topParam
     */
    @inline private def writeBodyConstruct[T](
        macroCfg: Expr[MacroConfiguration],
        macroVal: Expr[T],
        forwardExpr: Expr[BSONWriter[T]],
        top: Boolean
      )(using
        tpe: Type[T]
      ): Expr[TryResult[BSONDocument]] = {
      val repr = TypeRepr.of[T](using tpe)

      if (repr.isSingleton) singletonWriter[T](macroCfg)
      else {
        type IsProduct[U <: Product] = U

        tpe match {
          case '[IsProduct[t]] =>
            Expr.summon[ProductOf[t]] match {
              case Some(pof) =>
                productWriter[t, t](
                  macroVal.asExprOf[t],
                  forwardExpr.asExprOf[BSONWriter[t]],
                  top,
                  '{ identity[t] },
                  pof
                )

              case _ =>
                report.errorAndAbort(
                  s"Instance not found: ${aTpeRepr} 'ProductOf[${prettyType(repr)}]'"
                )
            }

          case '[t] =>
            Expr.summon[Conversion[t, _ <: Product]] match {
              case Some('{ $conv: Conversion[t, IsProduct[p]] }) =>
                Expr.summon[ProductOf[t]] match {
                  case Some(pof) =>
                    productWriter[t, p](
                      macroVal.asExprOf[t],
                      forwardExpr.asExprOf[BSONWriter[t]],
                      top,
                      conv,
                      pof
                    )

                  case _ =>
                    report.errorAndAbort(
                      s"Instance not found: 'ProductOf[${prettyType(repr)}]'"
                    )
                }

              case _ =>
                report.errorAndAbort(s"Instance not found: 'Conversion[${prettyType(repr)}, _ <: Product]'")
            }

          case _ =>
            report.errorAndAbort(s"Invalid product type: ${prettyType(repr)}")
        }
      }
    }

    private def singletonWriter[T](
        macroCfg: Expr[MacroConfiguration]
      )(using
        tpe: Type[T]
      ): Expr[TryResult[BSONDocument]] =
      '{
        def discriminator = ${ discriminatorElement[T](macroCfg) }
        scala.util.Success(BSONDocument(discriminator))
      }

    private type WritableProperty =
      Tuple4[Symbol, Int, TypeRepr, Option[Expr[BSONWriter[_]]]]

    private object WritableProperty {

      def apply(
          symbol: Symbol,
          index: Int,
          tpr: TypeRepr,
          writerFromAnnotation: Option[Expr[BSONWriter[_]]]
        ) =
        Tuple4(symbol, index, tpr, writerFromAnnotation)

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
     * @param macroVal the value to be written
     * @param toProduct the function to convert the input value as product `U`
     * @param tpe the value type
     */
    private def productWriter[T, U <: Product](
        macroVal: Expr[T],
        forwardExpr: Expr[BSONWriter[T]],
        top: Boolean,
        toProduct: Expr[T => U],
        pof: Expr[ProductOf[T]]
      )(using
        tpe: Type[T],
        ptpe: Type[U]
      ): Expr[TryResult[BSONDocument]] = {
      val tpr = TypeRepr.of[T](using tpe)
      val tprElements = productElements[T, U](tpr, pof)
      val types = tprElements.map(_._2)

      val resolve =
        resolver[BSONWriter, T](Map.empty, forwardExpr, debug)(writerType)

      val (optional, required) = tprElements.zipWithIndex.view.filterNot {
        case ((sym, _), _) => ignoreField(sym)
      }.map {
        case ((sym, pt), i) =>
          val writerAnnTpe = writerAnnotationRepr.appliedTo(pt)

          val writerAnns = sym.annotations.flatMap {
            case ann if (ann.tpe <:< writerAnyAnnotationRepr) => {
              if (!(ann.tpe <:< writerAnnTpe)) {
                report.errorAndAbort(s"Invalid annotation @Writer(${ann.show}) for '${prettyType(tpr)}.${sym.name}': Writer[${prettyType(pt)}]")
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
                warn(s"Exactly one @Writer must be provided for each property; Ignoring invalid annotations for '${prettyType(tpr)}.${sym.name}'")
              }

              Some(w)
            }

            case _ =>
              None
          }

          WritableProperty(sym, i, pt, writerFromAnn)
      }.partition {
        case WritableProperty(_, _, t, writerFromAnn) =>
          writerFromAnn.isEmpty && isOptionalType(t)
      }

      type ElementAcc = MBuilder[BSONElement, Seq[BSONElement]]
      type ExceptionAcc = MBuilder[HandlerException, Seq[HandlerException]]

      def withIdents[U: Type](
          f: Function3[Expr[MacroConfiguration], /* ok */ Expr[
            ElementAcc
          ], /* err */ Expr[ExceptionAcc], Expr[U]]
        ): Expr[U] = withMacroCfg { config =>
        '{
          val ok = Seq.newBuilder[BSONElement]
          val err = Seq.newBuilder[HandlerException]

          ${ f(config, '{ ok }, '{ err }) }
        }
      }

      val (tupleTpe, withTupled) =
        withTuple[T, U, TryResult[BSONDocument]](tpr, toProduct, types)

      withTupled(macroVal) { tupled =>
        val fieldMap = withFields(tupled, tupleTpe, tprElements, debug)

        (withIdents[TryResult[BSONDocument]] { (config, bufOk, bufErr) =>
          /*
           * @param field the name for the document field
           */
          def appendCall(
              field: Expr[String],
              bson: Expr[BSONValue]
            ): Expr[ElementAcc] =
            '{ ${ bufOk } += BSONElement($field, $bson) }

          /*
           * @param field the name for the document field
           */
          def appendDocCall[V](
              doc: Expr[BSONDocument],
              field: Expr[String],
              param: Symbol,
              pt: TypeRepr,
              writer: Expr[BSONWriter[V]]
            ): Expr[ElementAcc] = {
            if (mustFlatten[V](tpr, param, param.name, pt, writer)) {
              '{
                ${ bufOk } ++= ${ doc }.elements
              }
            } else appendCall(field, doc)
          }

          def writeCall[V: Type](
              field: Expr[String],
              param: Symbol,
              pt: TypeRepr,
              writer: Expr[BSONWriter[V]],
              v: Expr[V]
            ): Expr[Unit] = {
            lazy val res = '{ ${ writer }.writeTry(${ v }) }

            if (res.asTerm.tpe <:< successBsonDoc) {
              // SafeBSONWriter directly return Success document

              val docRes =
                res.asExprOf[scala.util.Success[BSONDocument]]

              '{
                val doc: BSONDocument = ${ docRes }.get
                ${ appendDocCall('{ doc }, field, param, pt, writer) }
                ()
              }
            } else if (res.asTerm.tpe <:< successBsonVal) {
              // SafeBSONWriter directly return Success value

              '{
                ${ res }.get match {
                  case doc: BSONDocument =>
                    ${ appendDocCall('{ doc }, field, param, pt, writer) }
                    ()

                  case bson =>
                    ${ appendCall(field, '{ bson }) }
                    ()
                }
              }
            } else {
              '{
                ${ res }.fold[Unit](
                  { e =>
                    ${ bufErr } += HandlerException(
                      ${ Expr(param.name) },
                      e
                    )
                    ()
                  },
                  {
                    case doc: BSONDocument =>
                      ${ appendDocCall('{ doc }, field, param, pt, writer) }
                      ()

                    case bson =>
                      ${ appendCall(field, '{ bson }) }
                      ()
                  }
                )
              }
            }
          }

          val values: View[Expr[Unit]] = required.map {
            case WritableProperty(param, i, pt, writerFromAnn) =>
              val pname = param.name

              val withField = fieldMap.get(pname) match {
                case Some(f) => f

                case _ =>
                  report.errorAndAbort(
                    s"Field not found: ${prettyType(tpr)}.${pname}"
                  )
              }

              val field = fieldName(config, fieldKey(param))

              pt.asType match {
                case pTpe @ '[p] =>
                  val writer: Expr[BSONWriter[p]] = writerFromAnn.fold(
                    resolveWriter[p, T](forwardExpr, pname, pt, resolve)(
                      using pTpe
                    )
                  )(_.asExprOf[BSONWriter[p]])

                  (withField { f =>
                    writeCall[p](
                      field,
                      param,
                      pt,
                      writer,
                      f.asExprOf[p]
                    ).asTerm
                  }).asExprOf[Unit]
              }
          } // end of required.map

          val extra: View[Expr[Unit]] = optional.collect {
            case WritableProperty(
                  param,
                  i,
                  optType @ OptionTypeParameter(pt),
                  None
                ) =>
              val pname = param.name

              val withField = fieldMap.get(pname) match {
                case Some(f) => f

                case _ =>
                  report.errorAndAbort(
                    s"Optional field not found: ${prettyType(tpr)}.${pname}"
                  )
              }

              pt.asType match {
                case pTpe @ '[p] =>
                  val writer: Expr[BSONWriter[p]] =
                    resolveWriter[p, T](forwardExpr, pname, pt, resolve)(
                      using pTpe
                    )

                  val field = fieldName(config, fieldKey(param))

                  val write = writeCall[p](field, param, pt, writer, _)

                  (withField { f =>
                    // pt => Unit
                    val ml = '{ (v: p) => ${ write('{ v }) } }

                    if (param.annotations.exists(_.tpe =:= noneAsNullRepr)) {
                      val empty: Expr[Unit] = '{
                        ${ appendCall(field, '{ BSONNull }) }
                        ()
                      }

                      '{
                        ${ f.asExprOf[Option[p]] }.fold(${ empty })(${ ml })
                      }.asTerm
                    } else {
                      '{ ${ f.asExprOf[Option[p]] }.foreach(${ ml }) }.asTerm
                    }
                  }).asExprOf[Unit]
              }
          } // end of extra.collect

          // List[Tree] corresponding to fields appended to the buffer/builder
          def fields = values ++ extra

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
          def writer =
            Block(fields.map(_.asTerm).toList, resExpr.asTerm)

          if (values.isEmpty && extra.isEmpty) {
            // TODO: Remove error as no field at all?
            //writer.asExprOf[TryResult[BSONDocument]]

            report.errorAndAbort(
              s"No field found: class ${prettyType(TypeRepr.of[T](using tpe))}"
            )
          } else {
            Block(tupled.asTerm :: Nil, writer)
              .asExprOf[TryResult[BSONDocument]]
          }
        })
      }
    }

    /**
     * @param tpr the representation for the type declaring `param`
     * @param ptpr the type representation for the type T of `param`
     */
    private def mustFlatten[T](
        tpr: TypeRepr,
        param: Symbol,
        pname: String,
        ptpr: TypeRepr,
        writer: Expr[BSONWriter[T]]
      ): Boolean = {
      if (param.annotations.exists(_.tpe =:= flattenRepr)) {
        if (writer.asTerm.symbol.name.toString == "forwardBSONWriter") {
          report.errorAndAbort(
            s"Cannot flatten writer for '${prettyType(tpr)}.$pname': recursive type"
          )
        }

        if (!(writer.asTerm.tpe <:< docWriterRepr.appliedTo(ptpr))) {
          report.errorAndAbort(s"Cannot flatten writer '${prettyType(writer.asTerm.tpe)}' for '${prettyType(tpr)}.$pname': doesn't conform BSONDocumentWriter")
        }

        true
      } else false
    }

    /**
     * @tparam T the field type
     * @tparam U the parent type (class)
     *
     * @param pname the parameter/field name
     * @param wtpe the representation of `T` type (related to `tpe`)
     * @param tpe the type parameter for the writer to be resolved
     */
    private def resolveWriter[T, U](
        forwardExpr: Expr[BSONWriter[U]],
        pname: String,
        tpr: TypeRepr,
        resolve: TypeRepr => Option[Implicit]
      )(using
        tpe: Type[T]
      ): Expr[BSONWriter[T]] = {
      resolve(tpr) match {
        case Some((writer, _)) =>
          writer.asExprOf[BSONWriter[T]]

        case None => {
          if (!hasOption[MacroOptions.AutomaticMaterialization]) {
            report.errorAndAbort(s"No implicit found for '${prettyType(aTpeRepr)}.$pname': ${classOf[BSONWriter[_]].getName}[${prettyType(tpr)}]")
          } else {
            val lt = leafType(tpr)

            warn(s"Materializing ${classOf[BSONWriter[_]].getName}[${lt}] for '${tpe}.$pname': it's recommended to declare it explicitly")

            val nme = s"${pname}LeafVal"

            lt.asType match {
              case tt @ '[at] =>
                val subHelper = createSubHelper[at](tt, lt)

                val wlm = { // Expr[at => TryResult[_ <: BSONValue]]
                  if (lt <:< anyValTpe) {
                    '{ (arg: at) =>
                      ${ subHelper.valueWriterTree('{ arg }, forwardExpr) }
                    }
                  } else {
                    '{
                      val subWriter: BSONDocumentWriter[at] =
                        withSelfWriter {
                          (forwardBSONWriter: BSONDocumentWriter[at]) =>
                            { (macroVal: at) =>
                              ${
                                subHelper.documentWriter(
                                  macroVal = '{ macroVal },
                                  forwardExpr = '{ forwardBSONWriter },
                                  top = false
                                )
                              }
                            }
                        }

                      subWriter.writeTry(_: at)
                    }
                  }
                }

                '{
                  given leafWriter: BSONWriter[at] =
                    BSONWriter.from[at](${ wlm })

                  scala.compiletime.summonInline[BSONWriter[T]]
                }
            }
          }
        }
      }
    }

    private def createSubHelper[T](tt: Type[T], tpr: TypeRepr) =
      new MacroHelpers[T]
        with WriterHelpers[T]
        with ImplicitResolver[T]
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

        val aTpe = tt
        val aTpeRepr = tpr
        val optsTpe = self.optsTpe
      }

    // --- Type helpers ---

    private def discriminatorElement[T](
        macroCfg: Expr[MacroConfiguration]
      )(using
        tpe: Type[T]
      ): Expr[BSONElement] =
      Expr.summon[ClassTag[T]] match {
        case Some(cls) =>
          '{
            BSONElement(
              ${ macroCfg }.discriminator,
              BSONString(${ macroCfg }.typeNaming($cls.runtimeClass))
            )
          }

        case _ =>
          report.errorAndAbort(
            s"Fails to resolve ClassTag[${prettyType(TypeRepr.of(using tpe))}]"
          )
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

    protected[api] def aTpe: Type[A]
    protected[api] def aTpeRepr: TypeRepr
    protected given aType: Type[A] = aTpe

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

    protected def withMacroCfg[T](
        body: Expr[MacroConfiguration] => Expr[T]
      ): Expr[T] = macroCfgInit.orElse(Expr.summon[MacroConfiguration]) match {
      case None =>
        report.errorAndAbort("Missing MacroConfiguration")

      case Some(expr) => body(expr)
    }

    // --- Case classes helpers ---

    protected final def isClass(tpr: TypeRepr): Boolean = {
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

    // --- Union helpers ---

    protected final lazy val subTypes: Option[List[TypeRepr]] =
      parseSubTypes orElse directKnownSubclasses

    protected def unionTypes = Option.empty[List[TypeRepr]]

    protected def parseSubTypes = Option.empty[List[TypeRepr]]

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
          tpr match {
            case AppliedType(_, args) =>
              args.headOption

            case _ =>
              None
          }
        } else None
      }
    }

    @inline protected def companion(tpr: TypeRepr): Symbol =
      tpr.typeSymbol.companionModule

    @inline protected def companionTpe(tpr: TypeRepr): TypeRepr =
      TypeRepr.typeConstructorOf(Class.forName(tpr.typeSymbol.fullName + '$'))

    private object ParamSymbolType {

      def unapply(sym: Symbol): Option[TypeRepr] = sym.tree match {
        case term: Term =>
          Some(term.tpe)

        case _ =>
          None
      }
    }

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

    protected override def parseSubTypes: Option[List[TypeRepr]] = {
      @annotation.tailrec
      def parseTypes(
          types: List[TypeRepr],
          found: List[TypeRepr]
        ): List[TypeRepr] =
        types match {
          case typ :: rem =>
            if (typ <:< unionTpe) {
              typ match {
                case AppliedType(_, List(a, b)) =>
                  parseTypes(a :: b :: rem, found)

                case _ =>
                  report.errorAndAbort(
                    s"Union type parameters expected: ${prettyType(typ)}"
                  )
              }
            } else parseTypes(rem, typ :: found)

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
        val types = parseTypes(List(t), Nil)

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
    private class ImplicitTransformer[T](
        boundTypes: Map[String, TypeRepr], // TODO: Remove?
        forwardExpr: Expr[T])
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

    protected def resolver[M[_], T](
        boundTypes: Map[String, TypeRepr], // TODO: Remove
        forwardExpr: Expr[M[T]],
        debug: String => Unit
      )(tc: Type[M]
      ): TypeRepr => Option[Implicit] = {
      val tx =
        new ImplicitTransformer[M[T]](boundTypes, forwardExpr)

      createImplicit(debug, boundTypes)(tc, _: TypeRepr, tx)
    }

    // To print the implicit types in the compiler messages
    protected def prettyType(t: TypeRepr): String = t match {
      case _ if (t <:< TypeRepr.of[EmptyTuple]) =>
        "EmptyTuple"

      case AppliedType(ty, a :: b :: Nil) if (ty <:< TypeRepr.of[*:]) =>
        s"${prettyType(a)} *: ${prettyType(b)}"

      case AppliedType(_, args) =>
        t.typeSymbol.fullName + args
          .map(_.typeSymbol.fullName)
          .mkString("[", ", ", "]")

      case OrType(a, b) =>
        s"${prettyType(a)} | ${prettyType(b)}"

      case _ =>
        t.typeSymbol.fullName.replaceAll("\\$", "")
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

    @annotation.tailrec
    private def withElems[U <: Product](
        tupled: Expr[U],
        fields: List[(Symbol, TypeRepr, Symbol)],
        prepared: List[Tuple2[String, (Ref => Term) => Term]]
      ): Map[String, (Ref => Term) => Term] = fields match {
      case (sym, t, f) :: tail => {
        val elem = ValDef.let(
          Symbol.spliceOwner,
          s"tuple${f.name}",
          Typed(tupled.asTerm.select(f), Inferred(t))
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
    def withFields[U <: Product](
        tupled: Expr[U],
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

      withElems[U](tupled, fields, List.empty)
    }

    /**
     * @tparam T the class type
     * @tparam U the type of the product corresponding to class `T`
     * @tparam R the result type (from the field operation)
     *
     * @param tpe the type for which a `ProductOf` is provided
     * @param toProduct the function to convert the input value as product `U`
     * @param types the types of the elements (fields)
     *
     * @return The tuple type + `{ v: Term => { tuple: Ref => ... } }`
     * with `v` a term of type `tpe`, and `tuple` the product created from.
     */
    def withTuple[T, U <: Product, R: Type](
        tpr: TypeRepr,
        toProduct: Expr[T => U],
        types: List[TypeRepr]
      )(using
        tpe: Type[T],
        ptpe: Type[U]
      ): Tuple2[TypeRepr, Expr[T] => (Expr[U] => Expr[R]) => Expr[R]] = {
      val unappliedTupleTpr =
        TypeRepr.typeConstructorOf(Class.forName(s"scala.Tuple${types.size}"))

      val tupleTpr = unappliedTupleTpr.appliedTo(types)

      tupleTpr -> {
        (in: Expr[T]) =>
          { (f: (Expr[U] => Expr[R])) =>
            '{
              val tuple: U = ${ toProduct }($in)
              ${ f('{ tuple }) }
            }
          }
      }
    }

    /**
     * Returns the elements type for `product`.
     *
     * @param owner the type representation for `T`
     */
    def productElements[T, U <: Product](
        owner: TypeRepr,
        pof: Expr[ProductOf[T]]
      ): List[(Symbol, TypeRepr)] = { // TODO: Remove Symbol?

      @annotation.tailrec
      def elementTypes(
          max: Int,
          tpes: List[TypeRepr],
          ts: List[TypeRepr]
        ): List[TypeRepr] = tpes.headOption match {
        case Some(AppliedType(ty, a :: b :: Nil))
            if (ty <:< TypeRepr.of[*:] && max > 0) =>
          elementTypes(max - 1, b :: tpes.tail, a :: ts)

        case Some(AppliedType(ty, as))
            if (ty <:< TypeRepr.of[Tuple] && as.size <= max) =>
          elementTypes(max - as.size, as ::: tpes.tail, ts)

        case Some(t) if (t =:= TypeRepr.of[EmptyTuple]) =>
          elementTypes(max, tpes.tail, ts)

        case Some(t) =>
          elementTypes(max, tpes.tail, t :: ts)

        case _ =>
          ts.reverse
      }

      val ownerSym = owner.typeSymbol
      val paramss = ownerSym.primaryConstructor.paramSymss.flatten.map { s =>
        s.name -> s
      }.toMap

      pof.asTerm.tpe match {
        case Refinement(
              Refinement(_, _, TypeBounds(t1 @ AppliedType(tycon1, _), _)),
              _,
              TypeBounds(t2 @ AppliedType(tycon2, _), _)
            )
            if (tycon1 <:< TypeRepr.of[Product] && tycon2 <:< TypeRepr
              .of[Product]) => {
          val names = elementTypes(Int.MaxValue, List(t2), List.empty).collect {
            case ConstantType(StringConstant(n)) => n
          }

          lazyZip(names, elementTypes(names.size, List(t1), List.empty)).map {
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
