package reactivemongo.api.bson

import reactivemongo.api.bson.buffer.{ ReadableBuffer, WritableBuffer }

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class ReadableBufferBenchmark {
  private val str = "loremIpsumDolor"

  private var fixture: WritableBuffer = _
  private var input: ReadableBuffer = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    fixture = WritableBuffer.empty.writeBsonString(str)
  }

  @Setup(Level.Invocation)
  def setupInvocation(): Unit = {
    input = fixture.toReadableBuffer()
  }

  @Benchmark
  def readInt(): Unit = {
    assert((input.readInt() - 1) == str.size)
  }

  @Benchmark
  def readString(): Unit =
    assert(input.readBsonString() == str)

  @Benchmark
  def toWritableBuffer(): Unit =
    assert(input.toWritableBuffer.size() == input.size)
}
