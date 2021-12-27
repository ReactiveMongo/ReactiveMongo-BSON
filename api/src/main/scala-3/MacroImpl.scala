package reactivemongo.api.bson

import scala.util.{
  Failure => TryFailure,
  Success => TrySuccess,
  Try => TryResult
}

import scala.collection.View
import scala.collection.mutable.{ Builder => MBuilder }

import scala.deriving.Mirror.ProductOf
import scala.quoted.{ Expr, Quotes, Type }
import scala.reflect.ClassTag

import exceptions.{
  HandlerException,
  TypeDoesNotMatchException,
  ValueDoesNotMatchException
}

// TODO: Enum
/* TODO: Double config
// Reader
[info]     |{
[info]     |  val config: reactivemongo.api.bson.MacroConfiguration = reactivemongo.api.bson.MacroConfiguration.default[Opts](reactivemongo.api.bson.MacroOptions.ValueOf.optionsDefault)
[info]     |  val `config₂`: reactivemongo.api.bson.MacroConfiguration = reactivemongo.api.bson.MacroConfiguration.default[Opts](reactivemongo.api.bson.MacroOptions.ValueOf.optionsDefault)
 */
private[api] object MacroImpl:
  import Macros.Annotations,
  Annotations.{ DefaultValue, Ignore, Key, Writer, Flatten, NoneAsNull, Reader }

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
      val underlying = f(self)
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
      val optsTpr = TypeRepr.of(using optsTpe)

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

      //TODO: helper.debug(s"// Value reader\n${reader.show}")

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
    val underlying = f(self)
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
      val optsTpr = TypeRepr.of(using optsTpe)

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

  private def ensureFindType[A](using q: Quotes, tpe: Type[A]): Unit = {
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
      withSelfDocReader {
        (forwardBSONReader: BSONDocumentReader[A]) =>
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
      val underlying = f(self)
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
      withSelfDocWriter {
        (forwardBSONWriter: BSONDocumentWriter[A]) =>
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

    val whelper = createWriterHelper[A, Opts](config)
    val rhelper = createReaderHelper[A, Opts](config)

    // TODO: HandlerHelper that mix MacroHelpers with WriterHelpers with ReaderHelpers to avoid instanciated both separately

    '{
      val writer = withSelfDocWriter {
        (forwardBSONWriter: BSONDocumentWriter[A]) =>
          { (macroVal: A) =>
            ${ whelper.writeBody('{ macroVal }, '{ forwardBSONWriter }) }
          }
      }

      val reader = withSelfDocReader {
        (forwardBSONReader: BSONDocumentReader[A]) =>
          { (macroVal: BSONDocument) =>
            ${ rhelper.readBody('{ macroVal }, '{ forwardBSONReader }) }
          }
      }

      BSONDocumentHandler.provided[A](reader, writer)
    }
  }

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
      val optsTpr = TypeRepr.of[Opts](using optsTpe)
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
      val optsTpr = TypeRepr.of[Opts](using optsTpe)
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

      //debug(s"// Writer\n${writer.show}")

      writer
    }
  }

  // ---

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

    protected final lazy val flattenRepr: TypeRepr =
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
              Map.empty,
              forwardExpr,
              debug
            )(docReaderType)

            val cases = types.zipWithIndex.map { (tpr, i) =>
              tpr.asType match {
                case tt @ '[at] =>
                  readDiscriminatedCase[at](
                    macroVal,
                    discriminator,
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
              Map.empty,
              forwardExpr,
              debug
            )(docReaderType)

            type Subtype[U <: A] = U

            val cases = types.zipWithIndex.map { (tpr, i) =>
              tpr.asType match {
                case tt @ '[Subtype[at]] =>
                  readDiscriminatedCase[at](
                    macroVal,
                    discriminator, {
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
                    val subHelper = createSubHelper[T](tpe, tpr)

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
        type IsProduct[U <: Product] = U

        tpe match {
          case '[IsProduct[t]] =>
            Expr.summon[ProductOf[t]] match {
              case Some(pof) =>
                productReader[t, t](
                  macroCfg,
                  macroVal,
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

    private def singletonReader[T](using tpe: Type[T]): Expr[TrySuccess[T]] =
      Expr.summon[ValueOf[T]] match {
        case Some(vof) =>
          '{ TrySuccess(${ vof }.value) }

        case _ =>
          report.errorAndAbort(
            s"Something weird is going on with '${prettyType(TypeRepr.of(using tpe))}'. Should be a singleton but can't parse it"
          )
      }

    private type ReadableProperty =
      Tuple5[Symbol, Int, TypeRepr, /*default:*/ Option[
        Expr[_]
      ], /*reader:*/ Option[Expr[BSONReader[_]]]]

    private object ReadableProperty {

      def apply(
          symbol: Symbol,
          index: Int,
          tpr: TypeRepr,
          default: Option[Expr[_]],
          reader: Option[Expr[BSONReader[_]]]
        ): ReadableProperty = Tuple5(symbol, index, tpr, default, reader)

      def unapply(p: ReadableProperty) = Some(p)
    }

    /**
     * @param macroVal the value to be read
     * @param tpe the value type
     */
    private def productReader[T, U <: Product](
        macroCfg: Expr[MacroConfiguration],
        macroVal: Expr[BSONDocument],
        forwardExpr: Expr[BSONDocumentReader[T]],
        pof: Expr[ProductOf[T]]
      )(using
        tpe: Type[T],
        ptpe: Type[U]
      ): Expr[TryResult[T]] = {
      val tpr = TypeRepr.of[T](using tpe)
      val tprElements = productElements[T, U](tpr, pof) match {
        case TryFailure(cause) =>
          report.errorAndAbort(cause.getMessage)

        case TrySuccess(elms) =>
          elms
      }

      val types = tprElements.map(_._2)

      val resolve =
        resolver[BSONReader, T](Map.empty, forwardExpr, debug)(readerType)

      val compCls = tpr.typeSymbol.companionClass

      val (optional, required) = tprElements.zipWithIndex.view.map {
        case ((sym, pt), i) =>
          pt.asType match {
            case '[t] =>
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

                case a =>
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
                      Ref(defaultSym).asExprOf[t]
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

              ReadableProperty(sym, i, pt, default, readerFromAnn)
          }
      }.partition {
        case ReadableProperty(_, _, t, _, readerFromAnn) =>
          readerFromAnn.isEmpty && isOptionalType(t)

      }

      type ExceptionAcc = MBuilder[HandlerException, Seq[HandlerException]]

      def withIdents[U: Type](
          f: Function2[Expr[MacroConfiguration], /* err */ Expr[
            ExceptionAcc
          ], Expr[U]]
        ): Expr[U] = '{
        val err = Seq.newBuilder[HandlerException]

        ${ f(macroCfg, '{ err }) }
      }

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

      withIdents[TryResult[T]] { (config, bufErr) =>
        val reqElmts: View[(Int, Expr[TryResult[_]])] = required.map {
          case ReadableProperty(param, n, pt, Some(default), _)
              if (ignoreField(param)) =>
            pt.asType match {
              case '[p] => n -> '{ TrySuccess(${ default.asExprOf[p] }) }
            }

          case ReadableProperty(param, _, _, None, _) if (ignoreField(param)) =>
            report.errorAndAbort(s"Cannot ignore '${prettyType(tpr)}.${param.name}': no default value (see @DefaultValue)")

          case ReadableProperty(param, n, pt, default, readerFromAnn) => {
            pt.asType match {
              case ptpe @ '[p] =>
                val pname = param.name
                val reader: Expr[BSONReader[p]] = readerFromAnn.fold(
                  resolveReader[p](pname, pt, resolve)
                )(_.asExprOf[BSONReader[p]])

                val get: Expr[TryResult[p]] = {
                  if (mustFlatten(tpr, param, pt, reader)) {
                    val readTry = '{ ${ reader }.readTry(${ macroVal }) }

                    default.fold(readTry) { dv =>
                      tryWithDefault[p](readTry, dv.asExprOf[p])
                    }
                  } else {
                    val field = fieldName(config, fieldKey(param))
                    val getAsTry = '{
                      ${ macroVal }.getAsTry[p]($field)($reader)
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

        val exElmts: View[(Int, Expr[TryResult[_]])] = optional.map {
          case p @ ReadableProperty(_, _, OptionTypeParameter(it), _, _) =>
            p.copy(_3 = it)

          case ReadableProperty(param, _, pt, _, _) =>
            report.errorAndAbort(
              s"Invalid optional field '${param.name}': ${prettyType(pt)}"
            )

        }.map {
          case ReadableProperty(param, n, pt, Some(default), _)
              if (ignoreField(param)) =>
            pt.asType match {
              case '[p] => n -> '{ TrySuccess(${ default.asExprOf[p] }) }
            }

          case ReadableProperty(param, _, _, None, _) if (ignoreField(param)) =>
            report.errorAndAbort(s"Cannot ignore '${prettyType(tpr)}.${param.name}': not default value (see @DefaultValue)")

          case ReadableProperty(
                param,
                n,
                it,
                default,
                Some(readerFromAnn)
              ) => {
            it.asType match {
              case '[i] =>
                type p = Option[i]
                val pt = TypeRepr.of[p]

                val pname = param.name
                val field = fieldName(config, fieldKey(param))
                val reader = readerFromAnn.asExprOf[BSONReader[p]]

                val readTry: Expr[TryResult[p]] = {
                  if (mustFlatten(tpr, param, pt, reader)) {
                    '{ ${ reader }.readTry(${ macroVal }) }
                  } else {
                    '{ ${ macroVal }.getAsTry[p]($field)($reader) }
                  }
                }

                n -> default.fold(readTry) { dv =>
                  tryWithOptDefault[i](readTry, dv.asExprOf[p])
                }
            }
          }

          case ReadableProperty(
                param,
                n,
                it,
                default,
                None
              ) => {
            val pname = param.name

            it.asType match {
              case '[i] =>
                val reader: Expr[BSONReader[i]] =
                  resolveReader(pname, it, resolve)

                type p = Option[i]

                val field = fieldName(config, fieldKey(param))

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
          trySeq(${ Expr.ofList(tupElmts.toList) }).map { ls =>
            ${ pof }.fromProduct(Tuple fromArray ls.toArray)
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
        reader: Expr[BSONReader[T]]
      ): Boolean = {
      if (param.annotations.exists(_.tpe =:= flattenRepr)) {
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
     *
     * @param pname the parameter/field name (for debug)
     * @param tpr the representation of the `U` field type
     * @param annotatedReader the reader from annotation
     */
    def resolveReader[T](
        pname: String,
        tpr: TypeRepr,
        resolve: TypeRepr => Option[Implicit]
      )(using
        tpe: Type[T]
      ): Expr[BSONReader[T]] = {
      resolve(tpr) match {
        case Some((reader, _)) =>
          reader.asExprOf[BSONReader[T]]

        case None => {
          if (!hasOption[MacroOptions.AutomaticMaterialization]) {
            report.errorAndAbort(s"No implicit found for '${prettyType(aTpeRepr)}.$pname': ${classOf[BSONReader[_]].getName}[${prettyType(tpr)}]")
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

                '{
                  given leafReader: BSONReader[at] =
                    BSONReader.from[at](${ tryRead })

                  scala.compiletime.summonInline[BSONReader[T]]
                }
              }

              case tt @ '[at] => {
                val subHelper = createSubHelper[at](tt, lt)

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

                // TODO: Common with previous leafWriter Expr
                '{
                  given leafReader: BSONDocumentReader[at] =
                    BSONDocumentReader.from[at](${ tryRead })

                  scala.compiletime.summonInline[BSONReader[T]]
                }
              }
            }
          }
        }
      }
    }

    private def createSubHelper[T](tt: Type[T], tpr: TypeRepr) =
      new MacroHelpers[T]
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
            Map.empty,
            forwardExpr,
            debug
          )(docWriterType)

          val cases = types.zipWithIndex.map { (tpr, i) =>
            tpr.asType match {
              case tt @ '[at] =>
                writeDiscriminatedCase[at](
                  macroVal,
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
                  config,
                  resolve,
                  tpr,
                  name = s"macroTpe${i}"
                )(using tt)

              case notChild @ '[t] =>
                report.errorAndAbort(
                  s"Not child: ${prettyType(TypeRepr.of(using notChild))}"
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
        forwardBSONWriter: Expr[BSONDocumentWriter[T]],
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
        givenWriter[T](be, tpe, tpr)(resolve).getOrElse {
          if (hasOption[MacroOptions.AutomaticMaterialization]) {
            // No existing implicit, but can fallback to automatic mat
            subHelper.writeBodyConstruct[T](
              config,
              be,
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
                  macroVal.asExprOf[t],
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
                      macroVal.asExprOf[t],
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
        forwardExpr: Expr[BSONDocumentWriter[T]],
        toProduct: Expr[T => U],
        pof: Expr[ProductOf[T]]
      )(using
        tpe: Type[T],
        ptpe: Type[U]
      ): Expr[TryResult[BSONDocument]] = {
      val tpr = TypeRepr.of[T](using tpe)
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
          resolver[BSONWriter, T](Map.empty, forwardExpr, debug)(writerType)

        val (optional, required) = tprElements.zipWithIndex.view.filterNot {
          case ((sym, _), _) => ignoreField(sym)
        }.map {
          case ((sym, pt), i) =>
            pt.asType match {
              case '[t] =>
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

                  case a =>
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

                WritableProperty(sym, i, pt, writerFromAnn)
            }
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
                  TryFailure[BSONDocument](error suppress acc.tail)

                case _ =>
                  TrySuccess(BSONDocument(${ bufOk }.result(): _*))
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
      if (param.annotations.exists(_.tpe =:= flattenRepr)) {
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

                '{
                  given leafWriter: BSONWriter[at] =
                    BSONWriter.from[at](${ tryWrite })

                  scala.compiletime.summonInline[BSONWriter[T]]
                }
              }

              case tt @ '[at] => {
                val subHelper = createSubHelper[at](tt, lt)

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

                // TODO: Common with previous leafWriter Expr
                '{
                  given leafWriter: BSONWriter[at] =
                    BSONWriter.from[at](${ tryWrite })

                  scala.compiletime.summonInline[BSONWriter[T]]
                }
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

  sealed trait MacroHelpers[A] extends OptionSupport with MacroLogging {
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
          tpr match {
            case AppliedType(_, args) =>
              args.headOption

            case _ =>
              None
          }
        } else None
      }
    }
  }

  sealed trait MacroTopHelpers[A] extends MacroHelpers[A] {
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

  sealed trait MacroLogging { _self: OptionSupport =>
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

  sealed trait OptionSupport {
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

      debug {
        val show: Option[String] =
          try {
            neededGiven.map(_.show)
          } catch {
            case e: MatchError /* Dotty bug */ =>
              neededGiven.map(_.symbol.fullName)
          }

        s"// Resolve given ${prettyType(TypeRepr.of(using tc))} for ${prettyType(ntpe)} as ${prettyType(
          neededGivenType
        )} (self? ${selfRef}) = ${show.mkString}"
      }

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

    private def fullName(sym: Symbol): String =
      sym.fullName.replaceAll("(\\.package\\$|\\$|scala\\.Predef\\$\\.)", "")

    // To print the implicit types in the compiler messages
    protected final def prettyType(t: TypeRepr): String = t match {
      case _ if (t <:< TypeRepr.of[EmptyTuple]) =>
        "EmptyTuple"

      case AppliedType(ty, a :: b :: Nil) if (ty <:< TypeRepr.of[*:]) =>
        s"${prettyType(a)} *: ${prettyType(b)}"

      case AppliedType(_, args) =>
        fullName(t.typeSymbol) + args.map(prettyType).mkString("[", ", ", "]")

      case OrType(a, b) =>
        s"${prettyType(a)} | ${prettyType(b)}"

      case _ =>
        fullName(t.typeSymbol)
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

    protected final lazy val anyValTpe: TypeRepr = TypeRepr.of[AnyVal]

    /**
     * Recursively find the sub-classes of `tpr`.
     *
     * Sub-abstract types are not listed, but their own sub-types are examined;
     * e.g. for trait `Foo`
     *
     * {{{
     * sealed trait Foo
     * case class Bar(name: String) extends Foo
     * sealed trait SubFoo extends Foo
     * case class Lorem() extends SubFoo
     * }}}
     *
     * Class `Lorem` is listed through `SubFoo`,
     * but `SubFoo` itself is not returned.
     */
    final def knownSubclasses(tpr: TypeRepr): Option[List[TypeRepr]] =
      tpr.classSymbol.flatMap { cls =>
        @annotation.tailrec
        def subclasses(
            children: List[Tree],
            out: List[TypeRepr]
          ): List[TypeRepr] = {
          val childTpr = children.headOption.collect {
            case tpd: Typed =>
              tpd.tpt.tpe

            case vd: ValDef =>
              vd.tpt.tpe

            case cd: ClassDef =>
              cd.constructor.returnTpt.tpe

          }

          childTpr match {
            case Some(child) => {
              val tpeSym = child.typeSymbol

              if (
                (tpeSym.flags.is(Flags.Abstract) &&
                  tpeSym.flags.is(Flags.Sealed) &&
                  !(child <:< anyValTpe)) ||
                (tpeSym.flags.is(Flags.Sealed) &&
                  tpeSym.flags.is(Flags.Trait))
              ) {
                // Ignore sub-trait itself, but check the sub-sub-classes
                subclasses(tpeSym.children.map(_.tree) ::: children.tail, out)
              } else {
                subclasses(children.tail, child :: out)
              }
            }

            case _ =>
              out.reverse
          }
        }

        val types = subclasses(cls.children.map(_.tree), Nil)

        if (types.isEmpty) None else Some(types)
      }

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
     * @param tpr the type for which a `ProductOf` is provided
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
        Type[T],
        Type[U]
      ): Tuple2[TypeRepr, Expr[T] => (Expr[U] => Expr[R]) => Expr[R]] = {
      val unappliedTupleTpr: TypeRepr = {
        if (types.isEmpty) {
          TypeRepr.of[EmptyTuple]
        } else {
          TypeRepr.typeConstructorOf(Class.forName(s"scala.Tuple${types.size}"))
        }
      }

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
      ): TryResult[List[(Symbol, TypeRepr)]] = { // TODO: Remove Symbol?

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

        case Some(TypeBounds(t, _)) =>
          elementTypes(max, t :: tpes.tail, ts)

        case Some(t) =>
          elementTypes(max, tpes.tail, t :: ts)

        case _ =>
          ts.reverse
      }

      val ownerSym = owner.typeSymbol
      val paramss = ownerSym.primaryConstructor.paramSymss.flatten.map { s =>
        s.name -> s
      }.toMap

      def prepare(
          elmLabels: TypeRepr,
          elmTypes: TypeRepr
        ): List[(Symbol, TypeRepr)] = {
        val names =
          elementTypes(Int.MaxValue, List(elmLabels), List.empty).collect {
            case ConstantType(StringConstant(n)) => n
          }

        lazyZip(
          names,
          elementTypes(names.size, List(elmTypes), List.empty)
        ).map {
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

      val elements: Option[List[(Symbol, TypeRepr)]] = pof.asTerm.tpe match {
        case Refinement(
              Refinement(_, _, TypeBounds(t1 @ AppliedType(tycon1, _), _)),
              _,
              TypeBounds(t2 @ AppliedType(tycon2, _), _)
            )
            if (tycon1 <:< TypeRepr.of[Product] && tycon2 <:< TypeRepr
              .of[Product]) =>
          // TODO: Merge cases ?
          Option(prepare(t2, t1))

        case Refinement(
              ref @ Refinement(_, _, TypeBounds(t1 @ TermRef(_, _), _)),
              _,
              TypeBounds(t2 @ TermRef(_, _), _)
            ) if {
              val emptyTupTpe = TypeRepr.of[EmptyTuple]
              (Ref.term(t1).tpe <:< emptyTupTpe && Ref
                .term(t2)
                .tpe <:< emptyTupTpe)
            } =>
          None

        case pofTpe =>
          pofTpe.dealias.typeSymbol.tree match {
            case ClassDef(_, _, _, _, members) =>
              members.collect {
                case TypeDef(
                      n @ ("MirroredElemTypes" | "MirroredElemLabels"),
                      tt: TypeTree
                    ) if (tt.tpe <:< TypeRepr.of[Product]) =>
                  n -> tt.tpe
              }.sortBy(_._1) match {
                case (_, elmLabels) :: (_, elmTypes) :: Nil =>
                  Option(prepare(elmLabels, elmTypes))

                case _ =>
                  Some(List.empty[(Symbol, TypeRepr)])
              }

            case _ =>
              Some(List.empty[(Symbol, TypeRepr)])
          }
      }

      elements match {
        case Some(ls) if (elements.isEmpty) =>
          TryFailure(
            new IllegalArgumentException(
              s"Ill-typed ProductOf[${owner.typeSymbol.fullName}]: Fails to resolve element types and labels (bad refinement?)"
            )
          )

        case Some(ls) =>
          TrySuccess(ls)

        case _ =>
          TrySuccess(List.empty[(Symbol, TypeRepr)])
      }
    }
  }

  private inline def trySeq[T](in: List[TryResult[T]]): TryResult[Seq[T]] = {
    @annotation.tailrec
    def execute[T](
        in: List[TryResult[T]],
        suc: List[T],
        fail: Option[Throwable]
      ): TryResult[List[T]] = in.headOption match {
      case Some(TrySuccess(v)) =>
        execute(in.tail, v :: suc, fail)

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

    execute[T](in, List.empty, Option.empty)
  }

end MacroImpl
