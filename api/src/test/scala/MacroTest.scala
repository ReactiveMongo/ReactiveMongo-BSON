import scala.util.{ Failure, Success, Try }

import reactivemongo.api.bson.{ BSONArray, BSONDocument, BSONDocumentHandler, BSONDocumentReader, BSONDocumentWriter, BSONHandler, BSONInteger, BSONObjectID, BSONReader, BSONString, BSONWriter, MacroOptions, Macros }
import reactivemongo.api.bson.Macros.Annotations.{
  DefaultValue,
  Flatten,
  Ignore,
  Key,
  NoneAsNull,
  Reader,
  Writer
}

object MacroTest {
  type Handler[A] = BSONDocumentReader[A] with BSONDocumentWriter[A] with BSONHandler[A]

  case class Person(firstName: String, lastName: String)
  case class Pet(name: String, owner: Person)
  case class Primitives(dbl: Double, str: String, bl: Boolean, int: Int, long: Long)

  case class Optional(name: String, value: Option[String])
  case class OptionalAsNull(name: String, @NoneAsNull value: Option[String])
  case class OptionalSingle(value: Option[String])
  case class OptionalGeneric[T](v: Int, opt: Option[T])

  case class Single(value: BigDecimal)
  case class SingleTuple(value: (String, String))
  case class User(_id: BSONObjectID = BSONObjectID.generate(), name: String)
  case class WordLover(name: String, words: Seq[String])
  case class Empty()
  object EmptyObject

  case class WithImplicit1(pos: Int, text: String)(implicit x: Numeric[Int]) {
    def test = x
  }

  @com.github.ghik.silencer.silent(".*parameter.*WithImplicit2\\ is\\ never\\ used.*")
  case class WithImplicit2[N: Numeric](ident: String, value: N)

  case class RenamedId(
    @Key("_id") myID: BSONObjectID = BSONObjectID.generate(),
    @CustomAnnotation value: String)

  case class Foo[T](bar: T, lorem: String)
  case class Bar(name: String, next: Option[Bar])

  case class GenSeq[A](items: Seq[A], count: Int)

  object Nest {
    case class Nested(name: String)
  }

  case class OverloadedApply(string: String)
  object OverloadedApply {
    val apply: Int => Unit = _ => (); //println(n)

    def apply(seq: Seq[String]): OverloadedApply =
      OverloadedApply(seq mkString " ")
  }

  case class OverloadedApply2(string: String, number: Int)
  object OverloadedApply2 {
    def apply(string: String): OverloadedApply2 = OverloadedApply2(string, 0)
  }

  case class OverloadedApply3(string: String, number: Int)
  object OverloadedApply3 {
    def apply(): OverloadedApply3 = OverloadedApply3("", 0)
  }

  object Union {
    sealed trait UT
    case class UA(n: Int) extends UT

    class UB(val s: String) extends UT {
      override def equals(that: Any): Boolean = that match {
        case other: UB => s == other.s
        case _ => false
      }

      override def hashCode: Int = s.hashCode
    }

    object UB {
      def apply(s: String): UB = new UB(s)
      def unapply(ub: UB): Option[String] = Some(ub.s)

      implicit val handler: Handler[UB] = Macros.handler[UB]
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

  trait NestModule {
    case class Nested(name: String)
    val format: Handler[Nested] = Macros.handler[Nested]
  }

  object TreeModule {
    /*
     Due to compiler limitations (read: only workaround I found),
     handlers must be defined here and explicit type annotations added
     to enable compiler to use implicit handlers recursively.
     */

    sealed trait Tree

    case class Node(left: Tree, right: Tree) extends Tree
    implicit val nodeHandler: Handler[Node] = Macros.handler[Node]

    case class Leaf(data: String) extends Tree
    implicit val leafHandler: Handler[Leaf] = Macros.handler[Leaf]

    object Tree {
      import MacroOptions._

      val x = Macros.readerOpts[Tree, UnionType[Node \/ Leaf]]

      implicit val bson: Handler[Tree] =
        Macros.handlerOpts[Tree, UnionType[Node \/ Leaf]]
    }
  }

  object TreeCustom {
    sealed trait Tree

    case class Node(left: Tree, right: Tree) extends Tree
    implicit val nodeHandler: Handler[Node] = Macros.handler[Node]

    case class Leaf(data: String) extends Tree

    object Leaf {
      private val helper: Handler[Leaf] = Macros.handler[Leaf]

      implicit val bson: Handler[Leaf] = new BSONDocumentHandler[Leaf] {
        def writeTry(t: Leaf): Try[BSONDocument] =
          helper.writeTry(Leaf("hai"))

        def readDocument(bson: BSONDocument): Try[Leaf] = helper readTry bson
      }
    }

    object Tree {
      import MacroOptions._

      implicit val bson: Handler[Tree] =
        Macros.handlerOpts[Tree, UnionType[Node \/ Leaf]]
      //Macros.handlerOpts[Tree, UnionType[Node \/ Leaf] with Verbose]
    }
  }

  object IntListModule {
    sealed trait IntList

    case class Cons(head: Int, tail: IntList) extends IntList
    implicit val consHandler: Handler[Cons] = Macros.handler[Cons]

    case object Tail extends IntList
    implicit val tailHandler: Handler[Tail.type] = Macros.handler[Tail.type]

    object IntList {
      import MacroOptions.{ UnionType, \/ }

      implicit val bson: Handler[IntList] =
        Macros.handlerOpts[IntList, UnionType[Cons \/ Tail.type]]
    }
  }

  object InheritanceModule {
    sealed trait T

    case class A() extends T
    implicit val ah: Handler[A] = Macros.handlerOpts[A, MacroOptions.Verbose]

    case object B extends T
    implicit val bh: Handler[B.type] = Macros.handler[B.type]

    sealed trait TT extends T
    case class C() extends TT
    implicit val ch: Handler[C] = Macros.handler[C]
  }

  case class NotIgnorable(@Ignore title: String, score: Int)

  case class Pair(@Ignore left: String = "_left", right: String)

  case class IgnoredAndKey(
    @Ignore @DefaultValue(Person("first", "last")) a: Person,
    @Key("second") b: String)

  case class Range(start: Int, end: Int)

  object Range {
    implicit val handler = Macros.handler[Range]
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

  val scoreWriter = BSONWriter[Float] { f =>
    BSONString(f.toString)
  }

  val descrReader = BSONReader.collect[Option[String]] {
    case BSONInteger(0) => None
    case BSONString(st) => Some(st)
  }

  val descrWriter = BSONWriter[Option[String]] {
    case Some(str) => BSONString(str)
    case _ => BSONInteger(0)
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
    @Writer(descrWriter)@Reader(descrReader) description: Option[String],
    @Flatten @Writer(rangeSeqWriter)@Reader(rangeSeqReader) range: Range,
    foo: T)

  case class PerField2(
    @Reader(implicitly[BSONReader[Int]])@Writer(descrWriter) name: String)

  final class FooVal(val v: Int) extends AnyVal

  final class BarVal(val v: Exception) extends AnyVal

  case class WithMap1(
    name: String,
    localizedDescription: Map[java.util.Locale, String])

  case class WithMap2(
    name: String,
    values: Map[FooVal, String])

  case class Person2(
    name: String,
    age: Int,
    phoneNum: Long,
    itemList: Seq[Item],
    list: Seq[Int])

  case class Item(name: String, number: FooVal)
}
