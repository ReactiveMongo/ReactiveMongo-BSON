package reactivemongo.api.bson

import scala.util.{
  Failure => TryFailure,
  Success => TrySuccess,
  Try => TryResult
}

import scala.collection.mutable.{ Builder => MBuilder }

import scala.deriving.Mirror.ProductOf
import scala.quoted.{ Expr, Quotes, Type }
import scala.reflect.ClassTag

import exceptions.{
  HandlerException,
  TypeDoesNotMatchException,
  ValueDoesNotMatchException
}

private[api] object MacroImpl:
  import Macros.Annotations, Annotations.{
    DefaultValue,
    Ignore,
    Key,
    Writer,
    Flatten,
    NoneAsNull,
    Reader
  }

  def reader[A: Type, Opts <: MacroOptions: Type](
      using
      Quotes,
      Type[DefaultValue],
      Type[Reader],
      Type[BSONReader],
      Type[Ignore],
      Type[Key],
      Type[Flatten],
      Type[MacroOptions.DisableWarnings],
      Type[MacroOptions.Verbose]
    ): Expr[BSONDocumentReader[A]] =
    readerWithConfig[A, Opts](implicitOptionsConfig)

  private inline def withSelfDocReader[T](
      f: BSONDocumentReader[T] => (BSONDocument => TryResult[T])
    ): BSONDocumentReader[T] = {
    new BSONDocumentReader[T] { self =>
      lazy val underlying = f(self)
      def readDocument(doc: BSONDocument) = underlying(doc)
    }
  }

  def opaqueAliasReader[A: Type, Opts <: MacroOptions: Type](
      using
      q: Quotes
    ): Expr[BSONReader[A]] = opaqueAliasHelper[A, BSONReader, Opts](strExpr = {
    Expr.summon[BSONReader[String]] match {
      case Some(reader) =>
        reader.asExprOf[BSONReader[A]]

      case _ =>
        q.reflect.report
          .errorAndAbort("Instance not found: BSONReader[String]]")
    }
  })

  def anyValReader[A <: AnyVal: Type, Opts <: MacroOptions: Type](
      using
      q: Quotes
    ): Expr[BSONReader[A]] = {
    import q.reflect.*

    type O = Opts

    val helper = new OptionSupport with MacroLogging {
      type Q = q.type

      val quotes = q

      type Opts = O
      val optsTpe = Type.of[O]
      val optsTpr = TypeRepr.of(using optsTpe).dealias

      val disableWarningsTpe = Type.of[MacroOptions.DisableWarnings]
      val verboseTpe = Type.of[MacroOptions.Verbose]
    }

    val aTpr = TypeRepr.of[A]

    val ctor = aTpr.typeSymbol.primaryConstructor

    def body(macroVal: Expr[BSONValue]): Expr[TryResult[A]] = {
      val reader = ctor.paramSymss match {
        case List(v: Symbol) :: Nil =>
          v.tree match {
            case vd: ValDef => {
              val tpr = vd.tpt.tpe

              tpr.asType match {
                case vtpe @ '[t] =>
                  Expr.summon[BSONReader[t]] match {
                    case Some(reader) => {
                      def mapf(in: Expr[t]): Expr[A] =
                        New(Inferred(aTpr))
                          .select(ctor)
                          .appliedTo(in.asTerm)
                          .asExprOf[A]

                      '{
                        ${ reader }.readTry($macroVal).map { inner =>
                          ${ mapf('inner) }
                        }
                      }
                    }

                    case None =>
                      report.errorAndAbort(s"Instance not found: ${classOf[BSONReader[_]].getName}[${tpr.typeSymbol.fullName}]")
                  }
              }
            }

            case _ =>
              report.errorAndAbort(
                s"Constructor parameter expected, found: ${v}"
              )
          }

        case _ =>
          report.errorAndAbort(
            s"Cannot resolve value reader for '${aTpr.typeSymbol.name}'"
          )

      }

      helper.debug(s"// Value reader\n${reader.show}")

      reader
    }

    '{
      BSONReader.from[A] { (macroVal: BSONValue) => ${ body('{ macroVal }) } }
    }
  }

  def writer[A: Type, Opts <: MacroOptions: Type](
      using
      Quotes,
      Type[Writer],
      Type[Ignore],
      Type[Key],
      Type[Flatten],
      Type[NoneAsNull]
    ): Expr[BSONDocumentWriter[A]] =
    writerWithConfig[A, Opts](implicitOptionsConfig)

  private inline def withSelfValWriter[T](
      f: BSONWriter[T] => (T => TryResult[BSONValue])
    ): BSONWriter[T] = new BSONWriter[T] { self =>
    lazy val underlying = f(self)
    def writeTry(v: T) = underlying(v)
  }

  def opaqueAliasWriter[A: Type, Opts <: MacroOptions: Type](
      using
      q: Quotes
    ): Expr[BSONWriter[A]] = opaqueAliasHelper[A, BSONWriter, Opts](strExpr = {
    Expr.summon[BSONWriter[String]] match {
      case Some(writer) =>
        writer.asExprOf[BSONWriter[A]]

      case _ =>
        q.reflect.report
          .errorAndAbort("Instance not found: BSONWriter[String]]")
    }
  })

  def anyValWriter[A <: AnyVal: Type, Opts <: MacroOptions: Type](
      using
      q: Quotes
    ): Expr[BSONWriter[A]] = {
    import q.reflect.*

    type O = Opts

    val helper = new OptionSupport with MacroLogging {
      type Q = q.type

      val quotes = q

      type Opts = O
      val optsTpe = Type.of[O]
      val optsTpr = TypeRepr.of(using optsTpe).dealias

      val disableWarningsTpe = Type.of[MacroOptions.DisableWarnings]
      val verboseTpe = Type.of[MacroOptions.Verbose]
    }

    val aTpr = TypeRepr.of[A]

    def body(macroVal: Expr[A]): Expr[TryResult[BSONValue]] = {
      val writer = aTpr.typeSymbol.primaryConstructor.paramSymss match {
        case List(v: Symbol) :: Nil =>
          v.tree match {
            case vd: ValDef => {
              val tpr = vd.tpt.tpe

              tpr.asType match {
                case vtpe @ '[t] =>
                  Expr.summon[BSONWriter[t]] match {
                    case Some(writer) => {
                      val term = macroVal.asTerm
                      val inner = term
                        .select(term.symbol.fieldMember(v.name))
                        .asExprOf[t](using vtpe)

                      '{ ${ writer }.writeTry($inner) }
                    }

                    case None =>
                      report.errorAndAbort(s"Instance not found: ${classOf[BSONWriter[_]].getName}[${tpr.typeSymbol.fullName}]")
                  }
              }
            }

            case _ =>
              report.errorAndAbort(
                s"Constructor parameter expected, found: ${v}"
              )
          }

        case _ =>
          report.errorAndAbort(
            s"Cannot resolve value writer for '${aTpr.typeSymbol.name}'"
          )

      }

      helper.debug(s"// Value writer\n${writer.show}")

      writer
    }

    '{
      BSONWriter.from[A] { (macroVal: A) => ${ body('{ macroVal }) } }
    }
  }

  def handler[A: Type, Opts <: MacroOptions: Type](
      using
      Quotes,
      Type[DefaultValue],
      Type[Reader],
      Type[Writer],
      Type[Ignore],
      Type[Key],
      Type[Flatten],
      Type[NoneAsNull],
      Type[MacroOptions.DisableWarnings],
      Type[MacroOptions.Verbose]
    ): Expr[BSONDocumentHandler[A]] =
    handlerWithConfig[A, Opts](implicitOptionsConfig)

  def opaqueAliasHandler[A: Type, Opts <: MacroOptions: Type](
      using
      q: Quotes
    ): Expr[BSONHandler[A]] = opaqueAliasHelper[A, BSONHandler, Opts](
    strExpr = {
      Expr.summon[BSONHandler[String]] match {
        case Some(handler) =>
          handler.asExprOf[BSONHandler[A]]

        case _ =>
          q.reflect.report
            .errorAndAbort("Instance not found: BSONHandler[String]]")
      }
    }
  )

  def anyValHandler[A <: AnyVal: Type, Opts <: MacroOptions: Type](
      using
      q: Quotes
    ): Expr[BSONHandler[A]] = {
    val writer = anyValWriter[A, Opts]
    val reader = anyValReader[A, Opts]

    '{ BSONHandler.provided[A](${ reader }, ${ writer }) }
  }

  def documentClass[A: Type](
      using
      q: Quotes
    ): Expr[DocumentClass[A]] = {
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
      q: Quotes
    ): Expr[A] = {
    if (
      !sys.props.get("reactivemongo.api.migrationRequired.nonFatal").exists {
        v => v.toLowerCase == "true" || v.toLowerCase == "yes"
      }
    ) {
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

  private def opaqueAliasHelper[
      A: Type,
      M[_]: Type,
      Opts <: MacroOptions: Type
    ](strExpr: => Expr[M[A]]
    )(using
      q: Quotes
    ): Expr[M[A]] = {
    import q.reflect.*

    type IsAnyVal[T <: AnyVal] = T

    type IsString[T <: String] = T

    TypeRepr.of[A] match {
      case ref: TypeRef if ref.isOpaqueAlias => {
        val underlyingTpr = ref.translucentSuperType

        underlyingTpr.asType match {
          case '[IsString[t]] =>
            strExpr

          case tpe @ '[IsAnyVal[t]] =>
            Expr.summon[M[t]] match {
              case Some(w) =>
                '{
                  @SuppressWarnings(Array("AsInstanceOf"))
                  def instance = ${ w }.asInstanceOf[M[A]]

                  instance
                }

              case _ =>
                report.errorAndAbort(s"Instance not found: ${TypeRepr.of[M].typeSymbol.fullName}[${underlyingTpr.typeSymbol.fullName}]")
            }

          case _ =>
            report.errorAndAbort(
              s"${underlyingTpr.show} is not a value class"
            )
        }
      }

      case tpr =>
        report.errorAndAbort(s"${tpr.show} is not an opaque alias")
    }
  }

  private def ensureFindType[A](
      using
      q: Quotes,
      tpe: Type[A]
    ): Unit = {
    import q.reflect.*

    TypeRepr.of[A](using tpe).dealias match {
      case OrType(_, _) =>

      case notFound if (notFound.typeSymbol == Symbol.noSymbol) =>
        report.errorAndAbort("Type not found")

      case _ =>
    }
  }

  def readerWithConfig[A: Type, Opts <: MacroOptions: Type](
      config: Expr[MacroConfiguration]
    )(using
      q: Quotes,
      dvat: Type[DefaultValue],
      rat: Type[Reader],
      rt: Type[BSONReader],
      it: Type[Ignore],
      kt: Type[Key],
      flt: Type[Flatten],
      dit: Type[MacroOptions.DisableWarnings],
      vt: Type[MacroOptions.Verbose]
    ): Expr[BSONDocumentReader[A]] = {
    import q.reflect.*

    ensureFindType[A]

    val helper = createReaderHelper[A, Opts](config)

    '{
      withSelfDocReader { (forwardBSONReader: BSONDocumentReader[A]) =>
        { (macroVal: BSONDocument) =>
          ${ helper.readBody('{ macroVal }, '{ forwardBSONReader }) }
        }
      }
    }
  }

  private inline def withSelfDocWriter[T](
      f: BSONDocumentWriter[T] => (T => TryResult[BSONDocument])
    ): BSONDocumentWriter[T] = {
    new BSONDocumentWriter[T] { self =>
      lazy val underlying = f(self)
      def writeTry(v: T) = underlying(v)
    }
  }

  def writerWithConfig[A: Type, Opts <: MacroOptions: Type](
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

    ensureFindType[A]

    val helper = createWriterHelper[A, Opts](config)

    '{
      withSelfDocWriter { (forwardBSONWriter: BSONDocumentWriter[A]) =>
        { (macroVal: A) =>
          ${ helper.writeBody('{ macroVal }, '{ forwardBSONWriter }) }
        }
      }
    }
  }

  def handlerWithConfig[A: Type, Opts <: MacroOptions: Type](
      config: Expr[MacroConfiguration]
    )(using
      q: Quotes,
      dvat: Type[DefaultValue],
      rat: Type[Reader],
      wat: Type[Writer],
      it: Type[Ignore],
      kt: Type[Key],
      flt: Type[Flatten],
      nant: Type[NoneAsNull],
      dit: Type[MacroOptions.DisableWarnings],
      vt: Type[MacroOptions.Verbose]
    ): Expr[BSONDocumentHandler[A]] = {
    import q.reflect.*

    ensureFindType[A]

    val helper = createHandlerHelper[A, Opts](config)

    '{
      val writer = withSelfDocWriter {
        (forwardBSONWriter: BSONDocumentWriter[A]) =>
          { (macroVal: A) =>
            ${ helper.writeBody('{ macroVal }, '{ forwardBSONWriter }) }
          }
      }

      val reader = withSelfDocReader {
        (forwardBSONReader: BSONDocumentReader[A]) =>
          { (macroVal: BSONDocument) =>
            ${ helper.readBody('{ macroVal }, '{ forwardBSONReader }) }
          }
      }

      BSONDocumentHandler.provided[A](reader, writer)
    }
  }

  def implicitOptionsConfig(using
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

  // ---

  private def createHandlerHelper[A, O <: MacroOptions](
      config: Expr[MacroConfiguration]
    )(using
      _quotes: Quotes,
      ot: Type[O],
      at: Type[A],
      dwt: Type[BSONDocumentWriter],
      drt: Type[BSONDocumentReader],
      dvat: Type[DefaultValue],
      rat: Type[Reader],
      wat: Type[Writer],
      wt: Type[BSONWriter],
      rt: Type[BSONReader],
      it: Type[Ignore],
      kt: Type[Key],
      flt: Type[Flatten],
      nant: Type[NoneAsNull],
      dit: Type[MacroOptions.DisableWarnings],
      vt: Type[MacroOptions.Verbose]
    ) = new HandlerHelper[A](config)
    with MacroTopHelpers[A]
    with WriterHelpers[A]
    with ReaderHelpers[A] {
    protected type Opts = O
    type Q = _quotes.type
    val quotes = _quotes

    import quotes.reflect.*

    val docReaderType = drt
    val docWriterType = dwt
    val writerAnnotationTpe = wat
    val readerAnnotationTpe = rat
    val readerType = rt
    val writerType = wt
    val defaultValueAnnotationTpe = dvat
    val flattenType = flt
    val noneAsNullType = nant
    val ignoreType = it
    val keyType = kt
    val disableWarningsTpe = dit
    val verboseTpe = vt

    val aTpe = at
    val aTpeRepr = TypeRepr.of[A](using at)

    val optsTpe = ot
    val optsTpr = TypeRepr.of[Opts](using optsTpe).dealias

    val pendingReaders = Map.empty[TypeRepr, Expr[BSONDocumentReader[_]]]
    val pendingWriters = Map.empty[TypeRepr, Expr[BSONDocumentWriter[_]]]
  }

  private abstract class HandlerHelper[A](
      val config: Expr[MacroConfiguration])
      extends ImplicitResolver[A]
      with QuotesHelper {
    self: MacroHelpers[A] with WriterHelpers[A] with ReaderHelpers[A] =>

    val macroCfgInit = config

    import quotes.reflect.*

    def writeBody(
        macroVal: Expr[A],
        forwardExpr: Expr[BSONDocumentWriter[A]]
      ): Expr[TryResult[BSONDocument]] = {
      val writer = documentWriter(macroVal, forwardExpr)

      debug(s"// Writer\n${writer}") // TODO: .show

      writer
    }

    def readBody(
        macroVal: Expr[BSONDocument],
        forwardExpr: Expr[BSONDocumentReader[A]]
      ): Expr[TryResult[A]] = {
      val reader = documentReader(macroVal, forwardExpr)

      debug(s"// Reader\n${reader.show}")

      reader
    }
  }

  // ---

  private def createReaderHelper[A, O <: MacroOptions](
      config: Expr[MacroConfiguration]
    )(using
      _quotes: Quotes,
      ot: Type[O],
      at: Type[A],
      drt: Type[BSONDocumentReader],
      dvat: Type[DefaultValue],
      rat: Type[Reader],
      rt: Type[BSONReader],
      it: Type[Ignore],
      kt: Type[Key],
      flt: Type[Flatten],
      dit: Type[MacroOptions.DisableWarnings],
      vt: Type[MacroOptions.Verbose]
    ) =
    new ReaderHelper[A](config) with MacroTopHelpers[A] with ReaderHelpers[A] {
      protected type Opts = O
      type Q = _quotes.type
      val quotes = _quotes

      import quotes.reflect.*

      val docReaderType = drt
      val readerAnnotationTpe = rat
      val readerType = rt
      val defaultValueAnnotationTpe = dvat
      val flattenType = flt
      val ignoreType = it
      val keyType = kt
      val disableWarningsTpe = dit
      val verboseTpe = vt

      val aTpe = at
      val aTpeRepr = TypeRepr.of[A](using at)

      val optsTpe = ot
      val optsTpr = TypeRepr.of[Opts](using optsTpe).dealias

      val pendingReaders = Map.empty[TypeRepr, Expr[BSONDocumentReader[_]]]
    }

  private abstract class ReaderHelper[A](
      val config: Expr[MacroConfiguration])
      extends ImplicitResolver[A]
      with QuotesHelper {
    self: MacroHelpers[A] with ReaderHelpers[A] =>

    val macroCfgInit = config

    import quotes.reflect.*

    def readBody(
        macroVal: Expr[BSONDocument],
        forwardExpr: Expr[BSONDocumentReader[A]]
      ): Expr[TryResult[A]] = {
      val reader = documentReader(macroVal, forwardExpr)

      debug(s"// Reader\n${reader.show}")

      reader
    }
  }

  sealed trait ReaderHelpers[A] {
    self: MacroHelpers[A] with ImplicitResolver[A] with QuotesHelper =>

    import quotes.reflect.*

    // --- Reader types ---

    protected def readerType: Type[BSONReader]

    private final lazy val readerTypeRepr: TypeRepr =
      TypeRepr.of(using readerType)

    protected def docReaderType: Type[BSONDocumentReader]

    private final lazy val docReaderRepr: TypeRepr =
      TypeRepr.of(using docReaderType)

    protected def readerAnnotationTpe: Type[Reader]

    protected def flattenType: Type[Flatten]

    protected final lazy val readerFlattenRepr: TypeRepr =
      TypeRepr.of(using flattenType)

    private final lazy val readerAnnotationRepr: TypeRepr =
      TypeRepr.of[Reader](using readerAnnotationTpe)

    private final lazy val readerAnyAnnotationRepr: TypeRepr = {
      given t: Type[Reader] = readerAnnotationTpe
      TypeRepr.of[Reader[_]]
    }

    protected def defaultValueAnnotationTpe: Type[DefaultValue]

    protected final lazy val defaultValueAnnotationRepr: TypeRepr =
      TypeRepr.of[DefaultValue](using defaultValueAnnotationTpe)

    private final lazy val defaultValueAnyAnnotationRepr: TypeRepr = {
      given t: Type[DefaultValue] = defaultValueAnnotationTpe
      TypeRepr.of[DefaultValue[_]]
    }

    // --- Reader builders ---

    /** The being materialized readers */
    protected def pendingReaders: Map[TypeRepr, Expr[BSONDocumentReader[_]]]

    /**
     * @param macroVal the value to be read
     */
    final def documentReader(
        macroVal: Expr[BSONDocument],
        forwardExpr: Expr[BSONDocumentReader[A]]
      ): Expr[TryResult[A]] = withMacroCfg { config =>

      def readClass: Expr[TryResult[A]] =
        readBodyConstruct(config, macroVal, forwardExpr)(using aTpe)

      def handleChildTypes(discriminator: Expr[String]): Expr[TryResult[A]] = {
        val discriminatedUnion: Option[Expr[TryResult[A]]] =
          unionTypes.map { types =>
            if (types.isEmpty) {
              report.errorAndAbort(
                s"Type ${prettyType(aTpeRepr)} is not a supported union"
              )
            }

            val resolve = resolver[BSONDocumentReader, A](
              forwardExpr,
              pendingReaders.view.mapValues(_.asTerm).toMap,
              debug
            )(docReaderType)

            val cases = types.zipWithIndex.map { (tpr, i) =>
              tpr.asType match {
                case tt @ '[at] =>
                  readDiscriminatedCase[at](
                    macroVal,
                    discriminator,
                    forwardExpr,
                    '{
                      @SuppressWarnings(Array("AsInstanceOf"))
                      def forward =
                        ${ forwardExpr }.asInstanceOf[BSONDocumentReader[at]]

                      forward
                    },
                    config,
                    resolve,
                    tpr,
                    name = s"macroTpe${i}"
                  )(using tt)
              }
            }

            Match(discriminator.asTerm, cases).asExprOf[TryResult[A]]
          }

        val discriminatedSubTypes: Option[Expr[TryResult[A]]] =
          subTypes.map { (types, exhaustive) =>
            val resolve = resolver[BSONDocumentReader, A](
              forwardExpr,
              pendingReaders.view.mapValues(_.asTerm).toMap,
              debug
            )(docReaderType)

            type Subtype[U <: A] = U

            val cases = types.zipWithIndex.map { (tpr, i) =>
              tpr match { // TODO: tpr.typeArgs.nonEmpty
                case AppliedType(_, args) if args.nonEmpty =>
                  report.errorAndAbort(s"Generic type ${prettyType(tpr)} is not supported as sub-type of ${prettyType(aTpeRepr)}")

                case _ =>
              }

              tpr.asType match {
                case tt @ '[Subtype[at]] =>
                  readDiscriminatedCase[at](
                    macroVal,
                    discriminator,
                    forwardExpr, {
                      def expectedTpe = prettyType(TypeRepr.of[at])

                      '{
                        BSONDocumentReader.from[at] { doc =>
                          ${ forwardExpr }.readTry(doc).collect {
                            case v: at => v
                          }
                        }
                      }
                    },
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
                TryFailure(
                  exceptions.ValueDoesNotMatchException(${ discriminator })
                )
              }.asTerm
            )

            Match(discriminator.asTerm, cases :+ fallback)
              .asExprOf[TryResult[A]]
          }

        discriminatedUnion orElse discriminatedSubTypes getOrElse readClass
      }

      if (subTypes.isEmpty && unionTypes.isEmpty) {
        readClass
      } else {
        '{
          ${ macroVal }.getAsTry[String](${ config }.discriminator).flatMap {
            (dis: String) => ${ handleChildTypes('dis) }
          }
        }
      }
    }

    private def readDiscriminatedCase[T](
        macroVal: Expr[BSONDocument],
        discriminator: Expr[String],
        parentBSONReader: Expr[BSONDocumentReader[A]],
        forwardBSONReader: Expr[BSONDocumentReader[T]],
        config: Expr[MacroConfiguration],
        resolve: TypeRepr => Option[Implicit],
        tpr: TypeRepr,
        name: String
      )(using
        tpe: Type[T]
      ): CaseDef = {

      type CaseType[U <: T] = U

      tpe match {
        case typ @ '[CaseType[t]] =>
          Expr.summon[ClassTag[t]] match {
            case Some(cls) => {
              val bind =
                Symbol.newBind(
                  Symbol.spliceOwner,
                  name,
                  Flags.Case,
                  TypeRepr.of[String]
                )

              val tpeCaseName: Expr[String] = '{
                ${ config }.typeNaming(${ cls }.runtimeClass)
              }

              val body: Expr[TryResult[t]] =
                givenReader[t](macroVal, typ)(resolve).getOrElse {
                  if (hasOption[MacroOptions.AutomaticMaterialization]) {
                    val subHelper = createSubReaderHelper[A, T](
                      aTpeRepr,
                      parentBSONReader,
                      tpe,
                      tpr
                    )

                    // No existing implicit, but can fallback to automatic mat
                    subHelper.readBodyConstruct[t](
                      config,
                      macroVal,
                      forwardBSONReader.asExprOf[BSONDocumentReader[t]]
                    )(using typ)
                  } else {
                    report.errorAndAbort(s"Instance not found: ${classOf[BSONReader[_]].getName}[${prettyType(tpr)}]")
                  }
                }

              CaseDef(
                Bind(bind, tpeCaseName.asTerm),
                guard = None,
                rhs = body.asTerm
              )
            }

            case None =>
              report.errorAndAbort(
                s"Instance not found: ClassTag[${prettyType(tpr)}]"
              )
          }
      }
    }

    /**
     * Reads the value using a reader resolved from the implicit scope.
     *
     * @param macroVal the document to be read
     * @param tpe the type to be read
     */
    private def givenReader[T](
        macroVal: Expr[BSONDocument],
        tpe: Type[T]
      )(r: TypeRepr => Option[Implicit]
      ): Option[Expr[TryResult[T]]] = {
      given typ: Type[T] = tpe
      val tpr = TypeRepr.of[T](using tpe)

      r(tpr).map { (reader, _) =>
        '{ ${ reader.asExprOf[BSONDocumentReader[T]] }.readTry(${ macroVal }) }
      }
    }

    @inline private def readBodyConstruct[T](
        macroCfg: Expr[MacroConfiguration],
        macroVal: Expr[BSONDocument],
        forwardExpr: Expr[BSONDocumentReader[T]]
      )(using
        tpe: Type[T]
      ): Expr[TryResult[T]] = {
      val repr = TypeRepr.of[T](using tpe)

      if (repr.isSingleton) {
        singletonReader[T]
      } else if (repr.typeSymbol == repr.typeSymbol.moduleClass) {
        val instance = Ref(repr.typeSymbol.companionModule).asExprOf[T]

        '{ TrySuccess($instance) }
      } else {
        tpe match {
          case '[IsProduct[t]] =>
            Expr.summon[ProductOf[t]] match {
              case Some(pof) =>
                productReader[t, t](
                  macroCfg,
                  macroVal,
                  repr,
                  forwardExpr.asExprOf[BSONDocumentReader[t]],
                  pof
                ).asExprOf[TryResult[T]]

              case _ =>
                report.errorAndAbort(
                  s"Instance not found: 'ProductOf[${prettyType(repr)}]'"
                )
            }

          case '[t] =>
            Expr.summon[Conversion[t, _ <: Product]] match {
              case Some('{ $conv: Conversion[t, IsProduct[p]] }) =>
                Expr.summon[ProductOf[t]] match {
                  case Some(pof) =>
                    productReader[t, p](
                      macroCfg,
                      macroVal,
                      repr,
                      forwardExpr.asExprOf[BSONDocumentReader[t]],
                      pof
                    ).asExprOf[TryResult[T]]

                  case _ =>
                    report.errorAndAbort(
                      s"Instance not found: 'ProductOf[${prettyType(repr)}]'"
                    )
                }

              case _ =>
                report.errorAndAbort(s"Instance not found: 'Conversion[${prettyType(repr)}, _ <: Product]'")
            }
        }
      }
    }

    private def singletonReader[T](
        using
        tpe: Type[T]
      ): Expr[TrySuccess[T]] =
      Expr.summon[ValueOf[T]] match {
        case Some(vof) =>
          '{ TrySuccess(${ vof }.value) }

        case _ =>
          report.errorAndAbort(
            s"Something weird is going on with '${prettyType(TypeRepr.of(using tpe))}'. Should be a singleton but can't parse it"
          )
      }

    private sealed trait ReadableProperty {
      def symbol: Symbol
      def index: Int
      def tpr: TypeRepr
      def default: Option[Expr[_]]
    }

    private case class RequiredReadableProperty(
        symbol: Symbol,
        index: Int,
        tpr: TypeRepr,
        default: Option[Expr[_]],
        reader: Expr[BSONReader[_]])
        extends ReadableProperty

    private case class OptionalReadableProperty(
        symbol: Symbol,
        index: Int,
        tpr: TypeRepr,
        default: Option[Expr[_]])
        extends ReadableProperty

    /**
     * @param macroVal the value to be read
     * @param tpr the representation of type `T` (related to `tpe`)
     * @param tpe the value type
     */
    private def productReader[T, U <: Product](
        macroCfg: Expr[MacroConfiguration],
        macroVal: Expr[BSONDocument],
        tpr: TypeRepr,
        forwardExpr: Expr[BSONDocumentReader[T]],
        pof: Expr[ProductOf[T]]
      )(using
        tpe: Type[T],
        ptpe: Type[U]
      ): Expr[TryResult[T]] = {
      val tprElements = productElements[T, U](tpr, pof) match {
        case TryFailure(cause) =>
          report.errorAndAbort(cause.getMessage)

        case TrySuccess(elms) =>
          elms
      }

      val types = tprElements.map(_._2)
      val resolve = resolver[BSONReader, T](
        forwardExpr,
        pendingReaders.view.mapValues(_.asTerm).toMap,
        debug
      )(readerType)

      val compCls = tpr.typeSymbol.companionClass
      val compMod = Ref(tpr.typeSymbol.companionModule)

      val (optional, required) = tprElements.zipWithIndex.map {
        case ((sym, rpt), i) =>
          val pt = rpt.dealias

          pt.asType match {
            case pTpe @ '[t] =>
              val readerAnnTpe = readerAnnotationRepr.appliedTo(pt)

              val readerAnns = sym.annotations.flatMap {
                case ann if (ann.tpe <:< readerAnyAnnotationRepr) => {
                  if (!(ann.tpe <:< readerAnnTpe)) {
                    report.errorAndAbort(s"Invalid annotation @Reader(${ann.show}) for '${prettyType(tpr)}.${sym.name}': Reader[${prettyType(pt)}]")
                  }

                  sym.getAnnotation(readerAnnTpe.typeSymbol).collect {
                    case Apply(_, List(reader)) =>
                      reader.asExprOf[BSONReader[t]]
                  }
                }

                case _ =>
                  Seq.empty[Expr[BSONReader[t]]]
              }

              val readerFromAnn: Option[Expr[BSONReader[t]]] =
                readerAnns match {
                  case w +: other => {
                    if (other.nonEmpty) {
                      warn(s"Exactly one @Reader must be provided for each property; Ignoring invalid annotations for '${prettyType(tpr)}.${sym.name}'")
                    }

                    Some(w)
                  }

                  case _ =>
                    None
                }

              lazy val defaultValueAnnTpe =
                defaultValueAnnotationRepr.appliedTo(pt)

              lazy val defaultValueAnns: Seq[Expr[t]] =
                sym.annotations.flatMap {
                  case ann if (ann.tpe <:< defaultValueAnyAnnotationRepr) => {
                    ann.tpe match {
                      case AppliedType(_, vtpr :: Nil) if (!(vtpr <:< pt)) =>
                        report.errorAndAbort(s"Invalid annotation type ${prettyType(ann.tpe)} for '${prettyType(tpr)}.${sym.name}': ${prettyType(pt)} default value expected")
                      case _ =>
                    }

                    sym.getAnnotation(defaultValueAnnTpe.typeSymbol).collect {
                      case Apply(_, defaultValue :: Nil) =>
                        defaultValue.asExprOf[t]
                    }
                  }

                  case _ =>
                    Seq.empty[Expr[t]]
                }

              val default: Option[Expr[t]] =
                compCls
                  .declaredMethod(f"$$lessinit$$greater$$default$$" + (i + 1))
                  .headOption
                  .collect {
                    case defaultSym if sym.flags.is(Flags.HasDefault) =>
                      compMod.select(defaultSym).asExprOf[t]
                  }
                  .orElse {
                    defaultValueAnns match {
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

              val reader: Option[Expr[BSONReader[t]]] =
                readerFromAnn.orElse {
                  resolveReader[T, t](
                    tpr,
                    forwardExpr,
                    sym.name,
                    pt,
                    resolve
                  )(
                    using pTpe
                  )
                }

              reader match {
                case Some(rdr) =>
                  RequiredReadableProperty(sym, i, pt, default, rdr)

                case _ if ignoreField(sym) =>
                  RequiredReadableProperty(
                    sym,
                    i,
                    pt,
                    default,
                    '{ scala.Predef.`???` }.asExprOf[BSONReader[t]]
                  )

                case _ if !isOptionalType(pt) =>
                  report.errorAndAbort(s"No implicit found for '${prettyType(tpr)}.${sym.name}': ${classOf[BSONReader[_]].getName}[${prettyType(pt)}]")

                case _ =>
                  OptionalReadableProperty(sym, i, pt, default)
              }
          }
      }.toSeq.partition {
        case _: OptionalReadableProperty => true
        case _                           => false
      }

      type ExceptionAcc = MBuilder[HandlerException, Seq[HandlerException]]

      // For required field
      def tryWithDefault[U: Type](
          `try`: Expr[TryResult[U]],
          default: Expr[U]
        ): Expr[TryResult[U]] =
        '{
          ${ `try` } match {
            case TryFailure(_: exceptions.BSONValueNotFoundException) =>
              TrySuccess(${ default })

            case result =>
              result
          }
        }

      // For optional field
      def tryWithOptDefault[U: Type](
          `try`: Expr[TryResult[Option[U]]],
          default: Expr[Option[U]]
        ): Expr[TryResult[Option[U]]] =
        '{
          ${ `try` } match {
            case TryFailure(_: exceptions.BSONValueNotFoundException) |
                TrySuccess(None) =>
              TrySuccess(${ default })

            case result =>
              result
          }
        }

      val reqElmts: Seq[(Int, Expr[TryResult[_]])] = required.map {
        case OptionalReadableProperty(param, _, pt, _) =>
          report.errorAndAbort(
            s"Unexpected optional field '${prettyType(tpr)}.${param.name}': ${prettyType(pt)}"
          )

        case p: RequiredReadableProperty => p
      }.map {
        case RequiredReadableProperty(param, n, pt, Some(default), _)
            if (ignoreField(param)) =>
          pt.asType match {
            case '[p] => n -> '{ TrySuccess(${ default.asExprOf[p] }) }
          }

        case RequiredReadableProperty(param, _, _, None, _)
            if (ignoreField(param)) =>
          report.errorAndAbort(s"Cannot ignore '${prettyType(tpr)}.${param.name}': no default value (see @DefaultValue)")

        case RequiredReadableProperty(param, n, pt, default, rdr) => {
          pt.asType match {
            case ptpe @ '[p] =>
              val pname = param.name
              val reader: Expr[BSONReader[p]] =
                rdr.asExprOf[BSONReader[p]]

              val get: Expr[TryResult[p]] = {
                if (mustFlatten(tpr, param, pt, reader)) {
                  val readTry = '{ ${ reader }.readTry(${ macroVal }) }

                  default.fold(readTry) { dv =>
                    tryWithDefault[p](readTry, dv.asExprOf[p])
                  }
                } else {
                  val field = fieldName(macroCfg, fieldKey(param))

                  val getAsTry = if (isOptionalType(pt)) {
                    '{
                      ${ macroVal }.getRawAsTry[p]($field)($reader)
                    }
                  } else {
                    '{
                      ${ macroVal }.getAsTry[p]($field)($reader)
                    }
                  }

                  default.fold(getAsTry) { dv =>
                    tryWithDefault(getAsTry, dv.asExprOf[p])
                  }
                }
              }

              val parsed: Expr[TryResult[p]] = '{
                ${ get }.recoverWith {
                  case cause =>
                    TryFailure[p](
                      exceptions.HandlerException(${ Expr(pname) }, cause)
                    )
                }
              }

              n -> parsed
          }
        }
      }

      val exElmts: Seq[(Int, Expr[TryResult[_]])] = optional.map {
        case p @ OptionalReadableProperty(_, _, OptionTypeParameter(it), _) =>
          p.copy(tpr = it)

        case OptionalReadableProperty(param, _, pt, _) =>
          report.errorAndAbort(
            s"Invalid optional field '${prettyType(tpr)}.${param.name}': ${prettyType(pt)}"
          )

        case RequiredReadableProperty(param, _, pt, _, _) =>
          report.errorAndAbort(
            s"Unexpected non-optional field '${prettyType(tpr)}.${param.name}': ${prettyType(pt)}"
          )

      }.map {
        case OptionalReadableProperty(param, n, pt, Some(default))
            if (ignoreField(param)) =>
          pt.asType match {
            case '[p] => n -> '{ TrySuccess(${ default.asExprOf[p] }) }
          }

        case OptionalReadableProperty(param, _, _, None)
            if (ignoreField(param)) =>
          report.errorAndAbort(s"Cannot ignore '${prettyType(tpr)}.${param.name}': not default value (see @DefaultValue)")

        case OptionalReadableProperty(
              param,
              n,
              it,
              default
            ) => {
          val pname = param.name

          it.asType match {
            case iTpe @ '[i] =>
              val reader: Expr[BSONReader[i]] =
                resolveReader(tpr, forwardExpr, pname, it, resolve)(
                  using iTpe
                ) match {
                  case Some(r) => r

                  case _ =>
                    report.errorAndAbort(s"No implicit found for '${prettyType(tpr)}.${param.name}': ${classOf[BSONReader[_]].getName}[${prettyType(it)}]")
                }

              type p = Option[i]

              val field = fieldName(macroCfg, fieldKey(param))
              val readTry: Expr[TryResult[p]] = {
                if (mustFlatten(tpr, param, TypeRepr.of[p], reader)) {
                  '{
                    ${ reader }.readTry(${ macroVal }).map(Some(_))
                  }
                } else {
                  '{
                    ${ macroVal }.getAsUnflattenedTry[i]($field)(${ reader })
                  }
                }
              }

              n -> default.fold(readTry) { dv =>
                tryWithOptDefault[i](readTry, dv.asExprOf[p])
              }
          }
        }
      }

      val tupElmts: Seq[Expr[TryResult[_]]] =
        (reqElmts ++ exElmts).toSeq
          .sortBy(_._1)
          .map(_._2) // Make sure the exprs respect fields order

      '{
        trySeq(${ Expr.ofSeq(tupElmts) }).map { ls =>
          ${ pof }.fromProduct(Tuple fromArray ls.toArray)
        }
      }
    }

    /**
     * @param tpr the representation for the type declaring `param`
     * @param ptpr the type representation for the type T of `param`
     */
    private def mustFlatten[T](
        tpr: TypeRepr,
        param: Symbol,
        ptpr: TypeRepr,
        reader: Expr[BSONReader[T]]
      ): Boolean = {
      if (param.annotations.exists(_.tpe =:= readerFlattenRepr)) {
        if (reader.asTerm.symbol.name.toString == "forwardBSONReader") {
          report.errorAndAbort(
            s"Cannot flatten reader for '${prettyType(tpr)}.${param.name}': recursive type"
          )
        }

        if (!(reader.asTerm.tpe <:< docReaderRepr.appliedTo(ptpr))) {
          report.errorAndAbort(s"Cannot flatten reader '${prettyType(reader.asTerm.tpe)}' for '${prettyType(tpr)}.${param.name}': doesn't conform BSONDocumentReader")
        }

        true
      } else false
    }

    /**
     * @tparam T the field type
     * @tparam U the parent type (class)
     *
     * @param parentTpr the representation of the parent type `U`
     * @param pname the parameter/field name (for debug)
     * @param tpr the representation of the `T` field type (related to `tpe`)
     * @param annotatedReader the reader from annotation
     */
    def resolveReader[U, T](
        parentTpr: TypeRepr,
        forwardExpr: Expr[BSONDocumentReader[U]],
        pname: String,
        tpr: TypeRepr,
        resolve: TypeRepr => Option[Implicit]
      )(using
        tpe: Type[T]
      ): Option[Expr[BSONReader[T]]] = {
      resolve(tpr) match {
        case Some((reader, _)) =>
          Some(reader.asExprOf[BSONReader[T]])

        case None => {
          if (!hasOption[MacroOptions.AutomaticMaterialization]) {
            None
          } else {
            val lt = leafType(tpr)

            warn(
              s"Materializing ${classOf[BSONReader[_]].getName}[${prettyType(
                  lt
                )}] for '${prettyType(TypeRepr.of(using tpe))}.$pname': it's recommended to declare it explicitly"
            )

            val nme = s"${pname}LeafVal"

            type IsAnyVal[U <: AnyVal] = U

            lt.asType match {
              case tt @ '[IsAnyVal[at]] => {
                given ot: Type[Opts] = optsTpe

                // Expr[BSONValue => TryResult[at]]
                val tryRead = '{
                  val subReader: BSONReader[at] =
                    ${ anyValReader[at, Opts] }

                  subReader.readTry(_: BSONValue)
                }

                Some('{
                  given leafReader: BSONReader[at] =
                    BSONReader.from[at](${ tryRead })

                  scala.compiletime.summonInline[BSONReader[T]]
                })
              }

              case tt @ '[at] => {
                val subHelper = createSubReaderHelper[U, at](
                  parentTpr,
                  forwardExpr,
                  tt,
                  lt
                )

                // Expr[BSONDocument => TryResult[at]]
                val tryRead = '{
                  val subReader: BSONDocumentReader[at] = withSelfDocReader {
                    (forwardBSONReader: BSONDocumentReader[at]) =>
                      { (macroVal: BSONDocument) =>
                        ${
                          subHelper.documentReader(
                            macroVal = '{ macroVal },
                            forwardExpr = '{ forwardBSONReader }
                          )
                        }
                      }
                  }

                  subReader.readTry(_: BSONDocument)
                }

                Some('{
                  given leafReader: BSONDocumentReader[at] =
                    BSONDocumentReader.from[at](${ tryRead })

                  scala.compiletime.summonInline[BSONReader[T]]
                })
              }
            }
          }
        }
      }
    }

    /**
     * @tparam U the parent type
     * @tparam T the sub-type
     */
    private def createSubReaderHelper[U, T](
        parentTpr: TypeRepr,
        parentBSONReader: Expr[BSONDocumentReader[U]],
        tt: Type[T],
        tpr: TypeRepr
      ) = new MacroHelpers[T]
      with ReaderHelpers[T]
      with ImplicitResolver[T]
      with QuotesHelper {

      type Opts = self.Opts
      type Q = self.quotes.type
      val quotes = self.quotes

      val macroCfgInit = self.macroCfgInit

      val docReaderType = self.docReaderType
      val readerAnnotationTpe = self.readerAnnotationTpe
      val defaultValueAnnotationTpe = self.defaultValueAnnotationTpe
      val readerType = self.readerType
      val flattenType = self.flattenType
      val ignoreType = self.ignoreType
      val keyType = self.keyType

      val aTpe = tt
      val aTpeRepr = tpr
      val optsTpe = self.optsTpe
      val optsTpr = self.optsTpr
      val disableWarningsTpe = self.disableWarningsTpe
      val verboseTpe = self.verboseTpe

      val pendingReaders = self.pendingReaders + (parentTpr -> parentBSONReader)
    }
  }

  // ---

  private def createWriterHelper[A, O <: MacroOptions](
      config: Expr[MacroConfiguration]
    )(using
      _quotes: Quotes,
      ot: Type[O],
      at: Type[A],
      dwt: Type[BSONDocumentWriter],
      wat: Type[Writer],
      wt: Type[BSONWriter],
      it: Type[Ignore],
      kt: Type[Key],
      flt: Type[Flatten],
      nant: Type[NoneAsNull],
      dit: Type[MacroOptions.DisableWarnings],
      vt: Type[MacroOptions.Verbose]
    ) =
    new WriterHelper[A](config) with MacroTopHelpers[A] with WriterHelpers[A] {
      protected type Opts = O
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
      val disableWarningsTpe = dit
      val verboseTpe = vt

      val aTpe = at
      val aTpeRepr = TypeRepr.of[A](using at)

      val optsTpe = ot
      val optsTpr = TypeRepr.of[Opts](using optsTpe).dealias

      val pendingWriters = Map.empty[TypeRepr, Expr[BSONDocumentWriter[_]]]
    }

  private abstract class WriterHelper[A](
      val config: Expr[MacroConfiguration])
      extends ImplicitResolver[A]
      with QuotesHelper {
    self: MacroHelpers[A] with WriterHelpers[A] =>

    val macroCfgInit = config

    import quotes.reflect.*

    def writeBody(
        macroVal: Expr[A],
        forwardExpr: Expr[BSONDocumentWriter[A]]
      ): Expr[TryResult[BSONDocument]] = {
      val writer = documentWriter(macroVal, forwardExpr)

      debug(s"// Writer\n${writer}") // TODO: .show

      writer
    }
  }

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
      TypeRepr.of[TrySuccess[BSONValue]]

    private final lazy val successBsonDoc =
      TypeRepr.of[TrySuccess[BSONDocument]]

    // --- Writer builders ---

    /** The being materialized writers */
    protected def pendingWriters: Map[TypeRepr, Expr[BSONDocumentWriter[_]]]

    /**
     * @param macroVal the value to be written
     */
    protected final def documentWriter(
        macroVal: Expr[A],
        forwardExpr: Expr[BSONDocumentWriter[A]]
      ): Expr[TryResult[BSONDocument]] = withMacroCfg { config =>
      val discriminatedUnion: Option[Expr[TryResult[BSONDocument]]] =
        unionTypes.map { types =>
          if (types.isEmpty) {
            report.errorAndAbort(
              s"Type ${prettyType(aTpeRepr)} is not a supported union"
            )
          }

          val resolve = resolver[BSONDocumentWriter, A](
            forwardExpr,
            pendingWriters.view.mapValues(_.asTerm).toMap,
            debug
          )(docWriterType)

          val cases = types.zipWithIndex.map { (tpr, i) =>
            tpr.asType match {
              case tt @ '[at] =>
                writeDiscriminatedCase[at](
                  macroVal,
                  forwardExpr,
                  '{
                    @SuppressWarnings(Array("AsInstanceOf"))
                    def forward =
                      ${ forwardExpr }.asInstanceOf[BSONDocumentWriter[at]]

                    forward
                  },
                  config,
                  resolve,
                  tpr,
                  name = s"macroTpe${i}"
                )(using tt)
            }
          }

          Match(macroVal.asTerm, cases).asExprOf[TryResult[BSONDocument]]
        }

      val discriminatedSubTypes: Option[Expr[TryResult[BSONDocument]]] =
        subTypes.map { (types, exhaustive) =>
          val resolve = resolver[BSONDocumentWriter, A](
            forwardExpr,
            pendingWriters.view.mapValues(_.asTerm).toMap,
            debug
          )(docWriterType)

          type Subtype[U <: A] = U

          val cases = types.zipWithIndex.map { (tpr, i) =>
            tpr match { // TODO: tpr.typeArgs.nonEmpty
              case AppliedType(_, args) if args.nonEmpty =>
                report.errorAndAbort(s"Generic type ${prettyType(tpr)} is not supported as sub-type of ${prettyType(aTpeRepr)}")

              case _ =>
            }

            tpr.asType match {
              case tt @ '[Subtype[at]] =>
                writeDiscriminatedCase[at](
                  macroVal,
                  forwardExpr,
                  '{ ${ forwardExpr }.narrow[at] },
                  config,
                  resolve,
                  tpr,
                  name = s"macroTpe${i}"
                )(using tt)

              case notChild @ '[t] =>
                report.errorAndAbort(
                  s"Type ${prettyType(TypeRepr.of(using notChild))} is not a sub-type of ${prettyType(aTpeRepr)}"
                )
            }
          }

          if (exhaustive) {
            Match(macroVal.asTerm, cases).asExprOf[TryResult[BSONDocument]]
          } else {
            def fallback = CaseDef(
              Wildcard(),
              None,
              '{
                TryFailure(
                  exceptions.ValueDoesNotMatchException(${ macroVal }.toString)
                )
              }.asTerm
            )

            Match(macroVal.asTerm, cases :+ fallback)
              .asExprOf[TryResult[BSONDocument]]
          }
        }

      discriminatedUnion orElse discriminatedSubTypes getOrElse {
        writeBodyConstruct(
          config,
          macroVal,
          forwardExpr
        )(using aTpe)
      }
    }

    private def writeDiscriminatedCase[T](
        macroVal: Expr[A],
        parentBSONWriter: Expr[BSONDocumentWriter[A]],
        forwardBSONWriter: Expr[BSONDocumentWriter[T]],
        config: Expr[MacroConfiguration],
        resolve: TypeRepr => Option[Implicit],
        tpr: TypeRepr,
        name: String
      )(using
        tpe: Type[T]
      ): CaseDef = {

      val bind =
        Symbol.newBind(
          Symbol.spliceOwner,
          name,
          Flags.Case,
          tpr
        )

      val matchedRef = Ref(bind).asExprOf[T]

      val body: Expr[TryResult[BSONDocument]] =
        givenWriter[T](matchedRef, tpe, tpr)(resolve).getOrElse {
          if (hasOption[MacroOptions.AutomaticMaterialization]) {
            val subHelper =
              createSubWriterHelper[A, T](aTpeRepr, parentBSONWriter, tpe, tpr)

            // No existing implicit, but can fallback to automatic mat
            subHelper.writeBodyConstruct[T](
              config,
              matchedRef,
              forwardBSONWriter
            )(using tpe)
          } else {
            report.errorAndAbort(s"Instance not found: ${classOf[BSONWriter[_]].getName}[${prettyType(tpr)}]")
          }
        }

      CaseDef(
        Bind(bind, Typed(Wildcard(), Inferred(tpr))),
        guard = None,
        rhs = '{
          def discriminator = ${ discriminatorElement[T](config) }
          ${ body }.map { _ ++ discriminator }
        }.asTerm
      )
    }

    /**
     * Writes the value using a writer resolved from the implicit scope.
     *
     * @param macroVal the value to be written
     * @param tpe the type of the `macroVal`
     * @param tpr the representation of `tpe`
     */
    private def givenWriter[T](
        macroVal: Expr[T],
        tpe: Type[T],
        tpr: TypeRepr
      )(r: TypeRepr => Option[Implicit]
      ): Option[Expr[TryResult[BSONDocument]]] = {
      given typ: Type[T] = tpe
      val tpr = TypeRepr.of[T](using tpe)

      r(tpr).map { (writer, _) =>
        '{
          ${ writer.asExprOf[BSONDocumentWriter[T]] }.writeTry(${ macroVal })
        }
      }
    }

    @inline private def writeBodyConstruct[T](
        macroCfg: Expr[MacroConfiguration],
        macroVal: Expr[T],
        forwardExpr: Expr[BSONDocumentWriter[T]]
      )(using
        tpe: Type[T]
      ): Expr[TryResult[BSONDocument]] = {
      val repr = TypeRepr.of[T](using tpe)

      if (repr.isSingleton || repr.typeSymbol == repr.typeSymbol.moduleClass) {
        // is object
        singletonWriter[T]
      } else {
        type IsProduct[U <: Product] = U

        tpe match {
          case '[IsProduct[t]] =>
            Expr.summon[ProductOf[t]] match {
              case Some(pof) =>
                productWriter[t, t](
                  macroCfg,
                  macroVal.asExprOf[t],
                  repr,
                  forwardExpr.asExprOf[BSONDocumentWriter[t]],
                  '{ identity[t] },
                  pof
                )

              case _ =>
                report.errorAndAbort(
                  s"Instance not found: 'ProductOf[${prettyType(repr)}]'"
                )
            }

          case '[t] =>
            Expr.summon[Conversion[t, _ <: Product]] match {
              case Some('{ $conv: Conversion[t, IsProduct[p]] }) =>
                Expr.summon[ProductOf[t]] match {
                  case Some(pof) =>
                    productWriter[t, p](
                      macroCfg,
                      macroVal.asExprOf[t],
                      repr,
                      forwardExpr.asExprOf[BSONDocumentWriter[t]],
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
        }
      }
    }

    private def singletonWriter[T: Type]: Expr[TrySuccess[BSONDocument]] =
      '{ TrySuccess(BSONDocument.empty) }

    private sealed trait WritableProperty {
      def symbol: Symbol
      def index: Int
      def tpr: TypeRepr
    }

    private case class RequiredWritableProperty(
        symbol: Symbol,
        index: Int,
        tpr: TypeRepr,
        writer: Expr[BSONWriter[_]])
        extends WritableProperty

    private case class OptionalWritableProperty(
        symbol: Symbol,
        index: Int,
        tpr: TypeRepr)
        extends WritableProperty

    private lazy val writerCompanion =
      '{ reactivemongo.api.bson.BSONWriter }.asTerm

    private lazy val tryDocTpe = TypeRepr.of[TryResult[BSONDocument]]

    protected def flattenType: Type[Flatten]

    protected final lazy val writerFlattenRepr: TypeRepr =
      TypeRepr.of(using flattenType)

    protected def noneAsNullType: Type[NoneAsNull]

    protected final lazy val noneAsNullRepr: TypeRepr =
      TypeRepr.of(using noneAsNullType)

    /**
     * @param macroVal the value to be written
     * @param tpr the representation of type `T` (related to `tpe`)
     * @param toProduct the function to convert the input value as product `U`
     * @param tpe the value type
     */
    private def productWriter[T, U <: Product](
        macroCfg: Expr[MacroConfiguration],
        macroVal: Expr[T],
        tpr: TypeRepr,
        forwardExpr: Expr[BSONDocumentWriter[T]],
        toProduct: Expr[T => U],
        pof: Expr[ProductOf[T]]
      )(using
        tpe: Type[T],
        ptpe: Type[U]
      ): Expr[TryResult[BSONDocument]] = {
      val tprElements = productElements[T, U](tpr, pof) match {
        case TryFailure(cause) =>
          report.errorAndAbort(cause.getMessage)

        case TrySuccess(elms) =>
          elms
      }

      if (tprElements.isEmpty) {
        // Empty class
        singletonWriter[T]
      } else {
        val types = tprElements.map(_._2)
        val resolve =
          resolver[BSONWriter, T](
            forwardExpr,
            pendingWriters.view.mapValues(_.asTerm).toMap,
            debug
          )(
            writerType
          )

        val (optional, required) = tprElements.zipWithIndex.view.filterNot {
          case ((sym, _), _) => ignoreField(sym)
        }.map {
          case ((sym, rpt), i) =>
            val pt = rpt.dealias

            pt.asType match {
              case pTpe @ '[t] =>
                val writerAnnTpe = writerAnnotationRepr.appliedTo(pt)

                val writerAnns = sym.annotations.flatMap {
                  case ann if (ann.tpe <:< writerAnyAnnotationRepr) => {
                    if (!(ann.tpe <:< writerAnnTpe)) {
                      report.errorAndAbort(s"Invalid annotation @Writer(${ann.show}) for '${prettyType(tpr)}.${sym.name}': Writer[${prettyType(pt)}]")
                    }

                    sym.getAnnotation(writerAnnTpe.typeSymbol).collect {
                      case Apply(_, List(writer)) =>
                        writer.asExprOf[BSONWriter[t]]
                    }
                  }

                  case _ =>
                    Seq.empty[Expr[BSONWriter[t]]]
                }

                val writerFromAnn: Option[Expr[BSONWriter[t]]] =
                  writerAnns match {
                    case w +: other => {
                      if (other.nonEmpty) {
                        warn(s"Exactly one @Writer must be provided for each property; Ignoring invalid annotations for '${prettyType(tpr)}.${sym.name}'")
                      }

                      Some(w)
                    }

                    case _ =>
                      None
                  }

                val writer: Option[Expr[BSONWriter[t]]] =
                  writerFromAnn.orElse {
                    resolveWriter[t, T](
                      tpr,
                      forwardExpr,
                      sym.name,
                      pt,
                      resolve
                    )(
                      using pTpe
                    )
                  }

                writer match {
                  case Some(wrt) =>
                    RequiredWritableProperty(sym, i, pt, wrt)

                  case _ if !isOptionalType(pt) =>
                    report.errorAndAbort(s"No implicit found for '${prettyType(tpr)}.${sym.name}': ${classOf[BSONWriter[_]].getName}[${prettyType(pt)}]")

                  case _ =>
                    OptionalWritableProperty(sym, i, pt)
                }
            }
        }.toSeq.partition {
          case _: OptionalWritableProperty => true
          case _                           => false
        }

        type ElementAcc = MBuilder[BSONElement, Seq[BSONElement]]
        type ExceptionAcc = MBuilder[HandlerException, Seq[HandlerException]]

        def withIdents[U: Type](
            f: Function3[Expr[MacroConfiguration], /* ok */ Expr[
              ElementAcc
            ], /* err */ Expr[ExceptionAcc], Expr[U]]
          ): Expr[U] = '{
          val ok = Seq.newBuilder[BSONElement]
          val err = Seq.newBuilder[HandlerException]

          ${ f(macroCfg, '{ ok }, '{ err }) }
        }

        val (tupleTpe, withTupled) =
          withTuple[T, U, TryResult[BSONDocument]](tpr, toProduct)

        withTupled(macroVal) { tupled =>
          val fieldMap = withFields(tupled, tupleTpe, tprElements, debug)

          withIdents[TryResult[BSONDocument]] { (config, bufOk, bufErr) =>
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
              if (mustFlatten[V](tpr, param, pt, writer)) {
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
                  res.asExprOf[TrySuccess[BSONDocument]]

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
                      ${ appendDocCall('{ doc }, field, param, pt, writer) }()

                    case bson =>
                      ${ appendCall(field, '{ bson }) }()
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
                        ${ appendDocCall('{ doc }, field, param, pt, writer) }()

                      case bson =>
                        ${ appendCall(field, '{ bson }) }()
                    }
                  )
                }
              }
            }

            val values: Seq[Expr[Unit]] = required.map {
              case OptionalWritableProperty(param, _, pt) =>
                report.errorAndAbort(
                  s"Unexpected optional field '${prettyType(tpr)}.${param.name}': ${prettyType(pt)}"
                )

              case p: RequiredWritableProperty => p
            }.map {
              case RequiredWritableProperty(param, i, pt, wrt) =>
                val pname = param.name

                val withField = fieldMap.get(pname) match {
                  case Some(f) => f

                  case _ =>
                    report.errorAndAbort(
                      s"Field not found: ${prettyType(tpr)}.${pname}"
                    )
                }

                pt.asType match {
                  case pTpe @ '[p] =>
                    val writer: Expr[BSONWriter[p]] =
                      wrt.asExprOf[BSONWriter[p]]

                    (withField { f =>
                      val field = fieldName(config, fieldKey(param))

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

            val extra: Seq[Expr[Unit]] = optional.map {
              case RequiredWritableProperty(param, _, pt, _) =>
                report.errorAndAbort(
                  s"Unexpected non-optional field '${prettyType(tpr)}.${param.name}': ${prettyType(pt)}"
                )

              case p: OptionalWritableProperty =>
                p
            }.map {
              case OptionalWritableProperty(
                    param,
                    i,
                    optType @ OptionTypeParameter(pt)
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
                      resolveWriter[p, T](tpr, forwardExpr, pname, pt, resolve)(
                        using pTpe
                      ) match {
                        case Some(w) => w
                        case None =>
                          report.errorAndAbort(s"No implicit found for '${prettyType(tpr)}.$pname': ${classOf[BSONWriter[_]].getName}[${prettyType(pt)}]")
                      }

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
                  TryFailure[BSONDocument](error suppress acc.tail)

                case _ =>
                  TrySuccess(BSONDocument(${ bufOk }.result(): _*))
              }
            }

            // => TryResult[BSONDocument]
            def writer = Block(fields.map(_.asTerm).toList, resExpr.asTerm)

            if (values.isEmpty && extra.isEmpty) {
              debug(
                s"No field found: class ${prettyType(TypeRepr.of[T](using tpe))}"
              )

              '{ TrySuccess(BSONDocument.empty) }
            } else {
              Block(tupled.asTerm :: Nil, writer)
                .asExprOf[TryResult[BSONDocument]]
            }
          }
        }
      }
    }

    /**
     * @param tpr the representation for the type declaring `param`
     * @param ptpr the type representation for the type T of `param`
     */
    private def mustFlatten[T](
        tpr: TypeRepr,
        param: Symbol,
        ptpr: TypeRepr,
        writer: Expr[BSONWriter[T]]
      ): Boolean = {
      if (param.annotations.exists(_.tpe =:= writerFlattenRepr)) {
        if (writer.asTerm.symbol.name.toString == "forwardBSONWriter") {
          report.errorAndAbort(
            s"Cannot flatten writer for '${prettyType(tpr)}.${param.name}': recursive type"
          )
        }

        if (!(writer.asTerm.tpe <:< docWriterRepr.appliedTo(ptpr))) {
          report.errorAndAbort(s"Cannot flatten writer '${prettyType(writer.asTerm.tpe)}' for '${prettyType(tpr)}.${param.name}': doesn't conform BSONDocumentWriter")
        }

        true
      } else false
    }

    /**
     * @tparam T the field type
     * @tparam U the parent type (class)
     *
     * @param parentTpr the representation of the parent type `U`
     * @param pname the parameter/field name
     * @param tpr the representation of `T` type (related to `tpe`)
     * @param tpe the type parameter for the writer to be resolved
     */
    private def resolveWriter[T, U](
        parentTpr: TypeRepr,
        forwardExpr: Expr[BSONDocumentWriter[U]],
        pname: String,
        tpr: TypeRepr,
        resolve: TypeRepr => Option[Implicit]
      )(using
        tpe: Type[T]
      ): Option[Expr[BSONWriter[T]]] = {
      resolve(tpr) match {
        case Some((writer, _)) =>
          Some(writer.asExprOf[BSONWriter[T]])

        case None => {
          if (!hasOption[MacroOptions.AutomaticMaterialization]) {
            None
          } else {
            val lt = leafType(tpr)

            warn(
              s"Materializing ${classOf[BSONWriter[_]].getName}[${prettyType(
                  lt
                )}] for '${prettyType(TypeRepr.of(using tpe))}.$pname': it's recommended to declare it explicitly"
            )

            val nme = s"${pname}LeafVal"

            type IsAnyVal[U <: AnyVal] = U

            lt.asType match {
              case tt @ '[IsAnyVal[at]] => {
                given ot: Type[Opts] = optsTpe

                // Expr[at => TryResult[_ <: BSONValue]]
                val tryWrite = '{
                  val subWriter: BSONWriter[at] =
                    ${ anyValWriter[at, Opts] }

                  subWriter.writeTry(_: at)
                }

                Some('{
                  given leafWriter: BSONWriter[at] =
                    BSONWriter.from[at](${ tryWrite })

                  scala.compiletime.summonInline[BSONWriter[T]]
                })
              }

              case tt @ '[at] => {
                val subHelper =
                  createSubWriterHelper[U, at](tpr, forwardExpr, tt, lt)

                // Expr[at => TryResult[BSONDocument]]
                val tryWrite = '{
                  val subWriter: BSONDocumentWriter[at] = withSelfDocWriter {
                    (forwardBSONWriter: BSONDocumentWriter[at]) =>
                      { (macroVal: at) =>
                        ${
                          subHelper.documentWriter(
                            macroVal = '{ macroVal },
                            forwardExpr = '{ forwardBSONWriter }
                          )
                        }
                      }
                  }

                  subWriter.writeTry(_: at)
                }

                Some('{
                  given leafWriter: BSONWriter[at] =
                    BSONWriter.from[at](${ tryWrite })

                  scala.compiletime.summonInline[BSONWriter[T]]
                })
              }
            }
          }
        }
      }
    }

    /**
     * @tparam U the parent type
     * @tparam T the sub-type
     */
    private def createSubWriterHelper[U, T](
        parentTpr: TypeRepr,
        parentBSONWriter: Expr[BSONDocumentWriter[U]],
        tt: Type[T],
        tpr: TypeRepr
      ) = new MacroHelpers[T]
      with WriterHelpers[T]
      with ImplicitResolver[T]
      with QuotesHelper {

      val macroCfgInit = self.macroCfgInit

      type Opts = self.Opts
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
      val optsTpr = self.optsTpr
      val disableWarningsTpe = self.disableWarningsTpe
      val verboseTpe = self.verboseTpe

      val pendingWriters = self.pendingWriters + (parentTpr -> parentBSONWriter)
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

  private inline def trySeq(in: Seq[TryResult[Any]]): TryResult[Seq[Any]] = {
    @annotation.tailrec
    def execute(
        in: Seq[TryResult[Any]],
        suc: Seq[Any],
        fail: Option[Throwable]
      ): TryResult[Seq[Any]] = in.headOption match {
      case Some(TrySuccess(v)) =>
        execute(in.tail, v +: suc, fail)

      case Some(TryFailure(cause)) =>
        execute(
          in.tail,
          suc,
          fail.map { exc =>
            exc.addSuppressed(cause)
            exc
          }.orElse(Some(cause))
        )

      case _ =>
        fail match {
          case Some(cause) =>
            TryFailure(cause)

          case _ =>
            TrySuccess(suc.reverse)
        }
    }

    execute(in, Seq.empty, Option.empty)
  }

  private type IsProduct[U <: Product] = U
end MacroImpl
