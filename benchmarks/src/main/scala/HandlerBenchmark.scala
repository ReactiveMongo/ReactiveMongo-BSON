package reactivemongo
package api.bson

import org.openjdk.jmh.annotations._

sealed trait HandlerBenchmark[B <: BSONValue] {
  protected def values: Iterable[B]
  protected def handler: BSONReader[_]
  final private def unsafeHandler = handler

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

// ---

@State(Scope.Benchmark)
class BSONBooleanHandlerBenchmark extends HandlerBenchmark[BSONBoolean] {
  val values = BSONValueFixtures.bsonBoolFixtures
  lazy val handler = BSONBooleanHandler
}

@State(Scope.Benchmark)
class BSONDateTimeHandlerBenchmark extends HandlerBenchmark[BSONDateTime] {
  val values = BSONValueFixtures.bsonDateTimeFixtures
  lazy val handler = BSONDateTimeHandler
}

@State(Scope.Benchmark)
class BSONDecimalHandlerBenchmark extends HandlerBenchmark[BSONDecimal] {
  val values = BSONValueFixtures.bsonDecimalFixtures.filter {
    case BSONDecimal.NegativeZero => false
    case dec => !dec.isInfinite && !dec.isNaN
  }

  lazy val handler = BSONDecimalHandler
}

@State(Scope.Benchmark)
class BSONDoubleHandlerBenchmark extends HandlerBenchmark[BSONDouble] {
  val values = BSONValueFixtures.bsonDoubleFixtures
  lazy val handler = BSONDoubleHandler
}

@State(Scope.Benchmark)
class BSONIntegerHandlerBenchmark extends HandlerBenchmark[BSONInteger] {
  val values = BSONValueFixtures.bsonIntFixtures
  lazy val handler = BSONIntegerHandler
}

@State(Scope.Benchmark)
class BSONLongHandlerBenchmark extends HandlerBenchmark[BSONLong] {
  val values = BSONValueFixtures.bsonLongFixtures
  lazy val handler = BSONLongHandler
}

@State(Scope.Benchmark)
class BSONStringHandlerBenchmark extends HandlerBenchmark[BSONString] {
  val values = BSONValueFixtures.bsonStrFixtures
  lazy val handler = BSONStringHandler
}

@State(Scope.Benchmark)
class BSONBinaryHandlerBenchmark extends HandlerBenchmark[BSONBinary] {
  val values = BSONValueFixtures.bsonBinFixtures
  lazy val handler = BSONBinaryHandler
}
