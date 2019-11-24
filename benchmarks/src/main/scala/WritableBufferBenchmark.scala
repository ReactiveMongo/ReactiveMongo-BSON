package reactivemongo.api.bson

import scala.util.Random

import org.openjdk.jmh.annotations._

import reactivemongo.api.bson.buffer.{ ReadableBuffer, WritableBuffer }

@State(Scope.Benchmark)
class WritableBufferBenchmark {
  private var bytes: Array[Byte] = _
  private var emptyBuffer: WritableBuffer = _
  private var outputBuffer: WritableBuffer = _
  private var bytesBuffer: ReadableBuffer = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    bytes = Array.ofDim[Byte](8096)
    Random.nextBytes(bytes)

    emptyBuffer = WritableBuffer.empty
    outputBuffer = WritableBuffer(bytes)
    bytesBuffer = outputBuffer.toReadableBuffer
  }

  @Benchmark
  def toReadableBuffer(): Unit = {
    assert(outputBuffer.toReadableBuffer.size == bytes.size)
  }

  @Benchmark // Only for internal testing, not part of runtime/API
  def empty() = WritableBuffer.empty

  @Benchmark
  def size(): Int = emptyBuffer.size

  @Benchmark
  def setInt(): WritableBuffer = emptyBuffer.setInt(0, 10)

  @Benchmark
  def writeBytesArray(): WritableBuffer = emptyBuffer.writeBytes(bytes)

  @Benchmark
  def writeBytesBuf(): WritableBuffer =
    emptyBuffer.writeBytes(bytesBuffer)

  @Benchmark
  def writeByte() = emptyBuffer.writeByte(0x04: Int)

  @Benchmark
  def writeInt(): WritableBuffer = emptyBuffer.writeInt(20)

  @Benchmark
  def writeLong(): WritableBuffer = emptyBuffer.writeLong(1234L)

  @Benchmark
  def writeDouble(): WritableBuffer = emptyBuffer.writeDouble(567.78D)

  @Benchmark
  def array(): Array[Byte] = {
    val res = outputBuffer.array()
    assert(java.util.Arrays.equals(bytes, res))
    res
  }
}
