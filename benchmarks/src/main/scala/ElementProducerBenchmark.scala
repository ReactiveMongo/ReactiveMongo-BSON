package reactivemongo

import scala.util.Random

import org.openjdk.jmh.annotations._

import reactivemongo.api.bson.{
  BSONArray,
  BSONDocument,
  BSONElement,
  BSONElementSet,
  BSONString,
  BSONValue,
  ElementProducer
}

@State(Scope.Benchmark)
class BSONArrayBenchmark
  extends ElementProducerCompositionBenchmark[BSONArray] {

  val values: Seq[BSONArray] =
    BSONValueFixtures.bsonArrayFixtures.filterNot(_.isEmpty)

  protected def elements(producer: BSONArray) = producer.elements
}

@State(Scope.Benchmark)
class BSONDocumentBenchmark
  extends ElementProducerCompositionBenchmark[BSONDocument] {

  val values: Seq[BSONDocument] =
    BSONValueFixtures.bsonDocFixtures.filterNot(_.isEmpty)

  protected def elements(producer: BSONDocument) = producer.elements

  @Benchmark
  def remove() = _1.remove(key)

  @Benchmark
  def prettyPrint() = BSONDocument.pretty(_2) // TODO: Also for BSONArray

  @Benchmark
  def elementsFactory_1() = BSONDocument(_1.elements: _*)

  @Benchmark
  def elementsFactory_2() = BSONDocument(_2.toMap)

  // TODO: write, read
}

// ---

sealed trait ElementProducerCompositionBenchmark[P <: BSONElementSet] {
  protected def values: Seq[P]

  private def it = Random.shuffle(values).iterator

  private def gen(): Iterator[P] = it ++ gen()

  private var fixtures: Iterator[P] = Iterator.empty

  protected var _1: P = _
  protected var _2: P = _
  protected var key: String = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    val data: (Option[P], Option[P]) = {
      if (fixtures.hasNext) {
        Option(fixtures.next()) -> Option(fixtures.next())
      } else {
        Option.empty[P] -> Option.empty[P]
      }
    }

    data match {
      case (Some(a), Some(b)) => {
        _1 = a
        _2 = b

        Random.shuffle(_1.elements).headOption match {
          case Some(BSONElement(k, _)) =>
            key = k

          case _ =>
            sys.error(s"No element found: $a")
        }
      }

      case _ => {
        fixtures = gen()
        setup()
      }
    }
  }

  @Benchmark
  final def compose(): ElementProducer = ElementProducer.Composition(_1, _2)

  @Benchmark
  final def elements_2() = _2.elements

  @Benchmark
  final def values_1() = _1.values

  @Benchmark
  final def headOption: Option[BSONElement] = _1.headOption

  @Benchmark
  final def isEmpty: Boolean = _2.isEmpty

  @Benchmark
  final def size: Int = _1.size

  @Benchmark
  final def prepend(): P#SetType =
    BSONElement("_prepend", BSONString(key)) ~: _1

  @Benchmark
  final def get(): Option[BSONValue] = _1.get(key)

  @Benchmark
  final def contains(): Boolean = _1.contains(key)
}
