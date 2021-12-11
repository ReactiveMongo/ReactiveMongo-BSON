import scala.util.{ Failure, Success }

import scala.deriving.Mirror

import reactivemongo.api.bson.{
  BSONArray,
  BSONDocument,
  BSONDocumentReader,
  BSONDocumentWriter,
  BSONInteger,
  BSONReader,
  BSONString,
  BSONWriter,
  Macros
}

import Macros.Annotations.{
  DefaultValue,
  Flatten,
  Ignore,
  Key,
  NoneAsNull,
  Reader,
  Writer
}

trait MacroTestCompat { _self: MacroTest.type =>
  given Conversion[Union.UB, Tuple1[String]] = (ub: Union.UB) => Tuple(ub.s)

  implicit object ProductOfUB extends Mirror.Product {
    type MirroredType = Union.UB
    type MirroredElemTypes = Tuple1[String]
    type MirroredMonoType = Union.UB
    type MirroredLabel = "UB"
    type MirroredElemLabels = Tuple1["s"]

    def fromProduct(p: Product): MirroredMonoType =
      new Union.UB(p.productElement(0).asInstanceOf[String])
  }
}

object MacroTest extends MacroTestCompat {
  case class Person(firstName: String, lastName: String)
  case class Pet(name: String, owner: Person)

  case class Primitives(
      dbl: Double,
      str: String,
      bl: Boolean,
      int: Int,
      long: Long)

  object Union {
    sealed trait UT

    case class UA(n: Int) extends UT

    class UB(val s: String) extends UT

    object UB {

      implicit val handler: BSONDocumentWriter[UB] =
        Macros.writer[UB] // TODO: Handler[UB] = Macros.handler[UB]

    }

    case class UC(s: String) extends UT
    case class UD(s: String) extends UT
    object UE extends UT
    case object UF extends UT

    trait UT2
    case class UA2(n: Int) extends UT2
    case class UB2(s: String) extends UT2

    case object DoNotExtendsA
    object DoNotExtendsB
  }

  case class Bar(name: String, next: Option[Bar])

  final class FooVal(val v: Int) extends AnyVal

  final class BarVal(val v: Exception) extends AnyVal

  case class OptionalAsNull(name: String, @NoneAsNull value: Option[String])
  case class OptionalGeneric[T](v: Int, opt: Option[T])

  case class Single(value: BigDecimal)

  // TODO: Remove; Only for Scala 2 tests
  case class WithImplicit1(pos: Int, text: String)(implicit x: Numeric[Int]) {
    def test = x
  }

  // TODO: Remove; Only for Scala 2 tests
  @com.github.ghik.silencer.silent
  case class WithImplicit2[N: Numeric](ident: String, value: N)

  case class Foo[T](bar: T, lorem: String)

  case class NotIgnorable(@Ignore title: String, score: Int)

  case class Pair(@Ignore left: String = "_left", right: String)

  case class IgnoredAndKey(
      @Ignore @DefaultValue(Person("first", "last")) a: Person,
      @Key("second") b: String)

  case class Range(start: Int, end: Int)

  object Range {
    /* TODO
    implicit val handler: BSONDocumentHandler[Range]=
      Macros.handler[Range]
     */

    // TODO: Remove
    implicit val writer: BSONDocumentWriter[Range] =
      Macros.writer[Range]

    // TODO: Remove
    implicit val reader: BSONDocumentReader[Range] =
      Macros.reader[Range]
  }

  // Flatten
  case class LabelledRange(
      name: String,
      @Flatten range: Range)

  case class InvalidRecursive(
      property: String,
      @Flatten parent: InvalidRecursive)

  case class InvalidNonDoc(@Flatten name: String)

  // ---

  case class WithDefaultValues1(
      id: Int,
      title: String = "default1",
      score: Option[Float] = Some(1.23F),
      range: Range = Range(3, 5))

  case class WithDefaultValues2(
      id: Int,
      @DefaultValue("default2") title: String,
      @DefaultValue(Some(45.6F)) score: Option[Float],
      @DefaultValue(Range(7, 11)) range: Range)

  case class WithDefaultValues3(
      @DefaultValue(1 /* type mismatch */ ) name: String)

  // ---

  val strStatusReader = BSONReader.collect[String] {
    case BSONInteger(1) => "on"
    case BSONInteger(0) => "off"
    case BSONString(st) => st
  }

  val scoreWriter = BSONWriter[Float] { f => BSONString(f.toString) }

  val descrReader = BSONReader.collect[Option[String]] {
    case BSONInteger(0) => None
    case BSONString(st) => Some(st)
  }

  val descrWriter = BSONWriter[Option[String]] {
    case Some(str) => BSONString(str)
    case _         => BSONInteger(0)
  }

  val rangeSeqReader = BSONDocumentReader.from[Range] { doc =>
    doc.getAsTry[Seq[Int]]("range").flatMap {
      case start +: end +: _ =>
        Success(Range(start, end))

      case _ =>
        Failure(new IllegalArgumentException())
    }
  }

  val rangeSeqWriter = BSONDocumentWriter[Range] { range =>
    BSONDocument("range" -> BSONArray(range.start, range.end))
  }

  case class PerField1[T](
      id: Long,
      @Reader(strStatusReader) status: String,
      @Writer(scoreWriter) score: Float,
      @Writer(descrWriter) @Reader(descrReader) description: Option[
        String
      ],
      @Flatten @Writer(
        rangeSeqWriter
      ) @Reader(rangeSeqReader) range: Range,
      foo: T)

  case class PerField2(
      @Reader(implicitly[BSONReader[Int]]) @Writer(
        descrWriter
      ) name: String)

  case class WithMap1(
      name: String,
      localizedDescription: Map[java.util.Locale, String])

  case class WithMap2(
      name: String,
      values: Map[FooVal, String])
}
