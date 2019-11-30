package reactivemongo
package api.bson

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class BSONTimestampBenchmark {
  private val timeMs = 1574884443000L
  private val timeSec = 366
  private val ordinal = -1368554632

  @Benchmark
  def fromTimeMS() = {
    val ts = BSONTimestamp(timeMs)

    assert(ts.time == timeSec && ts.ordinal == ordinal && ts.value == timeMs)
  }

  @Benchmark
  def fromSecOrdinal() = {
    val ts = BSONTimestamp(timeSec, ordinal)

    assert(ts.time == timeSec && ts.ordinal == ordinal && ts.value == timeMs)
  }
}
