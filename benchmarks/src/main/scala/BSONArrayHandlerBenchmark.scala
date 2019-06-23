package reactivemongo
package api.bson

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class BSONArrayHandlerBenchmark {
  var smallArray: BSONArray = _
  var bigArray: BSONArray = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    smallArray = BSONArray(1, "foo", 1.23D)
    bigArray = BSONArrayBenchmark.bigArray()
  }

  @Benchmark
  def seqReaderSmallArray(): Unit = {
    val res = collectionReader[Seq, BSONValue].readTry(smallArray)
    assert(res.isSuccess)
  }

  @Benchmark
  def listReaderBigArray(): Unit = {
    val res = collectionReader[List, BSONValue].readTry(bigArray)
    assert(res.isSuccess)
  }
}
