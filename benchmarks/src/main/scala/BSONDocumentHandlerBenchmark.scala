package reactivemongo
package api.bson

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class BSONDocumentHandlerBenchmark {
  var smallDoc: BSONDocument = _
  var bigDoc: BSONDocument = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    smallDoc = BSONDocument("_id" -> 1, "name" -> "foo", "bar" -> 1.23D)
    bigDoc = BSONDocumentBenchmark.bigDocument()
  }

  @Benchmark
  def mapReaderSmallDoc(): Unit = {
    val res = mapReader[String, BSONValue].readTry(smallDoc)
    assert(res.isSuccess)
  }

  @Benchmark
  def mapReaderBigDoc(): Unit = {
    val res = mapReader[String, BSONValue].readTry(bigDoc)
    assert(res.isSuccess)
  }

  @Benchmark
  def identityReaderDoc(): Unit = {
    val res = implicitly[BSONReader[BSONDocument]].readTry(bigDoc)
    assert(res.isSuccess)
  }

  @Benchmark
  def identityReader(): Unit = {
    val res = implicitly[BSONReader[BSONValue]].readTry(smallDoc)
    assert(res.isSuccess)
  }
}
