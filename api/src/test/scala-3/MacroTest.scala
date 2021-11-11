import scala.util.{ Failure, Success }

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
}, Macros.Annotations.{ DefaultValue, Flatten, Ignore, Key, NoneAsNull, Writer }

object MacroTest {
  case class Person(firstName: String, lastName: String)

  object Union {
    sealed trait UT
  }

  case class Bar(name: String, next: Option[Bar])

  final class FooVal(val v: Int) extends AnyVal

  case class OptionalAsNull(name: String, @NoneAsNull value: Option[String])
  case class OptionalGeneric[T](v: Int, opt: Option[T])

  case class Single(value: BigDecimal)

  case class WithImplicit1(pos: Int, text: String)(implicit x: Numeric[Int]) {
    def test = x
  }

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

    implicit val handler: BSONDocumentWriter[Range] /* TODO: BSONDocumentHandler[Range] */ =
      Macros.writer[Range] //TODO: Macros.handler[Range]
  }

  // Flatten
  case class LabelledRange(
      name: String,
      @Flatten range: Range)

  case class InvalidRecursive(
      property: String,
      @Flatten parent: InvalidRecursive)

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
      /* TODO: @Reader(strStatusReader) */ status: String,
      @Writer(scoreWriter) score: Float,
      @Writer(descrWriter) /* TODO: @Reader(descrReader) */ description: Option[
        String
      ],
      @Flatten @Writer(
        rangeSeqWriter
      ) /* TODO: @Reader(rangeSeqReader) */ range: Range,
      foo: T)

  case class PerField2(
      /* TODO: @Reader(implicitly[BSONReader[Int]]) */ @Writer(
        descrWriter
      ) name: String)

  case class WithMap1(
      name: String,
      localizedDescription: Map[java.util.Locale, String])
}
