package reactivemongo
package api.bson

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class BSONNumberLikeHandlerBenchmark {

  val values: List[BSONValue] =
    BSONValueFixtures.bsonIntFixtures ++ BSONValueFixtures.bsonDoubleFixtures ++ BSONValueFixtures.bsonLongFixtures ++ BSONValueFixtures.bsonDecimalFixtures ++ BSONValueFixtures.bsonDateTimeFixtures ++ BSONValueFixtures.bsonTsFixtures

  lazy val handler = BSONNumberLike.Handler
  @inline def unsafeHandler: BSONReader[_] = handler

  @Benchmark
  def valueAsSuccessfulTry() = values.foreach { v =>
    assert(v.asTry(handler).isSuccess)
    assert((v: BSONValue).asTry(unsafeHandler).isSuccess)
  }

  @Benchmark
  def valueAsFailedTry() = values.foreach { v =>
    assert(!(v: BSONValue).asTry[Unsupported.type].isSuccess)
  }

  @Benchmark
  def valueAsSuccessfulOpt() = values.foreach { v =>
    assert(v.asOpt(handler).isDefined)
    assert((v: BSONValue).asOpt(unsafeHandler).isDefined)
  }

  @Benchmark
  def valueAsFailedOpt() = values.foreach { v =>
    assert((v: BSONValue).asOpt[Unsupported.type].isEmpty)
  }

  private object Unsupported {

    implicit def reader: BSONReader[Unsupported.type] =
      BSONReader[Unsupported.type] { _ => ??? }
  }
}
